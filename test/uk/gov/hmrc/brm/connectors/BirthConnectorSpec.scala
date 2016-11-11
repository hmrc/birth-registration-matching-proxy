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
import play.api.libs.json.{JsArray, JsNull}
import uk.co.bigbeeconsultants.http.HttpClient
import uk.co.bigbeeconsultants.http.header.{Headers, MediaType}
import uk.co.bigbeeconsultants.http.request.Request
import uk.co.bigbeeconsultants.http.response.{Response, Status}
import uk.gov.hmrc.brm.BRMFakeApplication
import uk.gov.hmrc.brm.config.GROConnectorConfiguration
import uk.gov.hmrc.brm.metrics.{GroMetrics, Metrics}
import uk.gov.hmrc.brm.utils.{AccessTokenRepository, CertificateStatus}
import uk.gov.hmrc.play.http.{Upstream4xxResponse, _}
import uk.gov.hmrc.play.test.UnitSpec
import utils.JsonUtils

class BirthConnectorSpec extends UnitSpec with BRMFakeApplication with MockitoSugar with BeforeAndAfter {

  implicit val hc = HeaderCarrier()

  val mockHttpClient = mock[HttpClient]
  object MockAuthenticator extends Authenticator(
    username = GROConnectorConfiguration.username,
    password = GROConnectorConfiguration.password,
    endpoint = s"${GROConnectorConfiguration.serviceUrl}/api/v0/events/birth",
    http = mockHttpClient,
    tokenCache = new AccessTokenRepository(),
    metrics = GroMetrics
  )

  val mockCertificateStatus = mock[CertificateStatus]

  object MockBirthConnector extends BirthConnector {
    override val httpClient = mockHttpClient
    override val metrics = GroMetrics
    override val authenticator = MockAuthenticator
    override val delayTime = GROConnectorConfiguration.delayAttemptInMilliseconds
    override val delayAttempts = GROConnectorConfiguration.delayAttempts
    override val version = GROConnectorConfiguration.version
    override val endpoint = s"${GROConnectorConfiguration.serviceUrl}/api/$version/events/birth"
    override val username = GROConnectorConfiguration.username
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
        GROEnglandAndWalesConnector.delayAttempts shouldBe 3
        GROEnglandAndWalesConnector.delayTime shouldBe 100
        GROEnglandAndWalesConnector.authenticator shouldBe a[Authenticator]
        GROEnglandAndWalesConnector.metrics shouldBe a[Metrics]
        GROEnglandAndWalesConnector.httpClient shouldBe a[HttpClient]
      }

    }

    "parsing json" should {

      "throw Upstream5xxResponse for invalid json" in {
        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099/oauth/login"), None), Status.S200_OK, MediaType.APPLICATION_JSON, authRecord.toString())
        val eventResponse = Response.apply(Request.get(new URL("http://localhost:8099/v0/events/birth"), headers = Headers.apply(headers)), Status.S200_OK, MediaType.APPLICATION_JSON, "[something]")

        when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
        when(mockHttpClient.get(Matchers.any(), Matchers.any())).thenReturn(eventResponse)

        val result = await(MockBirthConnector.get("500035710"))
        val birthErrorResponse = result.asInstanceOf[BirthErrorResponse]
        birthErrorResponse.cause.isInstanceOf[Upstream5xxResponse] shouldBe true

      }
    }

    "authentication" should {

      "BirthErrorResponse of 4xx when authentication returns BadRequest" in {
//        MockBirthConnector.authenticator.tokenCache.saveToken("", DateTime.now.minusSeconds(10))
        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099/oauth/login"), None), Status.S400_BadRequest, MediaType.APPLICATION_JSON, "")
        when(MockBirthConnector.authenticator.http.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)

        val result = await(MockBirthConnector.get("500035710"))
        result shouldBe a[BirthErrorResponse]
        result match {
          case BirthErrorResponse(cause) =>
            cause shouldBe a[Upstream4xxResponse]
          case r @ BirthSuccessResponse(json) =>
            r should not be a[BirthSuccessResponse]
        }
      }

//      "500 with InternalServerError for authentication" in {
//        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None), Status.S500_InternalServerError, MediaType.APPLICATION_JSON, "")
//        when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
//
//        val result = await(MockBirthConnector.get("500035710"))
//        result shouldBe a[BirthErrorResponse]
//        result match {
//          case BirthErrorResponse(cause) =>
//            cause shouldBe a[Upstream5xxResponse]
//          case r @ BirthSuccessResponse(json) =>
//            r should not be a[BirthSuccessResponse]
//        }
//      }
//
//      "500 with InternalSeverError when certificate has expired" in {
//        // Force LocalDate to something other than now
//        val date = new DateTime(2050: Int, 9: Int, 15: Int, 5: Int, 10: Int, 10: Int)
//        DateTimeUtils.setCurrentMillisFixed(date.getMillis)
//
//        val result = await(MockBirthConnector.get("500035710"))
//
//        result shouldBe a[BirthErrorResponse]
//        result match {
//          case BirthErrorResponse(cause) =>
//            cause shouldBe a[Exception]
//          case r @ BirthSuccessResponse(json) =>
//            r should not be a[BirthSuccessResponse]
//        }
//
//        DateTimeUtils.setCurrentMillisSystem()
//      }
//
//      "500 with InternalServerError when authentication returns empty or no access tokem" in {
//        MockBirthConnector.authenticator.tokenCache.saveToken("", DateTime.now.minusSeconds(10))
//        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None), Status.S200_OK, MediaType.APPLICATION_JSON, "")
//        when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
//
//        val result = await(MockBirthConnector.get("500035710"))
//        result shouldBe a[BirthErrorResponse]
//        result match {
//          case BirthErrorResponse(cause) =>
//            cause shouldBe a[Exception]
//          case r @ BirthSuccessResponse(json) =>
//            r should not be a[BirthSuccessResponse]
//        }
//      }
//
//      "return BirthErrorResponse when authentication returns exception" in {
//        MockBirthConnector.authenticator.tokenCache.saveToken("", DateTime.now.minusSeconds(10))
//        val json =
//          """
//            |"reference": "something"
//          """.stripMargin
//        val eventResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None), Status.S200_OK, MediaType.APPLICATION_JSON, json)
//        when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(eventResponse)
//        val result = await(MockBirthConnector.get("500035710"))
//        result shouldBe a[BirthErrorResponse]
//        result match {
//          case BirthErrorResponse(cause) =>
//            cause shouldBe a[Exception]
//          case r @ BirthSuccessResponse(body) =>
//            r should not be a[BirthSuccessResponse]
//        }
//      }
//
//      "return BirthErrorResponse when all attempts fail for authentication (SocketTimeoutException)" in {
//        when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenThrow(new SocketTimeoutException(""))
//        val result = await(MockBirthConnector.get("500035710"))
//
//        verify(mockHttpClient, times(3)).post(Matchers.any(), Matchers.any(), Matchers.any())
//        result shouldBe a[BirthErrorResponse]
//        result match {
//          case BirthErrorResponse(cause) =>
//            cause shouldBe a[SocketTimeoutException]
//          case r @ BirthSuccessResponse(json) =>
//            r should not be a[BirthSuccessResponse]
//        }
//      }
//
//      "return BirthErrorResponse when Exception is thrown for authentication" in {
//        when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenThrow(new IOException(""))
//        val result = await(MockBirthConnector.get("500035710"))
//
//        verify(mockHttpClient, times(1)).post(Matchers.any(), Matchers.any(), Matchers.any())
//        result shouldBe a[BirthErrorResponse]
//        result match {
//          case BirthErrorResponse(cause) =>
//            cause shouldBe a[IOException]
//          case r @ BirthSuccessResponse(json) =>
//            r should not be a[BirthSuccessResponse]
//        }
//      }

    }

    "get" should {

//      "200 with json response with match" in {
//        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099/oauth/login"), None), Status.S200_OK, MediaType.APPLICATION_JSON, authRecord.toString())
//        val eventResponse = Response.apply(Request.get(new URL("http://localhost:8099/v0/events/birth"), headers = Headers.apply(headers)), Status.S200_OK, MediaType.APPLICATION_JSON, groResponse("500035710").toString())
//
//        when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
//        when(mockHttpClient.get(Matchers.any(), Matchers.any())).thenReturn(eventResponse)
//
//        val result = await(MockBirthConnector.get("500035710"))
//        result should not be JsNull
//      }
//
//      "404 with NotFound response for no match" in {
//        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None), Status.S200_OK, MediaType.APPLICATION_JSON, authRecord.toString())
//        val eventResponse = Response.apply(Request.get(new URL("http://localhost:8099"), headers = Headers.apply(headers)), Status.S404_NotFound, MediaType.APPLICATION_JSON, groResponse("NoMatch").toString())
//
//        when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any()))
//          .thenReturn(authResponse)
//        when(mockHttpClient.get(Matchers.any(), Matchers.any()))
//          .thenReturn(eventResponse)
//
//        val result = await(MockBirthConnector.get("500037654675710"))
//        result shouldBe a[BirthErrorResponse]
//        result match {
//          case BirthErrorResponse(cause) =>
//            cause shouldBe a[Upstream4xxResponse]
//          case r @ BirthSuccessResponse(json) =>
//            r should not be a[BirthSuccessResponse]
//        }
//      }
//
//      "400 with BadRequest for reference" in {
//        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None), Status.S200_OK, MediaType.APPLICATION_JSON, authRecord.toString())
//        val eventResponse = Response.apply(Request.get(new URL("http://localhost:8099"), headers = Headers.apply(headers)), Status.S400_BadRequest, MediaType.APPLICATION_JSON, "")
//
//        when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
//        when(mockHttpClient.get(Matchers.any(), Matchers.any())).thenReturn(eventResponse)
//
//
//        val result = await(MockBirthConnector.get("500035710"))
//        result shouldBe a[BirthErrorResponse]
//        result match {
//          case BirthErrorResponse(cause) =>
//            cause shouldBe a[Upstream4xxResponse]
//          case r @ BirthSuccessResponse(json) =>
//            r should not be a[BirthSuccessResponse]
//        }
//      }
//
//      "500 with InternalServerError for reference" in {
//        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None), Status.S200_OK, MediaType.APPLICATION_JSON, authRecord.toString())
//        val eventResponse = Response.apply(Request.get(new URL("http://localhost:8099"), headers = Headers.apply(headers)), Status.S500_InternalServerError, MediaType.APPLICATION_JSON, "")
//
//        when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
//        when(mockHttpClient.get(Matchers.any(), Matchers.any())).thenReturn(eventResponse)
//
//        val result = await(MockBirthConnector.get("500035710"))
//
//        result shouldBe a[BirthErrorResponse]
//        result match {
//          case BirthErrorResponse(cause) =>
//            cause shouldBe a[Upstream5xxResponse]
//          case r @ BirthSuccessResponse(json) =>
//            r should not be a[BirthSuccessResponse]
//        }
//      }
//
//      "return BirthErrorResponse when all attempts fail for event lookup (SocketTimeoutException)" in {
//        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099/oauth/login"), None), Status.S200_OK, MediaType.APPLICATION_JSON, authRecord.toString())
//        when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
//        when(mockHttpClient.get(Matchers.any(), Matchers.any())).thenThrow(new SocketTimeoutException(""))
//        val result = await(MockBirthConnector.get("500035710"))
//
//        verify(mockHttpClient, times(3)).get(Matchers.any(), Matchers.any())
//        result shouldBe a[BirthErrorResponse]
//        result match {
//          case BirthErrorResponse(cause) =>
//            cause shouldBe a[SocketTimeoutException]
//          case r @ BirthSuccessResponse(json) =>
//            r should not be a[BirthSuccessResponse]
//        }
//      }
//
//      "return BirthErrorResponse when Exception is thrown for event lookup" in {
//        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099/oauth/login"), None), Status.S200_OK, MediaType.APPLICATION_JSON, authRecord.toString())
//        when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
//        when(mockHttpClient.get(Matchers.any(), Matchers.any())).thenThrow(new IOException(""))
//        val result = await(MockBirthConnector.get("500035710"))
//
//        verify(mockHttpClient, times(1)).get(Matchers.any(), Matchers.any())
//        result shouldBe a[BirthErrorResponse]
//        result match {
//          case BirthErrorResponse(cause) =>
//            cause shouldBe a[IOException]
//          case r @ BirthSuccessResponse(json) =>
//            r should not be a[BirthSuccessResponse]
//        }
//      }

    }

    "getDetails" should {

//      "200 with json response with match" in {
//        val firstName = "adam"
//        val lastName = "smith"
//        val dateOfBirth = "2016-10-10"
//
//        val url = new URL(s"http://localhost:8099/api/v0/birth?forenames=$firstName&lastname=$lastName&dateofbirth=$dateOfBirth")
//
//        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099/oauth/login"), None),
//          Status.S200_OK,
//          MediaType.APPLICATION_JSON,
//          authRecord.toString())
//
//        val eventResponse = Response.apply(
//          Request.get(url,
//          headers = Headers.apply(headers)),
//          Status.S200_OK,
//          MediaType.APPLICATION_JSON,
//          groResponse("2006-11-12_smith_adam").toString())
//
//        when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
//        when(mockHttpClient.get(Matchers.any(), Matchers.any())).thenReturn(eventResponse)
//
//        val result = await(MockBirthConnector.get(firstName, lastName, dateOfBirth))
//        result should not be JsNull
//        result shouldBe BirthSuccessResponse(groResponse("2006-11-12_smith_adam"))
//      }
//
//      "200 with [] empty response for no records found" in {
//        val firstName = "adam"
//        val lastName = "smith"
//        val dateOfBirth = "2016-10-10"
//
//        val url = new URL(s"http://localhost:8099/api/v0/birth?forenames=$firstName&lastname=$lastName&dateofbirth=$dateOfBirth")
//
//        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None),
//          Status.S200_OK,
//          MediaType.APPLICATION_JSON,
//          authRecord.toString())
//
//        val eventResponse = Response.apply(
//          Request.get(url,
//            headers = Headers.apply(headers)
//          ),
//          Status.S200_OK,
//          MediaType.APPLICATION_JSON,
//          groResponse("NoMatch").toString())
//
//        when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any()))
//          .thenReturn(authResponse)
//        when(mockHttpClient.get(Matchers.any(), Matchers.any()))
//          .thenReturn(eventResponse)
//
//        val result = await(MockBirthConnector.get(firstName, lastName, dateOfBirth))
//        result should not be JsNull
//        result shouldBe BirthSuccessResponse(groResponse("NoMatch"))
//      }
//
//      "400 with BadRequest for missing forenames parameter" in {
//        val firstName = ""
//        val lastName = "smith"
//        val dateOfBirth = "2016-10-10"
//
//        val url = new URL(s"http://localhost:8099/api/v0/birth?lastname=$lastName&dateofbirth=$dateOfBirth")
//
//        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None),
//          Status.S200_OK,
//          MediaType.APPLICATION_JSON,
//          authRecord.toString())
//
//        val eventResponse = Response.apply(
//          Request.get(url,
//            headers = Headers.apply(headers)
//          ),
//          Status.S400_BadRequest,
//          MediaType.apply("text/plain; charset=UTF-8"),
//          "forenames or forename1 is required")
//
//        when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
//        when(mockHttpClient.get(Matchers.any(), Matchers.any())).thenReturn(eventResponse)
//
//        val result = await(MockBirthConnector.get(firstName, lastName, dateOfBirth))
//
//        result match {
//          case BirthErrorResponse(cause) =>
//            cause shouldBe a[Upstream4xxResponse]
//          case r @ BirthSuccessResponse(json) =>
//            r should not be a[BirthSuccessResponse]
//        }
//      }
//
//      "400 with BadRequest for missing lastname parameter" in {
//        val firstName = "adam"
//        val lastName = ""
//        val dateOfBirth = "2016-10-10"
//
//        val url = new URL(s"http://localhost:8099/api/v0/birth?forenames=$firstName&dateofbirth=$dateOfBirth")
//
//        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None),
//          Status.S200_OK,
//          MediaType.APPLICATION_JSON,
//          authRecord.toString())
//
//        val eventResponse = Response.apply(
//          Request.get(url,
//            headers = Headers.apply(headers)
//          ),
//          Status.S400_BadRequest,
//          MediaType.apply("text/plain; charset=UTF-8"),
//          "Must provide lastname parameter")
//
//        when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
//        when(mockHttpClient.get(Matchers.any(), Matchers.any())).thenReturn(eventResponse)
//
//        val result = await(MockBirthConnector.get(firstName, lastName, dateOfBirth))
//
//        result match {
//          case BirthErrorResponse(cause) =>
//            cause shouldBe a[Upstream4xxResponse]
//          case r @ BirthSuccessResponse(json) =>
//            r should not be a[BirthSuccessResponse]
//        }
//      }
//
//      "400 with BadRequest for missing dateofbirth parameter" in {
//        val firstName = "adam"
//        val lastName = "smith"
//        val dateOfBirth = ""
//
//        val url = new URL(s"http://localhost:8099/api/v0/birth?forenames=$firstName&lastname=$lastName")
//
//        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None),
//          Status.S200_OK,
//          MediaType.APPLICATION_JSON,
//          authRecord.toString())
//
//        val eventResponse = Response.apply(
//          Request.get(url,
//            headers = Headers.apply(headers)
//          ),
//          Status.S400_BadRequest,
//          MediaType.apply("text/plain; charset=UTF-8"),
//          "Must provide date of birth parameter")
//
//        when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
//        when(mockHttpClient.get(Matchers.any(), Matchers.any())).thenReturn(eventResponse)
//
//        val result = await(MockBirthConnector.get(firstName, lastName, dateOfBirth))
//
//        result match {
//          case BirthErrorResponse(cause) =>
//            cause shouldBe a[Upstream4xxResponse]
//          case r @ BirthSuccessResponse(json) =>
//            r should not be a[BirthSuccessResponse]
//        }
//      }

    }

  }

}
