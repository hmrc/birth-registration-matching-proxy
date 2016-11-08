/*
 * Copyright 2016 HM Revenue & Customs
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

import java.io.IOException
import java.net.{SocketTimeoutException, URL}

import org.joda.time.{DateTime, DateTimeUtils}
import org.mockito.Matchers
import org.mockito.Matchers.{eq => mockEq}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.libs.json.JsNull
import play.api.test.Helpers._
import uk.co.bigbeeconsultants.http.HttpClient
import uk.co.bigbeeconsultants.http.header.{Headers, MediaType}
import uk.co.bigbeeconsultants.http.request.Request
import uk.co.bigbeeconsultants.http.response.{Response, Status}
import uk.gov.hmrc.brm.config.GROConnectorConfiguration
import uk.gov.hmrc.brm.metrics.{GroMetrics, Metrics}
import uk.gov.hmrc.brm.utils.{AccessTokenRepository, CertificateStatus}
import uk.gov.hmrc.play.http.{Upstream4xxResponse, _}
import uk.gov.hmrc.play.test.UnitSpec
import utils.JsonUtils

class BirthConnectorSpec extends UnitSpec with OneAppPerSuite with MockitoSugar with BeforeAndAfter {

  implicit val hc = HeaderCarrier()

  val mockHttpClient = mock[HttpClient]

  val mockCertificateStatus = mock[CertificateStatus]

  object MockBirthConnector extends BirthConnector {
    override val httpClient = mockHttpClient
    override val metrics = GroMetrics
    override val authRepository = new AccessTokenRepository
    override val delayTime = GROConnectorConfiguration.delayAttemptInMilliseconds
    override val delayAttempts = GROConnectorConfiguration.delayAttempts
  }

  object MockBirthConnectorTestConfig extends BirthConnector {
    override val httpClient = mockHttpClient
    override val metrics = GroMetrics
    override val authRepository = new AccessTokenRepository
    override val delayTime = 100
    override val delayAttempts = 3
  }

  def groResponse(reference: String) = JsonUtils.getJsonFromFile(s"gro/$reference")

  val authRecord = JsonUtils.getJsonFromFile("gro/auth")

  val headers = Map(
    "Authorization" -> s"Bearer something",
    "X-Auth-Downstream-Username" -> "hmrc"
  )

  before(
    reset(mockHttpClient)
  )

  "BirthConnector" when {

    "initialising" should {

      "having correct configurations" in {
        running(app) {
          MockBirthConnectorTestConfig.delayAttempts shouldBe 3
          MockBirthConnectorTestConfig.delayTime shouldBe 100
          MockBirthConnectorTestConfig.authRepository shouldBe a[AccessTokenRepository]
          MockBirthConnectorTestConfig.metrics shouldBe a[Metrics]
          MockBirthConnectorTestConfig.httpClient shouldBe a[HttpClient]
        }
      }
    }

    "parsing json" should {
      "throw Upstream5xxResponse for invalid json" in {
        running(app) {
          val authResponse = Response.apply(Request.post(new URL("http://localhost:8099/oauth/login"), None), Status.S200_OK, MediaType.APPLICATION_JSON, authRecord.toString())
          val eventResponse = Response.apply(Request.get(new URL("http://localhost:8099/v0/events/birth"), headers = Headers.apply(headers)), Status.S200_OK, MediaType.APPLICATION_JSON, "[something]")
          when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
          when(mockHttpClient.get(Matchers.any(), Matchers.any())).thenReturn(eventResponse)
          val result = await(MockBirthConnector.getReference("500035710"))
          val birthErrorResponse = result.asInstanceOf[BirthErrorResponse]
          birthErrorResponse.cause.isInstanceOf[Upstream5xxResponse] shouldBe true
        }
      }
    }

    "getReference" should {
      "200 with json response with match" in {
        running(app) {
          val authResponse = Response.apply(Request.post(new URL("http://localhost:8099/oauth/login"), None), Status.S200_OK, MediaType.APPLICATION_JSON, authRecord.toString())
          val eventResponse = Response.apply(Request.get(new URL("http://localhost:8099/v0/events/birth"), headers = Headers.apply(headers)), Status.S200_OK, MediaType.APPLICATION_JSON, groResponse("500035710").toString())
          when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
          when(mockHttpClient.get(Matchers.any(), Matchers.any())).thenReturn(eventResponse)
          val result = await(MockBirthConnector.getReference("500035710"))
          result should not be JsNull
        }
      }

      "404 with NotFound response for no match" in {
        running(app) {
          val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None), Status.S200_OK, MediaType.APPLICATION_JSON, authRecord.toString())
          val eventResponse = Response.apply(Request.get(new URL("http://localhost:8099"), headers = Headers.apply(headers)), Status.S404_NotFound, MediaType.APPLICATION_JSON, groResponse("valid_empty").toString())
          when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any()))
            .thenReturn(authResponse)
          when(mockHttpClient.get(Matchers.any(), Matchers.any()))
            .thenReturn(eventResponse)
          val result = await(MockBirthConnector.getReference("500037654675710"))
          result shouldBe a[BirthErrorResponse]
          result match {
            case BirthErrorResponse(cause) =>
              cause shouldBe a[Upstream4xxResponse]
            case r@BirthSuccessResponse(json) =>
              r should not be a[BirthSuccessResponse]
          }
        }
      }

      "400 with BadRequest for authentication" in {
        running(app) {
          MockBirthConnector.authRepository.saveToken("", DateTime.now.minusSeconds(10))
          val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None), Status.S400_BadRequest, MediaType.APPLICATION_JSON, "")
          when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
          val result = await(MockBirthConnector.getReference("500035710"))
          result shouldBe a[BirthErrorResponse]
          result match {
            case BirthErrorResponse(cause) =>
              cause shouldBe a[Upstream4xxResponse]
            case r@BirthSuccessResponse(json) =>
              r should not be a[BirthSuccessResponse]
          }
        }
      }

      "500 with InternalServerError for authentication" in {
        running(app) {
          val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None), Status.S500_InternalServerError, MediaType.APPLICATION_JSON, "")
          when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
          val result = await(MockBirthConnector.getReference("500035710"))
          result shouldBe a[BirthErrorResponse]
          result match {
            case BirthErrorResponse(cause) =>
              cause shouldBe a[Upstream5xxResponse]
            case r@BirthSuccessResponse(json) =>
              r should not be a[BirthSuccessResponse]
          }
        }
      }


      "400 with BadRequest for reference" in {
        running(app) {
          val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None), Status.S200_OK, MediaType.APPLICATION_JSON, authRecord.toString())
          val eventResponse = Response.apply(Request.get(new URL("http://localhost:8099"), headers = Headers.apply(headers)), Status.S400_BadRequest, MediaType.APPLICATION_JSON, "")
          when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
          when(mockHttpClient.get(Matchers.any(), Matchers.any())).thenReturn(eventResponse)
          val result = await(MockBirthConnector.getReference("500035710"))
          result shouldBe a[BirthErrorResponse]
          result match {
            case BirthErrorResponse(cause) =>
              cause shouldBe a[Upstream4xxResponse]
            case r@BirthSuccessResponse(json) =>
              r should not be a[BirthSuccessResponse]
          }
        }
      }

      "500 with InternalServerError for reference" in {
        running(app) {
          val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None), Status.S200_OK, MediaType.APPLICATION_JSON, authRecord.toString())
          val eventResponse = Response.apply(Request.get(new URL("http://localhost:8099"), headers = Headers.apply(headers)), Status.S500_InternalServerError, MediaType.APPLICATION_JSON, "")
          when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
          when(mockHttpClient.get(Matchers.any(), Matchers.any())).thenReturn(eventResponse)
          val result = await(MockBirthConnector.getReference("500035710"))
          result shouldBe a[BirthErrorResponse]
          result match {
            case BirthErrorResponse(cause) =>
              cause shouldBe a[Upstream5xxResponse]
            case r@BirthSuccessResponse(json) =>
              r should not be a[BirthSuccessResponse]
          }
        }
      }

      "500 with InternalSeverError when certificate has expired" in {
        running(app) {
          // Force LocalDate to something other than now
          val date = new DateTime(2050: Int, 9: Int, 15: Int, 5: Int, 10: Int, 10: Int)
          DateTimeUtils.setCurrentMillisFixed(date.getMillis)
          val result = await(MockBirthConnector.getReference("500035710"))
          result shouldBe a[BirthErrorResponse]
          result match {
            case BirthErrorResponse(cause) =>
              cause shouldBe a[Exception]
            case r@BirthSuccessResponse(json) =>
              r should not be a[BirthSuccessResponse]
          }
          DateTimeUtils.setCurrentMillisSystem()
        }
      }

      "500 with InternalServerError when authentication returns empty or no access tokem" in {
        running(app) {
          MockBirthConnector.authRepository.saveToken("", DateTime.now.minusSeconds(10))
          val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None), Status.S200_OK, MediaType.APPLICATION_JSON, "")
          when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
          val result = await(MockBirthConnector.getReference("500035710"))
          result shouldBe a[BirthErrorResponse]
          result match {
            case BirthErrorResponse(cause) =>
              cause shouldBe a[Exception]
            case r@BirthSuccessResponse(json) =>
              r should not be a[BirthSuccessResponse]
          }
        }
      }

      "return BirthErrorResponse when authentication returns exception" in {
        running(app) {
          MockBirthConnector.authRepository.saveToken("", DateTime.now.minusSeconds(10))
          val json =
            """
              |"reference": "something"
            """.stripMargin
          val
          eventResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None), Status.S200_OK, MediaType.APPLICATION_JSON, json)
          when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(eventResponse)
          val result = await(MockBirthConnector.getReference("500035710"))
          result shouldBe a[BirthErrorResponse]
          result match {
            case BirthErrorResponse(cause) =>
              cause shouldBe a[Exception]
            case r @ BirthSuccessResponse(body) =>
              r should not be a[BirthSuccessResponse]
          }
        }
      }

      "return BirthErrorResponse when all attempts fail for authentication (SocketTimeoutException)" in {
        running(app) {
          when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenThrow(new SocketTimeoutException(""))
          val result = await(MockBirthConnector.getReference("500035710"))
          verify(mockHttpClient, times(3)).post(Matchers.any(), Matchers.any(), Matchers.any())
          result shouldBe a[BirthErrorResponse]
          result match {
            case BirthErrorResponse(cause) =>
              cause shouldBe a[SocketTimeoutException]
            case r@BirthSuccessResponse(json) =>
              r should not be a[BirthSuccessResponse]
          }
        }
      }

      "return BirthErrorResponse when Exception is thrown for authentication" in {
        running(app) {
          when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenThrow(new IOException(""))
          val result = await(MockBirthConnector.getReference("500035710"))
          verify(mockHttpClient, times(1)).post(Matchers.any(), Matchers.any(), Matchers.any())
          result shouldBe a[BirthErrorResponse]
          result match {
            case BirthErrorResponse(cause) =>
              cause shouldBe a[IOException]
            case r@BirthSuccessResponse(json) =>
              r should not be a[BirthSuccessResponse]
          }
        }
      }

      "return BirthErrorResponse when all attempts fail for event lookup (SocketTimeoutException)" in {
        running(app) {
          val authResponse = Response.apply(Request.post(new URL("http://localhost:8099/oauth/login"), None), Status.S200_OK, MediaType.APPLICATION_JSON, authRecord.toString())
          when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
          when(mockHttpClient.get(Matchers.any(), Matchers.any())).thenThrow(new SocketTimeoutException(""))
          val result = await(MockBirthConnector.getReference("500035710"))
          verify(mockHttpClient, times(3)).get(Matchers.any(), Matchers.any())
          result shouldBe a[BirthErrorResponse]
          result match {
            case BirthErrorResponse(cause) =>
              cause shouldBe a[SocketTimeoutException]
            case r@BirthSuccessResponse(json) =>
              r should not be a[BirthSuccessResponse]
          }
        }
      }

      "return BirthErrorResponse when Exception is thrown for event lookup" in {
        running(app) {
          val authResponse = Response.apply(Request.post(new URL("http://localhost:8099/oauth/login"), None), Status.S200_OK, MediaType.APPLICATION_JSON, authRecord.toString())
          when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
          when(mockHttpClient.get(Matchers.any(), Matchers.any())).thenThrow(new IOException(""))
          val result = await(MockBirthConnector.getReference("500035710"))
          verify(mockHttpClient, times(1)).get(Matchers.any(), Matchers.any())
          result shouldBe a[BirthErrorResponse]
          result match {
            case BirthErrorResponse(cause) =>
              cause shouldBe a[IOException]
            case r@BirthSuccessResponse(json) =>
              r should not be a[BirthSuccessResponse]
          }
        }
      }
    }
  }
}
