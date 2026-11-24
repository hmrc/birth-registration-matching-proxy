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
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.matching.StringValuePattern
import org.mockito.Mockito.{mock, when}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.Configuration
import play.api.libs.json.Json
import uk.gov.hmrc.brm.TestFixture
import uk.gov.hmrc.brm.certificate.CertificateStatus
import uk.gov.hmrc.brm.config.GroAppConfig
import uk.gov.hmrc.brm.metrics.BRMMetrics
import uk.gov.hmrc.brm.time.TimeProvider
import uk.gov.hmrc.brm.utils.{BrmLogger, KeyHolder, WireMockHelper}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing

class AuthenticatorSpec
    extends TestFixture with WireMockHelper with ScalaFutures with IntegrationPatience with LogCapturing {

  override def extraApplicationConfig: Map[String, Any] = Map("play.ws.timeout.request" -> "1 second")

  given hc: HeaderCarrier   = HeaderCarrier()
  given metrics: BRMMetrics = new BRMMetrics

  private val authPath = "/auth/token"

  private val brmKey = "authenticator-spec"

  override def beforeEach(): Unit = {
    super.beforeEach()
    KeyHolder.setKey(brmKey)
  }

  private def configFor(port: Int, tlsEnabled: Boolean): GroAppConfig = {
    val prefix    = "microservice.services.birth-registration-matching.gro"
    val overrides = Configuration(
      s"$prefix.authentication.v2.host" -> "127.0.0.1",
      s"$prefix.authentication.v2.port" -> port,
      s"$prefix.tls.tlsEnabled"         -> tlsEnabled
    )
    new GroAppConfig(new ServicesConfig(overrides.withFallback(real[Configuration])))
  }

  private def authenticator(
    port: Int = server.port(),
    tlsEnabled: Boolean = false,
    certificateValid: Boolean = true
  ): Authenticator = {
    val certificateStatus = mock(classOf[CertificateStatus])
    when(certificateStatus.certificateStatus()).thenReturn(certificateValid)
    new Authenticator(configFor(port, tlsEnabled), certificateStatus, real[HttpClientV2], new TimeProvider())
  }

  private def stubAuth(response: ResponseDefinitionBuilder): Unit =
    server.stubFor(post(urlEqualTo(authPath)).willReturn(response))

  private def tokenResponse(token: String = "a-new-access-token", expiresIn: Int = 300): ResponseDefinitionBuilder =
    okJson(Json.obj("access_token" -> token, "expires_in" -> expiresIn).toString())

  // wiremock is pinned to a version that doesn't have withFormParam by http-verbs-test
  // so we use matching instead
  private def formField(name: String, value: String): StringValuePattern =
    // match "name=value" as a complete field anywhere in the form body
    matching(s"(^|.*&)$name=$value(&.*|$$)")

  "Authenticator.token" when {

    "there is no cached token" should {

      "post the credentials to the auth service as a form" in {
        stubAuth(tokenResponse())

        authenticator().token().futureValue

        server.verify(
          postRequestedFor(urlEqualTo(authPath))
            .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
            .withRequestBody(formField("username", "test"))
            .withRequestBody(formField("password", "test"))
            .withRequestBody(formField("client_id", "test"))
            .withRequestBody(formField("client_secret", "test"))
            .withRequestBody(formField("grant_type", "password"))
        )
      }

      "return and cache the access token from a 200 response" in {
        stubAuth(tokenResponse("a-new-access-token"))
        val auth = authenticator()

        auth.token().futureValue       shouldBe BirthAccessTokenResponse("a-new-access-token")
        auth.tokenCache.token.toOption shouldBe Some("a-new-access-token")
      }

      "return a 500 error when a 200 response is not valid json" in {
        stubAuth(ok("this is not json"))
        val auth = authenticator()

        val result = auth.token().futureValue

        result                   shouldBe BirthErrorResponse(
          UpstreamErrorResponse("[ErrorHandler][500] [ErrorHandler][500]", 500, 500)
        )
        auth.tokenCache.hasToken shouldBe false
      }

      "return a 500 error when a 200 response has no access_token" in {
        stubAuth(okJson(Json.obj("expires_in" -> 300).toString()))
        val auth = authenticator()

        withCaptureOfLoggingFrom(BrmLogger) { logs =>
          val result = auth.token().futureValue

          result shouldBe BirthErrorResponse(
            UpstreamErrorResponse("[ErrorHandler][500] [ErrorHandler][500] request failed", 500, 500)
          )

          val authenticatorErrors = logs.collect {
            case event if event.getLevel == Level.ERROR && event.getFormattedMessage.contains("[Authenticator]") =>
              event.getFormattedMessage
          }

          authenticatorErrors shouldBe List(
            s"[BRM-Key:$brmKey],[Authenticator][requestNewToken] : unable to obtain new access token: " +
              "unexpected exception: uk.gov.hmrc.http.UpstreamErrorResponse: [ErrorHandler][500] request failed"
          )
        }

        auth.tokenCache.hasToken shouldBe false
      }
    }

    "there is a cached token" should {

      "use the cache and not call the auth service again" in {
        stubAuth(tokenResponse("cached-token"))
        val auth = authenticator()

        auth.token().futureValue shouldBe BirthAccessTokenResponse("cached-token")
        auth.token().futureValue shouldBe BirthAccessTokenResponse("cached-token")

        server.verify(1, postRequestedFor(urlEqualTo(authPath)))
      }
    }

    "the auth service fails" should {

      Seq(401, 500, 502, 503, 504).foreach { status =>
        s"return a 500 error when the auth service returns $status" in {
          stubAuth(aResponse().withStatus(status))

          withCaptureOfLoggingFrom(BrmLogger) { logs =>
            val result = authenticator().token().futureValue

            result shouldBe BirthErrorResponse(
              UpstreamErrorResponse(s"[ErrorHandler][500] [ErrorHandler][$status]", 500, 500)
            )

            val errors = logs.filter(_.getLevel == Level.ERROR).map(_.getFormattedMessage)
            errors shouldBe List(
              s"[BRM-Key:$brmKey],[Authenticator][requestNewToken] : unable to obtain new access token: " +
                s"unexpected exception: uk.gov.hmrc.http.UpstreamErrorResponse: [ErrorHandler][$status]"
            )
          }
        }
      }

      "return a 500 error when the auth service times out" in {
        stubAuth(tokenResponse().withFixedDelay(3000))

        withCaptureOfLoggingFrom(BrmLogger) { logs =>
          val result = authenticator().token().futureValue

          result shouldBe BirthErrorResponse(
            UpstreamErrorResponse("[ErrorHandler][500] [ErrorHandler][504] request timed out", 500, 500)
          )

          val port     = server.port()
          val warnings = logs.filter(_.getLevel == Level.WARN).map(_.getFormattedMessage)

          warnings shouldBe List(
            s"[BRM-Key:$brmKey],[ResponseHandler][handle] : [504] request timed out: " +
              s"POST of 'http://127.0.0.1:$port$authPath' timed out with message " +
              s"'Request timeout to 127.0.0.1/127.0.0.1:$port after 1000 ms'"
          )
        }
      }

      "return a 500 error when the auth service refuses the connection" in {
        val unusedPort = 19999

        withCaptureOfLoggingFrom(BrmLogger) { logs =>
          val result = authenticator(port = unusedPort).token().futureValue

          result shouldBe BirthErrorResponse(
            UpstreamErrorResponse("[ErrorHandler][500] [ErrorHandler][502] connection failed", 500, 500)
          )

          val warnings = logs.filter(_.getLevel == Level.WARN).map(_.getFormattedMessage)

          warnings shouldBe List(
            s"[BRM-Key:$brmKey],[ResponseHandler][handle] : [502] connection failed: " +
              s"POST of 'http://127.0.0.1:$unusedPort$authPath' failed. " +
              s"Caused by: 'Connection refused (connect failed): /127.0.0.1:$unusedPort'"
          )
        }
      }
    }

    "TLS is enabled" should {

      "not call the auth service when the certificate has expired" in {
        withCaptureOfLoggingFrom(BrmLogger) { logs =>
          val result = authenticator(tlsEnabled = true, certificateValid = false).token().futureValue

          result shouldBe BirthErrorResponse(
            UpstreamErrorResponse("[ErrorHandler][500] TLS Certificate expired", 500, 500)
          )

          val errors = logs.filter(_.getLevel == Level.ERROR).map(_.getFormattedMessage)
          errors shouldBe List(s"[BRM-Key:$brmKey],[Authenticator][token] : TLS Certificate expired")
        }
        server.verify(0, postRequestedFor(anyUrl()))
      }

      "call the auth service when the certificate is valid" in {
        stubAuth(tokenResponse())

        authenticator(tlsEnabled = true, certificateValid = true).token().futureValue

        server.verify(1, postRequestedFor(urlEqualTo(authPath)))
      }
    }
  }

}
