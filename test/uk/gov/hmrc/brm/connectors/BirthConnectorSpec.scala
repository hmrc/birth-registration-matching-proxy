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
import play.api.libs.json.JsArray
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
import play.api.test.Helpers._

import scala.util.{Failure, Success}

class BirthConnectorSpec extends UnitSpec with BRMFakeApplication with MockitoSugar with BeforeAndAfter {

  implicit val hc = HeaderCarrier()

  val mockTokenCache = mock[AccessTokenRepository]
  val mockHttpClient = mock[HttpClient]
  object MockAuthenticator extends Authenticator(
    username = GROConnectorConfiguration.username,
    password = GROConnectorConfiguration.password,
    endpoint = s"${GROConnectorConfiguration.serviceUrl}/api/v0/events/birth",
    http = mockHttpClient,
    tokenCache = mockTokenCache,
    metrics = GroMetrics,
    delayTime = 1,
    delayAttempts = 3
  )

  val mockCertificateStatus = mock[CertificateStatus]

  object MockBirthConnector extends BirthConnector {
    override val http = mockHttpClient
    override val metrics = GroMetrics
    override val authenticator = MockAuthenticator
    override val delayTime = 1
    override val delayAttempts = 3
    override val version = GROConnectorConfiguration.version
    override val endpoint = s"${GROConnectorConfiguration.serviceUrl}/api/$version/events/birth"
    override val username = GROConnectorConfiguration.username
    override val encoder = Encoder
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

  "BirthConnector" when running(fakeApplication) {

    "initialising" should {

      "having correct configurations" in {
        GROEnglandAndWalesConnector.delayAttempts shouldBe 3
        GROEnglandAndWalesConnector.delayTime shouldBe 100
        GROEnglandAndWalesConnector.authenticator shouldBe a[Authenticator]
        GROEnglandAndWalesConnector.metrics shouldBe a[Metrics]
        GROEnglandAndWalesConnector.http shouldBe a[HttpClient]
      }

    }

    "parsing json" should {

      "throw Upstream4xxResponse for invalid json" in {
        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099/oauth/login"), None), Status.S200_OK, MediaType.APPLICATION_JSON, authRecord.toString())
        val eventResponse = Response.apply(Request.get(new URL("http://localhost:8099/v0/events/birth"), headers = Headers.apply(headers)), Status.S200_OK, MediaType.APPLICATION_JSON, "[something]")

        when(mockTokenCache.token).thenReturn(Failure(new RuntimeException))
        when(MockBirthConnector.authenticator.http.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
        when(MockBirthConnector.http.get(Matchers.any(), Matchers.any())).thenReturn(eventResponse)

        val result = await(MockBirthConnector.get("500035710"))

        val birthErrorResponse = result.asInstanceOf[BirthErrorResponse]
        birthErrorResponse.cause.isInstanceOf[Upstream5xxResponse] shouldBe true

      }
    }

    "authentication" should {

      "BirthErrorResponse of 4xx when authentication returns BadRequest" in {
        val authResponse = Response.apply(
          Request.post(new URL("http://localhost:8099/oauth/login"), None),
          Status.S400_BadRequest, MediaType.APPLICATION_JSON, ""
        )

        when(mockTokenCache.token).thenReturn(Failure(new RuntimeException))
        when(MockBirthConnector.authenticator.http.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)

        val result = await(MockBirthConnector.get("500035710"))
        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Upstream4xxResponse]
      }

      "BirthErrorResponse when authentication returns 5xx" in {
        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None), Status.S500_InternalServerError, MediaType.APPLICATION_JSON, "")

        when(mockTokenCache.token).thenReturn(Failure(new RuntimeException))
        when(MockBirthConnector.authenticator.http.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)

        val result = await(MockBirthConnector.get("500035710"))
        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Upstream5xxResponse]

      }

      "BirthErrorResponse when certificate has expired" in {
        // Force LocalDate to something other than now
        val date = new DateTime(2050: Int, 9: Int, 15: Int, 5: Int, 10: Int, 10: Int)
        DateTimeUtils.setCurrentMillisFixed(date.getMillis)

        val result = await(MockBirthConnector.get("500035710"))

        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Exception]


        DateTimeUtils.setCurrentMillisSystem()
      }

      "BirthErrorResponse when authentication cache has no access token" in {
        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None), Status.S200_OK, MediaType.APPLICATION_JSON, "")

        when(mockTokenCache.token).thenReturn(Failure(new RuntimeException))
        when(MockBirthConnector.authenticator.http.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)

        val result = await(MockBirthConnector.get("500035710"))
        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Exception]

      }

      "BirthErrorResponse when authentication returns exception" in {
        val json =
          """
            |"reference": "something"
          """.stripMargin
        val eventResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None), Status.S200_OK, MediaType.APPLICATION_JSON, json)

        when(mockTokenCache.token).thenReturn(Failure(new RuntimeException))
        when(MockBirthConnector.authenticator.http.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(eventResponse)

        val result = await(MockBirthConnector.get("500035710"))
        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Exception]

      }

      "BirthErrorResponse 5xx when all attempts fail for authentication (SocketTimeoutException)" in {
        when(MockBirthConnector.authenticator.http.post(Matchers.any(), Matchers.any(), Matchers.any())).thenThrow(new SocketTimeoutException(""))

        val result = await(MockBirthConnector.get("500035710"))
        verify(mockHttpClient, times(3)).post(Matchers.any(), Matchers.any(), Matchers.any())

        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Upstream5xxResponse]

      }

      "BirthErrorResponse when Exception is thrown for authentication" in {
        when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenThrow(new IOException(""))
        val result = await(MockBirthConnector.get("500035710"))

        verify(mockHttpClient, times(1)).post(Matchers.any(), Matchers.any(), Matchers.any())
        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Upstream5xxResponse]
      }

      "BirthSuccessResponse when authenticator has valid token" in {
        when(mockTokenCache.token).thenReturn(Success("token"))
        val eventResponse = Response.apply(Request.get(new URL("http://localhost:8099/v0/events/birth"), headers = Headers.apply(headers)), Status.S200_OK, MediaType.APPLICATION_JSON, groResponse("500035710").toString())
        when(MockBirthConnector.http.get(Matchers.any(), Matchers.any())).thenReturn(eventResponse)
        val result = await(MockBirthConnector.get("500035710"))
        result shouldBe a[BirthSuccessResponse[_]]
      }

    }

    "get" should {

      "BirthSuccessResponse when gro responds with 200" in {
        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099/oauth/login"), None), Status.S200_OK, MediaType.APPLICATION_JSON, authRecord.toString())
        val eventResponse = Response.apply(Request.get(new URL("http://localhost:8099/v0/events/birth"), headers = Headers.apply(headers)), Status.S200_OK, MediaType.APPLICATION_JSON, groResponse("500035710").toString())

        when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
        when(MockBirthConnector.http.get(Matchers.any(), Matchers.any())).thenReturn(eventResponse)

        val result = await(MockBirthConnector.get("500035710"))
        result shouldBe a[BirthSuccessResponse[_]]
      }

      "BirthErrorResponse 4xx when gro returns 404" in {
        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None), Status.S200_OK, MediaType.APPLICATION_JSON, authRecord.toString())
        val eventResponse = Response.apply(Request.get(new URL("http://localhost:8099"), headers = Headers.apply(headers)), Status.S404_NotFound, MediaType.APPLICATION_JSON, groResponse("NoMatch").toString())

        when(mockTokenCache.token).thenReturn(Failure(new RuntimeException))
        when(MockBirthConnector.authenticator.http.post(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(authResponse)
        when(MockBirthConnector.http.get(Matchers.any(), Matchers.any()))
          .thenReturn(eventResponse)

        val result = await(MockBirthConnector.get("500037654675710"))
        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Upstream4xxResponse]

      }

      "BirthErrorResponse 4xx when gro returns BadRequest" in {
        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None), Status.S200_OK, MediaType.APPLICATION_JSON, authRecord.toString())
        val eventResponse = Response.apply(Request.get(new URL("http://localhost:8099"), headers = Headers.apply(headers)), Status.S400_BadRequest, MediaType.APPLICATION_JSON, "")

        when(mockTokenCache.token).thenReturn(Failure(new RuntimeException))
        when(MockBirthConnector.authenticator.http.post(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(authResponse)
        when(MockBirthConnector.http.get(Matchers.any(), Matchers.any())).thenReturn(eventResponse)

        val result = await(MockBirthConnector.get("500035710"))
        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Upstream4xxResponse]
      }

      "BirthErrorResponse 5xx when gro returns InternalServerError" in {
        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None), Status.S200_OK, MediaType.APPLICATION_JSON, authRecord.toString())
        val eventResponse = Response.apply(Request.get(new URL("http://localhost:8099"), headers = Headers.apply(headers)), Status.S500_InternalServerError, MediaType.APPLICATION_JSON, "")

        when(mockTokenCache.token).thenReturn(Failure(new RuntimeException))
        when(MockBirthConnector.authenticator.http.post(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(authResponse)
        when(MockBirthConnector.http.get(Matchers.any(), Matchers.any())).thenReturn(eventResponse)

        val result = await(MockBirthConnector.get("500035710"))

        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Upstream5xxResponse]
      }

      "BirthErrorResponse 5xx when all attempts fail for reference lookup (SocketTimeoutException)" in {
        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099/oauth/login"), None), Status.S200_OK, MediaType.APPLICATION_JSON, authRecord.toString())

        when(mockTokenCache.token).thenReturn(Failure(new RuntimeException))
        when(MockBirthConnector.authenticator.http.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
        when(MockBirthConnector.http.get(Matchers.any(), Matchers.any())).thenThrow(new SocketTimeoutException(""))

        val result = await(MockBirthConnector.get("500035710"))

        verify(mockHttpClient, times(3)).get(Matchers.any(), Matchers.any())
        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Upstream5xxResponse]
      }

      "BirthErrorResponse 5xx when Exception is thrown for reference lookup" in {
        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099/oauth/login"), None), Status.S200_OK, MediaType.APPLICATION_JSON, authRecord.toString())

        when(mockTokenCache.token).thenReturn(Failure(new RuntimeException))
        when(MockBirthConnector.authenticator.http.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
        when(MockBirthConnector.http.get(Matchers.any(), Matchers.any())).thenThrow(new IOException(""))

        val result = await(MockBirthConnector.get("500035710"))

        verify(mockHttpClient, times(1)).get(Matchers.any(), Matchers.any())
        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Upstream5xxResponse]
      }

    }

    "getDetails" should {

      "BirthSuccessResponse when gro details responds with 200 with single record." in {
        val firstName = "adam"
        val lastName = "smith"
        val dateOfBirth = "2016-10-10"

        val url = new URL(s"http://localhost:8099/api/v0/birth?forenames=$firstName&lastname=$lastName&dateofbirth=$dateOfBirth")

        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099/oauth/login"), None),
          Status.S200_OK,
          MediaType.APPLICATION_JSON,
          authRecord.toString())

        val eventResponse = Response.apply(
          Request.get(url,
          headers = Headers.apply(headers)),
          Status.S200_OK,
          MediaType.APPLICATION_JSON,
          groResponse("2006-11-12_smith_adam").toString())

        when(MockBirthConnector.http.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
        when(MockBirthConnector.http.get(Matchers.any(), Matchers.any())).thenReturn(eventResponse)

        val result = await(MockBirthConnector.get(firstName, lastName, dateOfBirth))
        result shouldBe a[BirthSuccessResponse[_]]
        result shouldBe BirthSuccessResponse(groResponse("2006-11-12_smith_adam"))
        result.asInstanceOf[BirthSuccessResponse[JsArray]].json.value.size shouldBe 2
      }

      "BirthSuccessResponse with [] empty response for no records found" in {
        val firstName = "adam"
        val lastName = "smith"
        val dateOfBirth = "2016-10-10"

        val url = new URL(s"http://localhost:8099/api/v0/birth?forenames=$firstName&lastname=$lastName&dateofbirth=$dateOfBirth")

        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None),
          Status.S200_OK,
          MediaType.APPLICATION_JSON,
          authRecord.toString())

        val eventResponse = Response.apply(
          Request.get(url,
            headers = Headers.apply(headers)
          ),
          Status.S200_OK,
          MediaType.APPLICATION_JSON,
          groResponse("NoMatch").toString())

        when(MockBirthConnector.http.post(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(authResponse)
        when(MockBirthConnector.http.get(Matchers.any(), Matchers.any()))
          .thenReturn(eventResponse)

        val result = await(MockBirthConnector.get(firstName, lastName, dateOfBirth))
        result shouldBe a[BirthSuccessResponse[_]]
        result shouldBe BirthSuccessResponse(groResponse("NoMatch"))
        result.asInstanceOf[BirthSuccessResponse[JsArray]].json.value.size shouldBe 0
      }

      "BirthErrorResponse 4xx with BadRequest for missing forenames parameter" in {
        val firstName = ""
        val lastName = "smith"
        val dateOfBirth = "2016-10-10"

        val url = new URL(s"http://localhost:8099/api/v0/birth?lastname=$lastName&dateofbirth=$dateOfBirth")

        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None),
          Status.S200_OK,
          MediaType.APPLICATION_JSON,
          authRecord.toString())

        val eventResponse = Response.apply(
          Request.get(url,
            headers = Headers.apply(headers)
          ),
          Status.S400_BadRequest,
          MediaType.apply("text/plain; charset=UTF-8"),
          "forenames or forename1 is required")

        when(MockBirthConnector.http.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
        when(MockBirthConnector.http.get(Matchers.any(), Matchers.any())).thenReturn(eventResponse)

        val result = await(MockBirthConnector.get(firstName, lastName, dateOfBirth))
        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Upstream4xxResponse]

      }

      "BirthErrorResponse 4xx with BadRequest for missing lastname parameter" in {
        val firstName = "adam"
        val lastName = ""
        val dateOfBirth = "2016-10-10"

        val url = new URL(s"http://localhost:8099/api/v0/birth?forenames=$firstName&dateofbirth=$dateOfBirth")

        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None),
          Status.S200_OK,
          MediaType.APPLICATION_JSON,
          authRecord.toString())

        val eventResponse = Response.apply(
          Request.get(url,
            headers = Headers.apply(headers)
          ),
          Status.S400_BadRequest,
          MediaType.apply("text/plain; charset=UTF-8"),
          "Must provide lastname parameter")

        when(MockBirthConnector.http.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
        when(MockBirthConnector.http.get(Matchers.any(), Matchers.any())).thenReturn(eventResponse)

        val result = await(MockBirthConnector.get(firstName, lastName, dateOfBirth))
        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Upstream4xxResponse]
      }

      "BirthErrorResponse 4xx with BadRequest for missing dateofbirth parameter" in {
        val firstName = "adam"
        val lastName = "smith"
        val dateOfBirth = ""

        val url = new URL(s"http://localhost:8099/api/v0/birth?forenames=$firstName&lastname=$lastName")

        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None),
          Status.S200_OK,
          MediaType.APPLICATION_JSON,
          authRecord.toString())

        val eventResponse = Response.apply(
          Request.get(url,
            headers = Headers.apply(headers)
          ),
          Status.S400_BadRequest,
          MediaType.apply("text/plain; charset=UTF-8"),
          "Must provide date of birth parameter")

        when(MockBirthConnector.http.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
        when(MockBirthConnector.http.get(Matchers.any(), Matchers.any())).thenReturn(eventResponse)

        val result = await(MockBirthConnector.get(firstName, lastName, dateOfBirth))

        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Upstream4xxResponse]
      }

      "BirthErrorResponse when GRO returns 5xx" in {
        val firstName = "adam"
        val lastName = "smith"
        val dateOfBirth = "2010-10-06"

        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None),
          Status.S200_OK,
          MediaType.APPLICATION_JSON,
          authRecord.toString())

        val eventResponse = Response.apply(
          Request.get(new URL("http://localhost:8099"),
            headers = Headers.apply(headers)),
          Status.S500_InternalServerError,
          MediaType.APPLICATION_JSON, "")

        when(MockBirthConnector.authenticator.http.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
        when(MockBirthConnector.http.get(Matchers.any(), Matchers.any())).thenReturn(eventResponse)

        val result = await(MockBirthConnector.get(firstName, lastName, dateOfBirth))
        verify(MockBirthConnector.http, times(1)).get(Matchers.any(), Matchers.any())

        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Upstream5xxResponse]
      }

      "BirthErrorResponse when GRO throws exception" in {
        val firstName = "adam"
        val lastName = "smith"
        val dateOfBirth = "2010-10-06"

        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None),
          Status.S200_OK,
          MediaType.APPLICATION_JSON,
          authRecord.toString())

        when(MockBirthConnector.authenticator.http.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
        when(MockBirthConnector.http.get(Matchers.any(), Matchers.any())).thenThrow(new IOException(""))

        val result = await(MockBirthConnector.get(firstName, lastName, dateOfBirth))
        verify(MockBirthConnector.http, times(1)).get(Matchers.any(), Matchers.any())

        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Upstream5xxResponse]
      }

      "BirthErrorResponse when all attempts fail [SocketTimeoutException]" in {
        val firstName = "adam"
        val lastName = "smith"
        val dateOfBirth = "2010-10-06"

        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None),
          Status.S200_OK,
          MediaType.APPLICATION_JSON,
          authRecord.toString())

        when(MockBirthConnector.authenticator.http.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
        when(MockBirthConnector.http.get(Matchers.any(), Matchers.any())).thenThrow(new SocketTimeoutException(""))

        val result = await(MockBirthConnector.get(firstName, lastName, dateOfBirth))
        verify(MockBirthConnector.http, times(3)).get(Matchers.any(), Matchers.any())

        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Upstream5xxResponse]
      }

    }

  }

}
