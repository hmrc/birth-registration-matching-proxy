/*
 * Copyright 2020 HM Revenue & Customs
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
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.specs2.mock.mockito.ArgumentCapture
import play.api.libs.json.{JsArray, JsValue}
import uk.co.bigbeeconsultants.http.HttpClient
import uk.co.bigbeeconsultants.http.header.{Headers, MediaType}
import uk.co.bigbeeconsultants.http.request.Request
import uk.co.bigbeeconsultants.http.response.{Response, Status}
import uk.gov.hmrc.brm.TestFixture
import uk.gov.hmrc.brm.metrics.BRMMetrics
import uk.gov.hmrc.brm.tls.HttpClientFactory
import uk.gov.hmrc.brm.utils.{AccessTokenRepository, CertificateStatus, JsonUtils}
import uk.gov.hmrc.http.{HeaderCarrier, Upstream4xxResponse, Upstream5xxResponse}

import scala.util.{Failure, Success}


class GROEnglandAndWalesConnectorSpec extends TestFixture {

  val mockTokenCache: AccessTokenRepository = mock[AccessTokenRepository]
  val mockHttpClient: HttpClient = mock[HttpClient]

  val mockAuthenticator: Authenticator =
    new Authenticator(testProxyConfig, testGroConfig, mock[HttpClientFactory], mock[ProxyAuthenticator], mock[CertificateStatus]) {
      override val http: HttpClient = mockHttpClient
      override val tokenCache: AccessTokenRepository = mockTokenCache
  }

  val testConnector: GROEnglandAndWalesConnector =
    new GROEnglandAndWalesConnector(testGroConfig, testProxyConfig, mock[HttpClientFactory], mockAuthenticator) {
      override val http: HttpClient = mockHttpClient
  }

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val authRecord: JsValue = JsonUtils.getJsonFromFile("gro/auth")

  def groResponse(reference: String): JsValue = JsonUtils.getJsonFromFile(s"gro/$reference")

  before(
    reset(mockHttpClient)
  )

  trait AuthenticationFixture {

    implicit val metrics: BRMMetrics = mock[BRMMetrics]

    when(mockTokenCache.token).thenReturn(Failure(new RuntimeException))

    def buildResponse(authRecord: String, status: Status, mediaType: MediaType): Response = {
      Response.apply(
        Request.post(new URL("http://localhost:8099"), None),
        status, mediaType, authRecord
      )
    }

    def result: BirthResponse = {
      await(testConnector.getReference("500035710"))
    }
  }

  "BirthConnector" when {

    "initialising" should {

      "having correct configurations" in {
        testConnector.delayAttempts shouldBe 3
        testConnector.delayTime shouldBe 5000
        testConnector.authenticator shouldBe a[Authenticator]
        testConnector.http shouldBe a[HttpClient]
      }
    }

    "parsing json" should {

      "throw Upstream5xxResponse for invalid json" in new AuthenticationFixture {
        val authResponse: Response = authSuccessResponse(authRecord)
        val eventResponse: Response = eventResponseWithStatus (Status.S200_OK,"[something]")

        when(testConnector.authenticator.http.post(any(), any(), any())).thenReturn(authResponse)
        when(testConnector.http.get(any(), any())).thenReturn(eventResponse)

        val birthErrorResponse: BirthErrorResponse = result.asInstanceOf[BirthErrorResponse]
        birthErrorResponse.cause.isInstanceOf[Upstream5xxResponse] shouldBe true
      }
    }

    "authentication" should {

      implicit val metrics: BRMMetrics = mock[BRMMetrics]

      "return 4xx when authentication returns BadRequest" in new AuthenticationFixture {
        val authResponse: Response = buildResponse("", Status.S400_BadRequest, MediaType.APPLICATION_JSON)
        when(testConnector.authenticator.http.post(any(), any(), any())).thenReturn(authResponse)
        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Upstream4xxResponse]
      }

      "return 5xx when authentication returns 5xx" in new AuthenticationFixture {
        val authResponse: Response = buildResponse("", Status.S500_InternalServerError, MediaType.APPLICATION_JSON)
        when(testConnector.authenticator.http.post(any(), any(), any())).thenReturn(authResponse)
        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Upstream5xxResponse]
      }

      "return exception when certificate has expired" in new AuthenticationFixture {
        // Force LocalDate to something other than now
        val date = new DateTime(2050: Int, 9: Int, 15: Int, 5: Int, 10: Int, 10: Int)
        DateTimeUtils.setCurrentMillisFixed(date.getMillis)

        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Exception]

        DateTimeUtils.setCurrentMillisSystem()
      }

      "return exception when authentication cache has no access token" in new AuthenticationFixture {
        val authResponse: Response = buildResponse("", Status.S200_OK, MediaType.APPLICATION_JSON)
        when(testConnector.authenticator.http.post(any(), any(), any())).thenReturn(authResponse)
        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Exception]
      }

      "return exception when authentication returns exception" in new AuthenticationFixture {
        val json: String =
          """
            |"reference": "something"
          """.stripMargin
        val eventResponse: Response = eventResponseWithStatus (Status.S200_OK,json)

        when(testConnector.authenticator.http.post(any(), any(), any())).thenReturn(eventResponse)

        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Exception]
      }

      "BirthErrorResponse 5xx when all attempts fail for authentication (SocketTimeoutException)" in new AuthenticationFixture {
        when(testConnector.authenticator.http.post(any(), any(), any())).thenThrow(new SocketTimeoutException(""))
        await(testConnector.getReference("500035710"))
        verify(mockHttpClient, times(3)).post(any(), any(), any())
      }

      "BirthErrorResponse when Exception is thrown for authentication" in new AuthenticationFixture {
        when(mockHttpClient.post(any(), any(), any())).thenThrow(new IOException(""))
        val response: BirthResponse = await(testConnector.getReference("500035710"))
        verify(mockHttpClient, times(1)).post(any(), any(), any())
        response shouldBe a[BirthErrorResponse]
        response.asInstanceOf[BirthErrorResponse].cause shouldBe a[Upstream5xxResponse]
      }

      "BirthSuccessResponse when authenticator has valid token" in new AuthenticationFixture {
        when(mockTokenCache.token).thenReturn(Success("token"))
        val eventResponse: Response =  eventSuccessResponse(groResponse("500035710"))
        when(testConnector.http.get(any(), any())).thenReturn(eventResponse)
        val response: BirthResponse = await(testConnector.getReference("500035710"))
        response shouldBe a[BirthSuccessResponse[_]]
      }

    }

    "get reference" should {

      "BirthSuccessResponse when gro responds with 200 for reference" in {
        implicit val metrics: BRMMetrics = new BRMMetrics(testProxyConfig)

        val authResponse = authSuccessResponse(authRecord)
        val eventResponse = eventSuccessResponse(groResponse("500035710"))

        when(mockTokenCache.token).thenReturn(Success("token"))
        when(testConnector.authenticator.http.post(any(), any(), any())).thenReturn(authResponse)
        when(testConnector.http.get(any(), any())).thenReturn(eventResponse)

        val result = await(testConnector.getReference("500035710"))
        result shouldBe a[BirthSuccessResponse[_]]
        metrics.defaultRegistry.counter(s"${metrics.prefix}-request-count").getCount shouldBe 1
      }

      "BirthErrorResponse 4xx when gro returns 404" in {
        implicit val metrics: BRMMetrics = new BRMMetrics(testProxyConfig)

        val authResponse = authSuccessResponse(authRecord)
        val eventResponse = eventResponseWithStatus (Status.S404_NotFound,groResponse("NoMatch").toString())

        when(mockTokenCache.token).thenReturn(Failure(new RuntimeException))
        when(testConnector.authenticator.http.post(any(), any(), any())).thenReturn(authResponse)
        when(testConnector.http.get(any(), any())).thenReturn(eventResponse)

        val result = await(testConnector.getReference("500037654675710"))
        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Upstream4xxResponse]
      }

      "BirthErrorResponse 4xx when gro returns BadRequest" in {
        implicit val metrics: BRMMetrics = new BRMMetrics(testProxyConfig)
        val authResponse = authSuccessResponse(authRecord)
        val eventResponse = eventResponseWithStatus (Status.S400_BadRequest,"")

        when(mockTokenCache.token).thenReturn(Failure(new RuntimeException))
        when(testConnector.authenticator.http.post(any(), any(), any()))
          .thenReturn(authResponse)
        when(testConnector.http.get(any(), any())).thenReturn(eventResponse)

        val result = await(testConnector.getReference("500035710"))
        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Upstream4xxResponse]
      }

      "BirthErrorResponse 4xx when gro returns unexpected response" in {
        implicit val metrics: BRMMetrics = new BRMMetrics(testProxyConfig)
        val authResponse = authSuccessResponse(authRecord)
        val eventResponse = eventResponseWithStatus (Status(600, "unknown"),"")

        when(mockTokenCache.token).thenReturn(Failure(new RuntimeException))
        when(testConnector.authenticator.http.post(any(), any(), any()))
          .thenReturn(authResponse)
        when(testConnector.http.get(any(), any())).thenReturn(eventResponse)

        val result = await(testConnector.getReference("500035710"))
        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Upstream4xxResponse]
      }

      "BirthErrorResponse 5xx when gro returns InternalServerError" in {
        implicit val metrics: BRMMetrics = new BRMMetrics(testProxyConfig)
        val authResponse = authSuccessResponse(authRecord)
        val eventResponse = eventResponseWithStatus (Status.S500_InternalServerError,"")
        when(mockTokenCache.token).thenReturn(Failure(new RuntimeException))
        when(testConnector.authenticator.http.post(any(), any(), any()))
          .thenReturn(authResponse)
        when(testConnector.http.get(any(), any())).thenReturn(eventResponse)

        val result = await(testConnector.getReference("500035710"))

        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Upstream5xxResponse]
      }

      "BirthErrorResponse 5xx when all attempts fail for reference lookup (SocketTimeoutException)" in {
        implicit val metrics: BRMMetrics = new BRMMetrics(testProxyConfig)
        val authResponse = authSuccessResponse(authRecord)

        when(mockTokenCache.token).thenReturn(Failure(new RuntimeException))
        when(testConnector.authenticator.http.post(any(), any(), any())).thenReturn(authResponse)
        when(testConnector.http.get(any(), any())).thenThrow(new SocketTimeoutException(""))

        val result = await(testConnector.getReference("500035710"))

        verify(mockHttpClient, times(3)).get(any(), any())
        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Upstream5xxResponse]
      }

      "BirthErrorResponse 5xx when Exception is thrown for reference lookup" in {
        implicit val metrics: BRMMetrics = new BRMMetrics(testProxyConfig)
        val authResponse = authSuccessResponse(authRecord)

        when(mockTokenCache.token).thenReturn(Failure(new RuntimeException))
        when(testConnector.authenticator.http.post(any(), any(), any())).thenReturn(authResponse)
        when(testConnector.http.get(any(), any())).thenThrow(new IOException(""))

        val result = await(testConnector.getReference("500035710"))

        verify(mockHttpClient, times(1)).get(any(), any())
        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Upstream5xxResponse]
      }
    }

    "get details" should {

      "BirthSuccessResponse when gro details responds with 200 with single record." in {
        implicit val metrics: BRMMetrics = new BRMMetrics(testProxyConfig)

        val firstName = "adam"
        val lastName = "smith"
        val dateOfBirth = "2016-10-10"

        val url = new URL(s"http://localhost:8099/api/v0/birth?forenames=$firstName&lastname=$lastName&dateofbirth=$dateOfBirth")

        val authResponse = authSuccessResponse(authRecord)
        val eventResponse =  eventSuccessResponse(groResponse("2006-11-12_smith_adam"))

        val argumentCapture = new ArgumentCapture[URL]

        when(mockTokenCache.token).thenReturn(Success("token"))
        when(testConnector.http.post(any(), any(), any())).thenReturn(authResponse)
        when(testConnector.http.get(argumentCapture.capture, any())).thenReturn(eventResponse)

        val result = await(testConnector.getDetails(firstName, lastName, dateOfBirth))
        result shouldBe a[BirthSuccessResponse[_]]
        result shouldBe BirthSuccessResponse(groResponse("2006-11-12_smith_adam"))
        result.asInstanceOf[BirthSuccessResponse[JsArray]].json.value.size shouldBe 2
        metrics.defaultRegistry.counter(s"${metrics.prefix}-details-request-count").getCount shouldBe 1
        argumentCapture.value.getQuery shouldBe getUrlEncodeString(firstName,lastName,dateOfBirth)
      }


      "BirthSuccessResponse when gro details responds with 200 with single record when request has special character." in {
        implicit val metrics: BRMMetrics = new BRMMetrics(testProxyConfig)

        val firstName = "Adàm TËST"
        val lastName = "SMÏTH"
        val dateOfBirth = "2006-11-12"

        val url = new URL(s"http://localhost:8099/api/v0/birth?forenames=$firstName&lastname=$lastName&dateofbirth=$dateOfBirth")

        val authResponse = authSuccessResponse(authRecord)
        val eventResponse =  eventSuccessResponse(groResponse("2006-11-12_smith_adam-utf-8"))

        val argumentCapture = new ArgumentCapture[URL]

        when(mockTokenCache.token).thenReturn(Success("token"))
        when(testConnector.http.post(any(), any(), any())).thenReturn(authResponse)
        when(testConnector.http.get(argumentCapture.capture, any())).thenReturn(eventResponse)

        val result = await(testConnector.getDetails(firstName, lastName, dateOfBirth))
        result shouldBe a[BirthSuccessResponse[_]]
        result shouldBe BirthSuccessResponse(groResponse("2006-11-12_smith_adam-utf-8"))
        result.asInstanceOf[BirthSuccessResponse[JsArray]].json.value.size shouldBe 2
        argumentCapture.value.getQuery shouldBe getUrlEncodeString(firstName,lastName,dateOfBirth)

      }

      "BirthSuccessResponse with [] empty response for no records found" in {
        implicit val metrics: BRMMetrics = new BRMMetrics(testProxyConfig)

        val firstName = "adam"
        val lastName = "smith"
        val dateOfBirth = "2016-10-10"

        val url = new URL(s"http://localhost:8099/api/v0/birth?forenames=$firstName&lastname=$lastName&dateofbirth=$dateOfBirth")

        val authResponse = authSuccessResponse(authRecord)
        val eventResponse =  eventSuccessResponse(groResponse("NoMatch"))

        val argumentCapture = new ArgumentCapture[URL]
        when(mockTokenCache.token).thenReturn(Success("token"))
        when(testConnector.http.post(any(), any(), any())).thenReturn(authResponse)
        when(testConnector.http.get(argumentCapture.capture, any())).thenReturn(eventResponse)

        val result = await(testConnector.getDetails(firstName, lastName, dateOfBirth))
        result shouldBe a[BirthSuccessResponse[_]]
        result shouldBe BirthSuccessResponse(groResponse("NoMatch"))
        result.asInstanceOf[BirthSuccessResponse[JsArray]].json.value.size shouldBe 0
        argumentCapture.value.getQuery shouldBe getUrlEncodeString(firstName,lastName,dateOfBirth)
      }

      "BirthErrorResponse 4xx with BadRequest for missing forenames parameter" in {
        implicit val metrics: BRMMetrics = new BRMMetrics(testProxyConfig)

        val firstName = ""
        val lastName = "smith"
        val dateOfBirth = "2016-10-10"

        val url = new URL(s"http://localhost:8099/api/v0/birth?lastname=$lastName&dateofbirth=$dateOfBirth")

        val authResponse = authSuccessResponse(authRecord)

        val eventResponse = Response.apply(
          Request.get(url,
            headers = Headers.apply(headers)
          ),
          Status.S400_BadRequest,
          MediaType.apply("text/plain; charset=UTF-8"),
          "forenames or forename1 is required")
        val argumentCapture = new ArgumentCapture[URL]
        when(mockTokenCache.token).thenReturn(Success("token"))
        when(testConnector.http.post(any(), any(), any())).thenReturn(authResponse)
        when(testConnector.http.get(argumentCapture.capture, any())).thenReturn(eventResponse)

        val result = await(testConnector.getDetails(firstName, lastName, dateOfBirth))
        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Upstream4xxResponse]
        argumentCapture.value.getQuery shouldBe getUrlEncodeString(firstName,lastName,dateOfBirth)
      }

      "BirthErrorResponse 4xx with BadRequest for missing lastname parameter" in {
        implicit val metrics: BRMMetrics = new BRMMetrics(testProxyConfig)

        val firstName = "adam"
        val lastName = ""
        val dateOfBirth = "2016-10-10"

        val url = new URL(s"http://localhost:8099/api/v0/birth?forenames=$firstName&dateofbirth=$dateOfBirth")

        val authResponse = authSuccessResponse(authRecord)

        val eventResponse = Response.apply(
          Request.get(url,
            headers = Headers.apply(headers)
          ),
          Status.S400_BadRequest,
          MediaType.apply("text/plain; charset=UTF-8"),
          "Must provide lastname parameter")
        val argumentCapture = new ArgumentCapture[URL]
        when(mockTokenCache.token).thenReturn(Success("token"))
        when(testConnector.http.post(any(), any(), any())).thenReturn(authResponse)
        when(testConnector.http.get(argumentCapture.capture, any())).thenReturn(eventResponse)

        val result = await(testConnector.getDetails(firstName, lastName, dateOfBirth))
        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Upstream4xxResponse]
        argumentCapture.value.getQuery shouldBe getUrlEncodeString(firstName,lastName,dateOfBirth)
      }

      "BirthErrorResponse 4xx with BadRequest for missing dateofbirth parameter" in {
        implicit val metrics: BRMMetrics = new BRMMetrics(testProxyConfig)

        val firstName = "adam"
        val lastName = "smith"
        val dateOfBirth = ""

        val url = new URL(s"http://localhost:8099/api/v0/birth?forenames=$firstName&lastname=$lastName")

        val authResponse = authSuccessResponse(authRecord)
        val eventResponse = Response.apply(
          Request.get(url,
            headers = Headers.apply(headers)
          ),
          Status.S400_BadRequest,
          MediaType.apply("text/plain; charset=UTF-8"),
          "Must provide date of birth parameter")
        val argumentCapture = new ArgumentCapture[URL]
        when(mockTokenCache.token).thenReturn(Success("token"))
        when(testConnector.http.post(any(), any(), any())).thenReturn(authResponse)
        when(testConnector.http.get(argumentCapture.capture, any())).thenReturn(eventResponse)

        val result = await(testConnector.getDetails(firstName, lastName, dateOfBirth))

        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Upstream4xxResponse]
        argumentCapture.value.getQuery shouldBe getUrlEncodeString(firstName,lastName,dateOfBirth)
      }

      "BirthErrorResponse when GRO returns 5xx" in {
        implicit val metrics: BRMMetrics = new BRMMetrics(testProxyConfig)

        val firstName = "adam"
        val lastName = "smith"
        val dateOfBirth = "2010-10-06"

        val authResponse = authSuccessResponse(authRecord)
        val eventResponse = eventResponseWithStatus (Status.S500_InternalServerError,"")
        when(mockTokenCache.token).thenReturn(Failure(new RuntimeException))
        when(testConnector.authenticator.http.post(any(), any(), any())).thenReturn(authResponse)
        when(testConnector.http.get(any(), any())).thenReturn(eventResponse)

        val result = await(testConnector.getDetails(firstName, lastName, dateOfBirth))
        verify(testConnector.http, times(1)).get(any(), any())

        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Upstream5xxResponse]
      }

      "BirthErrorResponse when GRO throws exception" in {
        implicit val metrics: BRMMetrics = new BRMMetrics(testProxyConfig)

        val firstName = "adam"
        val lastName = "smith"
        val dateOfBirth = "2010-10-06"

        val authResponse = authSuccessResponse(authRecord)
        when(mockTokenCache.token).thenReturn(Failure(new RuntimeException))
        when(testConnector.authenticator.http.post(any(), any(), any())).thenReturn(authResponse)
        when(testConnector.http.get(any(), any())).thenThrow(new IOException(""))

        val result = await(testConnector.getDetails(firstName, lastName, dateOfBirth))
        verify(testConnector.http, times(1)).get(any(), any())

        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Upstream5xxResponse]
      }

      "BirthErrorResponse when all attempts fail [SocketTimeoutException]" in {
        implicit val metrics: BRMMetrics = new BRMMetrics(testProxyConfig)

        val firstName = "adam"
        val lastName = "smith"
        val dateOfBirth = "2010-10-06"

        val authResponse = authSuccessResponse(authRecord)
        when(mockTokenCache.token).thenReturn(Failure(new RuntimeException))
        when(testConnector.authenticator.http.post(any(), any(), any())).thenReturn(authResponse)
        when(testConnector.http.get(any(), any())).thenThrow(new SocketTimeoutException(""))

        val result = await(testConnector.getDetails(firstName, lastName, dateOfBirth))
        verify(testConnector.http, times(3)).get(any(), any())

        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Upstream5xxResponse]
      }

      "return a BirthErrorResponse when token has expired and unable to obtain a new token" in {
        implicit val metrics: BRMMetrics = mock[BRMMetrics]

        val firstName = "adam"
        val lastName = "smith"
        val dateOfBirth = "2010-10-06"

        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None), Status.S500_InternalServerError, MediaType.APPLICATION_JSON, "")

        when(testConnector.authenticator.tokenCache.token).thenReturn(Failure(new RuntimeException))
        when(testConnector.authenticator.http.post(any(), any(), any())).thenReturn(authResponse)

        val result = await(testConnector.getDetails(firstName, lastName, dateOfBirth))

        result shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[Upstream5xxResponse]
      }

    }
  }
}