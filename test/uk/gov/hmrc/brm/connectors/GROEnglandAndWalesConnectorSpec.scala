/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.brm.connectors

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.http.Fault
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.Configuration
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.brm.TestFixture
import uk.gov.hmrc.brm.certificate.CertificateStatus
import uk.gov.hmrc.brm.config.GroAppConfig
import uk.gov.hmrc.brm.metrics.BRMMetrics
import uk.gov.hmrc.brm.time.TimeProvider
import uk.gov.hmrc.brm.utils.{BrmLogger, JsonUtils, KeyHolder, WireMockHelper}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing

class GROEnglandAndWalesConnectorSpec
    extends TestFixture with WireMockHelper with ScalaFutures with IntegrationPatience with LogCapturing {

  override def extraApplicationConfig: Map[String, Any] = Map("play.ws.timeout.request" -> "1 second")

  given hc: HeaderCarrier   = HeaderCarrier()
  given metrics: BRMMetrics = new BRMMetrics

  private val authPath    = "/auth/token"
  private val groPath     = "/v1/registration/birth"
  private val accessToken = "gro-access-token"
  private val reference   = "500035710"

  private val brmKey = "gro-connector-spec"
  KeyHolder.setKey(brmKey)

  override def beforeEach(): Unit = {
    super.beforeEach()
    server.stubFor(post(urlEqualTo(authPath)).willReturn(tokenResponse()))
  }

  private def configFor(groPort: Int, authPort: Int): GroAppConfig = {
    val services  = "microservice.services.birth-registration-matching"
    val overrides = Configuration(
      s"$services.host"                       -> "127.0.0.1",
      s"$services.port"                       -> groPort,
      s"$services.gro.authentication.v2.host" -> "127.0.0.1",
      s"$services.gro.authentication.v2.port" -> authPort,
      s"$services.gro.tls.tlsEnabled"         -> false
    )
    new GroAppConfig(new ServicesConfig(overrides.withFallback(real[Configuration])))
  }

  private def connector(groPort: Int = server.port()): GROEnglandAndWalesConnector = {
    val config        = configFor(groPort, authPort = server.port())
    val http          = real[HttpClientV2]
    val authenticator = new Authenticator(config, real[CertificateStatus], http, new TimeProvider())
    new GROEnglandAndWalesConnector(config, http, authenticator)
  }

  private def tokenResponse(): ResponseDefinitionBuilder =
    okJson(Json.obj("access_token" -> accessToken, "expires_in" -> 300).toString())

  private def groRecord(name: String): JsValue = JsonUtils.getJsonFromFile(s"gro/$name")

  private def stubReference(response: ResponseDefinitionBuilder): Unit =
    server.stubFor(get(urlEqualTo(s"$groPath/$reference")).willReturn(response))

  private def stubDetails(response: ResponseDefinitionBuilder): Unit =
    server.stubFor(get(urlPathEqualTo(groPath)).willReturn(response))

  private def connectorLogs(logs: List[ILoggingEvent], level: Level): List[String] =
    logs.collect {
      case event if event.getLevel == level && event.getFormattedMessage.contains("[GROEnglandAndWalesConnector]") =>
        event.getFormattedMessage
    }

  private def groUrl: String = s"http://127.0.0.1:${server.port()}$groPath"

  private val upstreamError = "uk.gov.hmrc.http.UpstreamErrorResponse"

  private def infoLine(method: String, message: String): String =
    s"[BRM-Key:$brmKey], [GROEnglandAndWalesConnector][$method] : $message"

  private def warnOrErrorLine(method: String, message: String): String =
    s"[BRM-Key:$brmKey],[GROEnglandAndWalesConnector][$method] : $message"

  private def referenceLogs(found: Boolean): List[String] = {
    val foundOrNotFound = if (found) "found" else "not found"
    val requestMessage  = s"[referenceHelper] $foundOrNotFound record by reference"

    List(
      infoLine("getReference", "valid access token obtained"),
      infoLine("request", "[referenceHelper] attempting to find record by reference"),
      infoLine("getChildByReference", s"requesting child's details $groUrl"),
      infoLine("request", requestMessage)
    )
  }

  private def detailsLogs(found: Boolean): List[String] = {
    val requestMessage =
      if (found) "[detailsHelper] found record(s) by details" else "[detailsHelper] not found record by details"

    List(
      infoLine("getDetails", "valid access token obtained"),
      infoLine("request", "[detailsHelper] attempting to find record(s) by details"),
      infoLine("getChildByDetails", s"requesting child's details $groUrl"),
      infoLine(
        "request",
        requestMessage
      )
    )
  }

  private def referenceFailure(detail: String): String =
    warnOrErrorLine("request", s"[referenceHelper] failed to load record by reference: $detail")

  private def detailsFailure(detail: String): String =
    warnOrErrorLine("request", s"[detailsHelper] failed to load record(s) by details: $detail")

  private def tokenFailure(method: String, upstreamMessage: String): String =
    warnOrErrorLine(method, s"Failed to obtain access token: BirthErrorResponse($upstreamError: $upstreamMessage)")

  "GROEnglandAndWalesConnector.getReference" when {

    "GRO finds the record" should {

      "return the record from a 200 response" in {
        stubReference(okJson(groRecord(reference).toString()))

        withCaptureOfLoggingFrom(BrmLogger) { logs =>
          val result = connector().getReference(reference).futureValue

          result shouldBe BirthSuccessResponse(groRecord(reference))

          connectorLogs(logs, Level.INFO) shouldBe referenceLogs(found = true)

          connectorLogs(logs, Level.WARN) shouldBe Nil
        }
      }

      "send the access token and the GRO headers" in {
        stubReference(okJson(groRecord(reference).toString()))

        connector().getReference(reference).futureValue

        server.verify(
          getRequestedFor(urlEqualTo(s"$groPath/$reference"))
            .withHeader("Authorization", equalTo(s"Bearer $accessToken"))
            .withHeader("X-Auth-Downstream-Username", equalTo("test"))
            .withHeader("Accept-Encoding", containing("gzip"))
            .withHeader("Accept-Charset", equalTo("UTF-8,*;q=.1"))
        )
      }

      "count the request in the reference request metric" in {
        stubReference(okJson(groRecord(reference).toString()))
        val counter = metrics.defaultRegistry.counter(s"${metrics.prefix}-request-count")
        val before  = counter.getCount

        connector().getReference(reference).futureValue

        counter.getCount shouldBe before + 1
      }

      "use the cached token and only call the auth service once" in {
        stubReference(okJson(groRecord(reference).toString()))
        val groConnector = connector()

        groConnector.getReference(reference).futureValue
        groConnector.getReference(reference).futureValue

        server.verify(1, postRequestedFor(urlEqualTo(authPath)))
        server.verify(2, getRequestedFor(urlEqualTo(s"$groPath/$reference")))
      }
    }

    "GRO does not find the record" should {

      "return a Birth404ErrorResponse from a 404 response" in {
        stubReference(aResponse().withStatus(404).withBody(groRecord("NoMatch").toString()))

        withCaptureOfLoggingFrom(BrmLogger) { logs =>
          val result = connector().getReference(reference).futureValue

          result shouldBe Birth404ErrorResponse(UpstreamErrorResponse("[ErrorHandler][404]", 404, 404))

          connectorLogs(logs, Level.INFO) shouldBe referenceLogs(found = false)
        }
      }
    }

    "GRO fails" should {

      "keep the status of a 4xx response" in {
        stubReference(aResponse().withStatus(400))

        withCaptureOfLoggingFrom(BrmLogger) { logs =>
          val result = connector().getReference(reference).futureValue

          result shouldBe BirthErrorResponse(
            UpstreamErrorResponse("[ErrorHandler][400] [ErrorHandler][400]", 400, 400)
          )

          val errorLogs = logs.filter(_.getLevel == Level.ERROR).map(_.getFormattedMessage)

          errorLogs shouldBe List(
            referenceFailure(s"upstream 4xx response: $upstreamError: [ErrorHandler][400]")
          )
        }
      }

      Seq(500, 502, 503, 504).foreach { status =>
        s"return a 500 error when GRO returns $status" in {
          stubReference(aResponse().withStatus(status))

          withCaptureOfLoggingFrom(BrmLogger) { logs =>
            val result = connector().getReference(reference).futureValue

            result shouldBe BirthErrorResponse(
              UpstreamErrorResponse(s"[ErrorHandler][500] [ErrorHandler][$status]", 500, 500)
            )

            val errorLogs = logs.filter(_.getLevel == Level.ERROR).map(_.getFormattedMessage)

            errorLogs shouldBe List(
              referenceFailure(s"$upstreamError: [ErrorHandler][$status]")
            )
          }
        }
      }

      "return a 500 error when a 200 response is not valid json" in {
        stubReference(ok("this is not json"))

        val result = connector().getReference(reference).futureValue

        result shouldBe BirthErrorResponse(
          UpstreamErrorResponse("[ErrorHandler][500] [ErrorHandler][500]", 500, 500)
        )
      }

      "return a 500 error when GRO times out" in {
        stubReference(okJson(groRecord(reference).toString()).withFixedDelay(3000))

        withCaptureOfLoggingFrom(BrmLogger) { logs =>
          val result = connector().getReference(reference).futureValue

          result shouldBe BirthErrorResponse(
            UpstreamErrorResponse("[ErrorHandler][500] [ErrorHandler][504] request timed out", 500, 500)
          )

          val errorLogs = logs.filter(_.getLevel == Level.ERROR).map(_.getFormattedMessage)

          errorLogs shouldBe List(
            referenceFailure(s"$upstreamError: [ErrorHandler][504] request timed out")
          )
        }
      }

      "return a 500 error when GRO refuses the connection" in {
        val unusedPort = 19999

        val result = connector(groPort = unusedPort).getReference(reference).futureValue

        result shouldBe BirthErrorResponse(
          UpstreamErrorResponse("[ErrorHandler][500] [ErrorHandler][502] connection failed", 500, 500)
        )
      }

      "return a 500 error when GRO resets the connection" in {
        stubReference(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))

        withCaptureOfLoggingFrom(BrmLogger) { logs =>
          val result = connector().getReference(reference).futureValue

          result shouldBe BirthErrorResponse(
            UpstreamErrorResponse("[ErrorHandler][500] [ErrorHandler][500] request failed", 500, 500)
          )

          connectorLogs(logs, Level.ERROR) shouldBe List(
            referenceFailure(s"$upstreamError: [ErrorHandler][500] request failed")
          )
        }
      }
    }

    "the auth service fails" should {

      "return the auth error and not call GRO" in {
        server.stubFor(post(urlEqualTo(authPath)).willReturn(aResponse().withStatus(503)))

        withCaptureOfLoggingFrom(BrmLogger) { logs =>
          val result = connector().getReference(reference).futureValue

          result shouldBe BirthErrorResponse(
            UpstreamErrorResponse("[ErrorHandler][500] [ErrorHandler][503]", 500, 500)
          )

          connectorLogs(logs, Level.WARN) shouldBe List(
            tokenFailure("getReference", "[ErrorHandler][500] [ErrorHandler][503]")
          )

          connectorLogs(logs, Level.INFO) shouldBe Nil
        }

        server.verify(0, getRequestedFor(urlPathMatching(s"$groPath.*")))
      }
    }
  }

  "GROEnglandAndWalesConnector.getDetails" when {

    "GRO finds records" should {

      "send the details as an encoded query and return the records" in {
        stubDetails(okJson(groRecord("2006-11-12_smith_adam").toString()))

        withCaptureOfLoggingFrom(BrmLogger) { logs =>
          val result = connector().getDetails("Adam TEST", "SMITH", "2006-11-12").futureValue

          result shouldBe BirthSuccessResponse(groRecord("2006-11-12_smith_adam"))

          connectorLogs(logs, Level.INFO) shouldBe detailsLogs(found = true)

          connectorLogs(logs, Level.WARN) shouldBe Nil
        }

        server.verify(
          getRequestedFor(urlEqualTo(s"$groPath?forenames=Adam+TEST&surname=SMITH&dateOfBirth=2006-11-12"))
            .withHeader("Authorization", equalTo(s"Bearer $accessToken"))
        )
      }

      "encode special characters in the query as UTF-8" in {
        stubDetails(okJson(groRecord("2006-11-12_smith_adam-utf-8").toString()))

        val result = connector().getDetails("Adàm TËST", "SMÏTH", "2006-11-12").futureValue

        result shouldBe BirthSuccessResponse(groRecord("2006-11-12_smith_adam-utf-8"))

        server.verify(
          getRequestedFor(
            urlEqualTo(s"$groPath?forenames=Ad%C3%A0m+T%C3%8BST&surname=SM%C3%8FTH&dateOfBirth=2006-11-12")
          )
        )
      }

      "count the request in the details request metric" in {
        stubDetails(okJson(groRecord("2006-11-12_smith_adam").toString()))
        val counter = metrics.defaultRegistry.counter(s"${metrics.prefix}-details-request-count")
        val before  = counter.getCount

        connector().getDetails("Adam TEST", "SMITH", "2006-11-12").futureValue

        counter.getCount shouldBe before + 1
      }
    }

    "GRO finds no records" should {

      "return the empty list from a 200 response" in {
        stubDetails(okJson(groRecord("NoMatch").toString()))

        val result = connector().getDetails("adam", "smith", "2016-10-10").futureValue

        result shouldBe BirthSuccessResponse(groRecord("NoMatch"))
      }

      "return a Birth404ErrorResponse from a 404 response" in {
        stubDetails(aResponse().withStatus(404).withBody(groRecord("NoMatch").toString()))

        withCaptureOfLoggingFrom(BrmLogger) { logs =>
          val result = connector().getDetails("adam", "smith", "2010-10-06").futureValue

          result shouldBe Birth404ErrorResponse(UpstreamErrorResponse("[ErrorHandler][404]", 404, 404))

          connectorLogs(logs, Level.INFO) shouldBe detailsLogs(found = false)
        }
      }
    }

    "a detail is missing" should
      Seq(
        ("forenames", "", "smith", "2016-10-10"),
        ("lastname", "adam", "", "2016-10-10"),
        ("dateofbirth", "adam", "smith", "")
      ).foreach { case (missing, forenames, lastname, dateOfBirth) =>
        s"send the empty $missing and keep the status of the 400 response" in {
          stubDetails(aResponse().withStatus(400))

          withCaptureOfLoggingFrom(BrmLogger) { logs =>
            val result = connector().getDetails(forenames, lastname, dateOfBirth).futureValue

            result shouldBe BirthErrorResponse(
              UpstreamErrorResponse("[ErrorHandler][400] [ErrorHandler][400]", 400, 400)
            )

            server.verify(
              getRequestedFor(
                urlEqualTo(s"$groPath?forenames=$forenames&surname=$lastname&dateOfBirth=$dateOfBirth")
              )
            )

            val errorLogs = logs.filter(_.getLevel == Level.ERROR).map(_.getFormattedMessage)

            errorLogs shouldBe List(
              detailsFailure(s"upstream 4xx response: $upstreamError: [ErrorHandler][400]")
            )
          }
        }
      }

    "GRO fails" should {

      Seq(500, 502, 503, 504).foreach { status =>
        s"return a 500 error when GRO returns $status" in {
          stubDetails(aResponse().withStatus(status))

          withCaptureOfLoggingFrom(BrmLogger) { logs =>
            val result = connector().getDetails("adam", "smith", "2010-10-06").futureValue

            result shouldBe BirthErrorResponse(
              UpstreamErrorResponse(s"[ErrorHandler][500] [ErrorHandler][$status]", 500, 500)
            )

            val errorLogs = logs.filter(_.getLevel == Level.ERROR).map(_.getFormattedMessage)

            errorLogs shouldBe List(
              detailsFailure(s"$upstreamError: [ErrorHandler][$status]")
            )
          }
        }
      }

      "return a 500 error when GRO times out" in {
        stubDetails(okJson(groRecord("2006-11-12_smith_adam").toString()).withFixedDelay(3000))

        val result = connector().getDetails("adam", "smith", "2010-10-06").futureValue

        result shouldBe BirthErrorResponse(
          UpstreamErrorResponse("[ErrorHandler][500] [ErrorHandler][504] request timed out", 500, 500)
        )
      }

      "return a 500 error when GRO refuses the connection" in {
        val unusedPort = 19999

        val result = connector(groPort = unusedPort).getDetails("adam", "smith", "2010-10-06").futureValue

        result shouldBe BirthErrorResponse(
          UpstreamErrorResponse("[ErrorHandler][500] [ErrorHandler][502] connection failed", 500, 500)
        )
      }
    }

    "the auth service fails" should {

      "return the auth error and not call GRO" in {
        server.stubFor(post(urlEqualTo(authPath)).willReturn(aResponse().withStatus(500)))

        withCaptureOfLoggingFrom(BrmLogger) { logs =>
          val result = connector().getDetails("adam", "smith", "2010-10-06").futureValue

          result shouldBe BirthErrorResponse(
            UpstreamErrorResponse("[ErrorHandler][500] [ErrorHandler][500]", 500, 500)
          )

          connectorLogs(logs, Level.WARN) shouldBe List(
            tokenFailure("getDetails", "[ErrorHandler][500] [ErrorHandler][500]")
          )

          connectorLogs(logs, Level.INFO) shouldBe Nil
        }

        server.verify(0, getRequestedFor(urlPathMatching(s"$groPath.*")))
      }
    }
  }

  "GROEnglandAndWalesConnector" when {

    "GRO returns its sample V1 record" should {

      "return the record for its reference" in {
        server.stubFor(get(urlEqualTo(s"$groPath/123456789")).willReturn(okJson(groRecord("123456789").toString())))

        withCaptureOfLoggingFrom(BrmLogger) { logs =>
          val result = connector().getReference("123456789").futureValue

          result shouldBe BirthSuccessResponse(groRecord("123456789"))

          connectorLogs(logs, Level.INFO) shouldBe referenceLogs(found = true)

          connectorLogs(logs, Level.WARN) shouldBe Nil
        }

        server.verify(
          getRequestedFor(urlEqualTo(s"$groPath/123456789"))
            .withHeader("Authorization", equalTo(s"Bearer $accessToken"))
        )
      }

      "return the record for a search by its child's details" in {
        server.stubFor(
          get(urlPathEqualTo(groPath)).willReturn(okJson(Json.arr(groRecord("123456789")).toString()))
        )

        withCaptureOfLoggingFrom(BrmLogger) { logs =>
          val result = connector().getDetails("Joan Narcissus Ouroboros", "SMITH", "2018-08-08").futureValue

          result shouldBe BirthSuccessResponse(Json.arr(groRecord("123456789")))

          connectorLogs(logs, Level.INFO) shouldBe detailsLogs(found = true)
          connectorLogs(logs, Level.WARN) shouldBe Nil
        }

        server.verify(
          getRequestedFor(
            urlEqualTo(s"$groPath?forenames=Joan+Narcissus+Ouroboros&surname=SMITH&dateOfBirth=2018-08-08")
          )
        )
      }

      "return a Birth404ErrorResponse for a reference that is too long" in {
        server.stubFor(
          get(urlEqualTo(s"$groPath/500037654675710"))
            .willReturn(aResponse().withStatus(404).withBody(groRecord("NoMatch").toString()))
        )

        withCaptureOfLoggingFrom(BrmLogger) { logs =>
          val result = connector().getReference("500037654675710").futureValue

          result shouldBe Birth404ErrorResponse(UpstreamErrorResponse("[ErrorHandler][404]", 404, 404))

          connectorLogs(logs, Level.INFO) shouldBe referenceLogs(found = false)
          connectorLogs(logs, Level.WARN) shouldBe Nil
        }
      }
    }
  }

}
