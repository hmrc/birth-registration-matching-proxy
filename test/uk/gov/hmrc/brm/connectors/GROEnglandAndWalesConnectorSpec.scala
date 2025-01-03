/*
 * Copyright 2024 HM Revenue & Customs
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

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status
import play.api.libs.json.{JsArray, JsValue}
import play.api.libs.ws.WSClient
import uk.gov.hmrc.brm.TestFixture
import uk.gov.hmrc.brm.metrics.BRMMetrics
import uk.gov.hmrc.brm.utils.{AccessTokenRepository, CertificateStatus, JsonUtils, TimeProvider}
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{GatewayTimeoutException, HeaderCarrier, HttpReads, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.audit.http.HttpAuditing

import java.net.URL
import java.time.{ZoneId, ZonedDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class GROEnglandAndWalesConnectorSpec extends TestFixture with ScalaFutures {

  val mockTokenCache: AccessTokenRepository = mock[AccessTokenRepository]
  val mockWsClient: WSClient                = mock[WSClient]
  val mockAuditing: HttpAuditing            = mock[HttpAuditing]
  val mockHttpClient: HttpClientV2          = mock[HttpClientV2]
  val mockResponseHandler: ResponseHandler  = mock[ResponseHandler]
  val mockErrorHandler: ErrorHandler        = mock[ErrorHandler]
  val mockRequestBuilderGet                 = mock[RequestBuilder]
  val mockRequestBuilderPost                = mock[RequestBuilder]

  val mockAuthenticator: Authenticator =
    new Authenticator(testGroConfig, real[CertificateStatus], mockHttpClient, new TimeProvider) {
      override val tokenCache: AccessTokenRepository = mockTokenCache
      override val responseHandler: ResponseHandler  = mockResponseHandler
      override val errorHandler: ErrorHandler        = mockErrorHandler
    }

  val testConnector: GROEnglandAndWalesConnector =
    new GROEnglandAndWalesConnector(
      testGroConfig,
      mockHttpClient,
      mockAuthenticator
    )

  when(mockResponseHandler.handle(any())(any(), any())(any[ExecutionContext]))
    .thenReturn(Future(BirthAccessTokenResponse("some token")))

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val authRecord: JsValue = JsonUtils.getJsonFromFile("gro/auth")

  lazy val testCredentials: Map[String, Seq[String]] = Map(
    "username"      -> Seq("username"),
    "password"      -> Seq("key"),
    "client_id"     -> Seq("clientID"),
    "client_secret" -> Seq("clientSecret"),
    "grant_type"    -> Seq("password")
  )

  lazy val refNumber: String = "500035710"

  lazy val testHeaders: Seq[(String, String)] = Seq.empty

  lazy val path: String = "http://localhost:8099/api/v0/events/birth?"

  def groResponse(reference: String): JsValue = JsonUtils.getJsonFromFile(s"gro/$reference")

  before {
    reset(mockHttpClient, mockRequestBuilderGet, mockRequestBuilderPost)

    when(mockHttpClient.post(any())(any())).thenReturn(mockRequestBuilderPost)
    when(mockHttpClient.get(any())(any())).thenReturn(mockRequestBuilderGet)

    when(mockRequestBuilderPost.setHeader(any())).thenReturn(mockRequestBuilderPost)
    when(mockRequestBuilderGet.setHeader(any())).thenReturn(mockRequestBuilderGet)

    when(mockRequestBuilderGet.withProxy).thenReturn(mockRequestBuilderGet)
    when(mockRequestBuilderPost.withProxy).thenReturn(mockRequestBuilderPost)

    when(mockRequestBuilderPost.withBody(any())(any(), any(), any())).thenReturn(mockRequestBuilderPost)

  }

  trait AuthenticationFixture {

    implicit val metrics: BRMMetrics = mock[BRMMetrics]

    when(mockTokenCache.token).thenReturn(Failure(new RuntimeException))

    def buildResponse(status: Int): HttpResponse =
      HttpResponse(
        status,
        authRecord.toString()
      )

    def result: BirthResponse =
      testConnector.getReference(refNumber).futureValue
  }

  "BirthConnector" when {

    "parsing json" should {

      "throw Upstream5xxResponse for invalid json" in new AuthenticationFixture {
        val authResponse: HttpResponse  = authSuccessResponse(authRecord)
        val eventResponse: HttpResponse = eventResponseWithStatus(Status.OK, "[something]")

        when(
          mockRequestBuilderPost
            .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])
        ).thenReturn(Future.successful(authResponse))

        when(
          mockRequestBuilderGet
            .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])
        ).thenReturn(Future.successful(eventResponse))

        val birthErrorResponse: BirthErrorResponse = result.asInstanceOf[BirthErrorResponse]
        birthErrorResponse.cause.isInstanceOf[UpstreamErrorResponse] shouldBe true
      }
    }

    "authentication" should {

      "return 4xx when authentication returns BadRequest" in new AuthenticationFixture {
        val testConnector: GROEnglandAndWalesConnector =
          new GROEnglandAndWalesConnector(
            testGroConfig,
            mockHttpClient,
            mockAuthenticator
          ) {
            override val responseHandler: ResponseHandler = mock[ResponseHandler]
            override val http: HttpClientV2               = mockHttpClient
          }

        val authResponse: HttpResponse = buildResponse(Status.BAD_REQUEST)

        when(
          mockRequestBuilderPost
            .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])
        ).thenReturn(Future.successful(authResponse))

        when(testConnector.responseHandler.handle(any())(any(), any())(any()))
          .thenReturn(Future.successful(BirthErrorResponse(UpstreamErrorResponse("message", Status.NOT_FOUND))))

        testConnector.getReference(refNumber).futureValue shouldBe a[BirthErrorResponse]
        testConnector
          .getReference(refNumber)
          .futureValue
          .asInstanceOf[BirthErrorResponse]
          .cause                                          shouldBe a[UpstreamErrorResponse]
      }

      "return 5xx when authentication returns 5xx" in new AuthenticationFixture {
        val testConnector: GROEnglandAndWalesConnector =
          new GROEnglandAndWalesConnector(
            testGroConfig,
            mockHttpClient,
            mockAuthenticator
          ) {
            override val responseHandler: ResponseHandler = mock[ResponseHandler]
          }

        val authResponse: HttpResponse = buildResponse(Status.BAD_REQUEST)

        when(
          mockRequestBuilderPost
            .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])
        ).thenReturn(Future.successful(authResponse))

        when(testConnector.responseHandler.handle(any())(any(), any())(any()))
          .thenReturn(
            Future.successful(BirthErrorResponse(UpstreamErrorResponse("message", Status.INTERNAL_SERVER_ERROR)))
          )

        testConnector.getReference(refNumber).futureValue shouldBe a[BirthErrorResponse]

        testConnector
          .getReference(refNumber)
          .futureValue
          .asInstanceOf[BirthErrorResponse]
          .cause shouldBe a[UpstreamErrorResponse]
      }

      "return exception when certificate has expired" in new AuthenticationFixture {

        val mockTimeProvider                 = mock[TimeProvider]
        val mockAuthenticator: Authenticator =
          new Authenticator(testGroConfig, real[CertificateStatus], mockHttpClient, mockTimeProvider) {
            override val tokenCache: AccessTokenRepository = mockTokenCache
            override val responseHandler: ResponseHandler  = mockResponseHandler
            override val errorHandler: ErrorHandler        = mockErrorHandler
          }

        val testConnector: GROEnglandAndWalesConnector =
          new GROEnglandAndWalesConnector(
            testGroConfig,
            mockHttpClient,
            mockAuthenticator
          ) {
            override val http: HttpClientV2               = mockHttpClient
            override val responseHandler: ResponseHandler = mock[ResponseHandler]
          }

        when(testConnector.responseHandler.handle(any())(any(), any())(any()))
          .thenReturn(
            Future.successful(BirthErrorResponse(UpstreamErrorResponse("message", Status.INTERNAL_SERVER_ERROR)))
          )

        // Force LocalDate to the past so cert is expired
        val date = ZonedDateTime.of(2000, 9, 15, 5, 10, 10, 0, ZoneId.of("GMT"))

        when(mockTimeProvider.now) thenReturn date

        testConnector.getReference(refNumber).futureValue                                        shouldBe a[BirthErrorResponse]
        testConnector.getReference(refNumber).futureValue.asInstanceOf[BirthErrorResponse].cause shouldBe an[Exception]
      }

      "return exception when authentication cache has no access token" in new AuthenticationFixture {
        val testConnector: GROEnglandAndWalesConnector =
          new GROEnglandAndWalesConnector(
            testGroConfig,
            mockHttpClient,
            mockAuthenticator
          ) {
            override val http: HttpClientV2               = mockHttpClient
            override val responseHandler: ResponseHandler = mock[ResponseHandler]
          }
        when(testConnector.responseHandler.handle(any())(any(), any())(any()))
          .thenReturn(
            Future.successful(BirthErrorResponse(UpstreamErrorResponse("message", Status.INTERNAL_SERVER_ERROR)))
          )

        val authResponse: HttpResponse = buildResponse(Status.OK)

        when(
          mockRequestBuilderPost
            .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])
        ).thenReturn(Future.successful(authResponse))

        testConnector.getReference(refNumber).futureValue                                        shouldBe a[BirthErrorResponse]
        testConnector.getReference(refNumber).futureValue.asInstanceOf[BirthErrorResponse].cause shouldBe a[Exception]
      }

      "return exception when authentication returns exception" in new AuthenticationFixture {
        val testConnector: GROEnglandAndWalesConnector =
          new GROEnglandAndWalesConnector(
            testGroConfig,
            mockHttpClient,
            mockAuthenticator
          ) {
            override val http: HttpClientV2               = mockHttpClient
            override val responseHandler: ResponseHandler = mock[ResponseHandler]
          }
        when(testConnector.responseHandler.handle(any())(any(), any())(any()))
          .thenReturn(
            Future.successful(BirthErrorResponse(UpstreamErrorResponse("message", Status.INTERNAL_SERVER_ERROR)))
          )

        val json: String                =
          """
            |"reference": "something"
          """.stripMargin
        val eventResponse: HttpResponse = eventResponseWithStatus(Status.OK, json)

        when(
          mockRequestBuilderPost
            .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])
        ).thenReturn(Future.successful(eventResponse))

        testConnector.getReference(refNumber).futureValue                                        shouldBe a[BirthErrorResponse]
        testConnector.getReference(refNumber).futureValue.asInstanceOf[BirthErrorResponse].cause shouldBe a[Exception]
      }

      "BirthSuccessResponse when authenticator has valid token" in new AuthenticationFixture {
        when(mockTokenCache.token).thenReturn(Success("token"))
        val eventResponse: HttpResponse = eventSuccessResponse(groResponse(refNumber))

        when(
          mockRequestBuilderGet
            .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])
        ).thenReturn(Future.successful(eventResponse))

        val response: BirthResponse = testConnector.getReference(refNumber).futureValue
        response shouldBe a[BirthSuccessResponse[_]]
      }

    }

    "get reference" should {

      "BirthSuccessResponse when gro responds with 200 for reference" in {
        implicit val metrics: BRMMetrics = new BRMMetrics

        val authResponse  = authSuccessResponse(authRecord)
        val eventResponse = eventSuccessResponse(groResponse(refNumber))

        when(mockTokenCache.token).thenReturn(Success("token"))

        when(
          mockRequestBuilderPost
            .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])
        ).thenReturn(Future.successful(authResponse))

        when(
          mockRequestBuilderGet
            .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])
        ).thenReturn(Future.successful(eventResponse))

        val result = testConnector.getReference(refNumber).futureValue
        result                                                                       shouldBe a[BirthSuccessResponse[_]]
        metrics.defaultRegistry.counter(s"${metrics.prefix}-request-count").getCount shouldBe 1
      }

      "BirthErrorResponse 4xx when gro returns 404" in {
        implicit val metrics: BRMMetrics = new BRMMetrics

        val authResponse  = authSuccessResponse(authRecord)
        val eventResponse = eventResponseWithStatus(Status.NOT_FOUND, groResponse("NoMatch").toString())

        when(mockTokenCache.token).thenReturn(Failure(new RuntimeException))
        when(
          mockRequestBuilderPost
            .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])
        ).thenReturn(Future.successful(authResponse))

        when(
          mockRequestBuilderGet
            .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])
        ).thenReturn(Future.successful(eventResponse))

        val result = testConnector.getReference("500037654675710").futureValue
        result                                           shouldBe a[Birth404ErrorResponse]
        result.asInstanceOf[Birth404ErrorResponse].cause shouldBe a[UpstreamErrorResponse]
      }

      "BirthErrorResponse 4xx when gro returns BadRequest" in {
        implicit val metrics: BRMMetrics = new BRMMetrics
        val authResponse                 = authSuccessResponse(authRecord)
        val eventResponse                = eventResponseWithStatus(Status.BAD_REQUEST, "")

        when(mockTokenCache.token).thenReturn(Failure(new RuntimeException))

        when(
          mockRequestBuilderPost
            .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])
        ).thenReturn(Future.successful(authResponse))

        when(
          mockRequestBuilderGet
            .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])
        ).thenReturn(Future.successful(eventResponse))

        val result = testConnector.getReference(refNumber).futureValue
        result                                        shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[UpstreamErrorResponse]
      }

      "BirthErrorResponse 5xx when gro returns InternalServerError" in {
        implicit val metrics: BRMMetrics = new BRMMetrics
        val authResponse                 = authSuccessResponse(authRecord)
        val eventResponse                = eventResponseWithStatus(Status.INTERNAL_SERVER_ERROR, "")
        when(mockTokenCache.token).thenReturn(Failure(new RuntimeException))
        when(
          mockRequestBuilderPost
            .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])
        ).thenReturn(Future.successful(authResponse))

        when(
          mockRequestBuilderGet
            .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])
        ).thenReturn(Future.successful(eventResponse))

        val result = testConnector.getReference(refNumber).futureValue

        result                                        shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[UpstreamErrorResponse]
      }

      "BirthErrorResponse 5xx when gro throws" in {

        val testConnector: GROEnglandAndWalesConnector =
          new GROEnglandAndWalesConnector(
            testGroConfig,
            mockHttpClient,
            mockAuthenticator
          ) {
            override val http: HttpClientV2               = mockHttpClient
            override val responseHandler: ResponseHandler = mock[ResponseHandler]
          }

        implicit val metrics: BRMMetrics = new BRMMetrics
        val authResponse                 = authSuccessResponse(authRecord)
        val eventResponse                = eventResponseWithStatus(Status.INTERNAL_SERVER_ERROR, "")
        when(mockTokenCache.token).thenReturn(Failure(new RuntimeException))
        when(
          mockRequestBuilderPost
            .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])
        ).thenReturn(Future.successful(authResponse))

        when(
          mockRequestBuilderGet
            .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])
        ).thenReturn(Future.successful(eventResponse))

        when(
          testConnector.responseHandler.handle(any[Future[HttpResponse]])(any(), any[BRMMetrics])(any[ExecutionContext])
        )
          .thenReturn(Future(BirthErrorResponse(new GatewayTimeoutException("some exception"))))

        val result = testConnector.getReference(refNumber).futureValue

        result                                        shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[UpstreamErrorResponse]
      }
    }

    "get details" should {

      "BirthSuccessResponse when gro details responds with 200 with single record." in {
        implicit val metrics: BRMMetrics = new BRMMetrics

        val firstName   = "adam"
        val lastName    = "smith"
        val dateOfBirth = "2016-10-10"

        val authResponse  = authSuccessResponse(authRecord)
        val eventResponse = eventSuccessResponse(groResponse("2006-11-12_smith_adam"))

        val argumentCapture: ArgumentCaptor[URL] = ArgumentCaptor.forClass(classOf[URL])

        when(mockTokenCache.token).thenReturn(Success("token"))
        when(
          mockRequestBuilderPost
            .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])
        ).thenReturn(Future.successful(authResponse))

        when(
          mockRequestBuilderGet
            .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])
        ).thenReturn(Future.successful(eventResponse))

        val result = testConnector.getDetails(firstName, lastName, dateOfBirth).futureValue

        verify(mockHttpClient).get(argumentCapture.capture())(any())

        result                                                                               shouldBe a[BirthSuccessResponse[_]]
        result                                                                               shouldBe BirthSuccessResponse(groResponse("2006-11-12_smith_adam"))
        result.asInstanceOf[BirthSuccessResponse[JsArray]].json.value.size                   shouldBe 2
        metrics.defaultRegistry.counter(s"${metrics.prefix}-details-request-count").getCount shouldBe 1
        argumentCapture.getValue.toString                                                    shouldBe getEntireUrl(path, firstName, lastName, dateOfBirth)
      }

      "BirthSuccessResponse when gro details responds with 200 with single record when request has special character." in {
        implicit val metrics: BRMMetrics = new BRMMetrics

        val firstName   = "Adàm TËST"
        val lastName    = "SMÏTH"
        val dateOfBirth = "2006-11-12"

        val authResponse  = authSuccessResponse(authRecord)
        val eventResponse = eventSuccessResponse(groResponse("2006-11-12_smith_adam-utf-8"))

        val argumentCapture: ArgumentCaptor[URL] = ArgumentCaptor.forClass(classOf[URL])

        when(mockTokenCache.token).thenReturn(Success("token"))
        when(
          mockRequestBuilderPost
            .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])
        ).thenReturn(Future.successful(authResponse))

        when(
          mockRequestBuilderGet
            .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])
        ).thenReturn(Future.successful(eventResponse))

        val result = testConnector.getDetails(firstName, lastName, dateOfBirth).futureValue
        verify(mockHttpClient).get(argumentCapture.capture())(any())
        result                                                             shouldBe a[BirthSuccessResponse[_]]
        result                                                             shouldBe BirthSuccessResponse(groResponse("2006-11-12_smith_adam-utf-8"))
        result.asInstanceOf[BirthSuccessResponse[JsArray]].json.value.size shouldBe 2
        argumentCapture.getValue.toString                                  shouldBe getEntireUrl(path, firstName, lastName, dateOfBirth)

      }

      "BirthSuccessResponse with [] empty response for no records found" in {
        implicit val metrics: BRMMetrics = new BRMMetrics

        val firstName   = "adam"
        val lastName    = "smith"
        val dateOfBirth = "2016-10-10"

        val authResponse  = authSuccessResponse(authRecord)
        val eventResponse = eventSuccessResponse(groResponse("NoMatch"))

        val argumentCapture: ArgumentCaptor[URL] = ArgumentCaptor.forClass(classOf[URL])
        when(mockTokenCache.token).thenReturn(Success("token"))
        when(
          mockRequestBuilderPost
            .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])
        ).thenReturn(Future.successful(authResponse))

        when(
          mockRequestBuilderGet
            .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])
        ).thenReturn(Future.successful(eventResponse))

        val result = testConnector.getDetails(firstName, lastName, dateOfBirth).futureValue

        verify(mockHttpClient).get(argumentCapture.capture())(any())
        result                                                             shouldBe a[BirthSuccessResponse[_]]
        result                                                             shouldBe BirthSuccessResponse(groResponse("NoMatch"))
        result.asInstanceOf[BirthSuccessResponse[JsArray]].json.value.size shouldBe 0
        argumentCapture.getValue.toString                                  shouldBe getEntireUrl(path, firstName, lastName, dateOfBirth)
      }

      "BirthErrorResponse 4xx with BadRequest for missing forenames parameter" in {
        implicit val metrics: BRMMetrics = new BRMMetrics

        val firstName   = ""
        val lastName    = "smith"
        val dateOfBirth = "2016-10-10"

        val authResponse = authSuccessResponse(authRecord)

        val eventResponse                        = HttpResponse.apply(Status.BAD_REQUEST, "forenames or forename1 is required")
        val argumentCapture: ArgumentCaptor[URL] = ArgumentCaptor.forClass(classOf[URL])
        when(mockTokenCache.token).thenReturn(Success("token"))
        when(
          mockRequestBuilderPost
            .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])
        ).thenReturn(Future.successful(authResponse))

        when(
          mockRequestBuilderGet
            .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])
        ).thenReturn(Future.successful(eventResponse))

        val result = testConnector.getDetails(firstName, lastName, dateOfBirth).futureValue
        verify(mockHttpClient).get(argumentCapture.capture())(any())
        result                                        shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[UpstreamErrorResponse]
        argumentCapture.getValue.toString             shouldBe getEntireUrl(path, firstName, lastName, dateOfBirth)
      }

      "BirthErrorResponse 4xx with BadRequest for missing lastname parameter" in {
        implicit val metrics: BRMMetrics = new BRMMetrics

        val firstName   = "adam"
        val lastName    = ""
        val dateOfBirth = "2016-10-10"

        val authResponse = authSuccessResponse(authRecord)

        val eventResponse                        = HttpResponse.apply(Status.BAD_REQUEST, "Must provide lastname parameter")
        val argumentCapture: ArgumentCaptor[URL] = ArgumentCaptor.forClass(classOf[URL])
        when(mockTokenCache.token).thenReturn(Success("token"))
        when(
          mockRequestBuilderPost
            .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])
        ).thenReturn(Future.successful(authResponse))

        when(
          mockRequestBuilderGet
            .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])
        ).thenReturn(Future.successful(eventResponse))

        val result = testConnector.getDetails(firstName, lastName, dateOfBirth).futureValue

        verify(mockHttpClient).get(argumentCapture.capture())(any())

        result                                        shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[UpstreamErrorResponse]
        argumentCapture.getValue.toString             shouldBe getEntireUrl(path, firstName, lastName, dateOfBirth)
      }

      "BirthErrorResponse 4xx with BadRequest for missing dateofbirth parameter" in {
        implicit val metrics: BRMMetrics = new BRMMetrics

        val firstName   = "adam"
        val lastName    = "smith"
        val dateOfBirth = ""

        val authResponse                         = authSuccessResponse(authRecord)
        val eventResponse                        =
          Future.successful(HttpResponse.apply(Status.BAD_REQUEST, "Must provide date of birth parameter"))
        val argumentCapture: ArgumentCaptor[URL] = ArgumentCaptor.forClass(classOf[URL])

        when(mockTokenCache.token).thenReturn(Success("token"))

        when(
          mockRequestBuilderPost
            .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])
        ).thenReturn(Future.successful(authResponse))

        when(
          mockRequestBuilderGet
            .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])
        ).thenReturn(eventResponse)

        val result = testConnector.getDetails(firstName, lastName, dateOfBirth).futureValue
        verify(mockHttpClient).get(argumentCapture.capture())(any())
        result                                        shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[UpstreamErrorResponse]
        argumentCapture.getValue.toString             shouldBe getEntireUrl(path, firstName, lastName, dateOfBirth)
      }

      "BirthErrorResponse when GRO returns 5xx" in {
        implicit val metrics: BRMMetrics = new BRMMetrics

        val firstName   = "adam"
        val lastName    = "smith"
        val dateOfBirth = "2010-10-06"

        val authResponse  = authSuccessResponse(authRecord)
        val eventResponse = eventResponseWithStatus(Status.INTERNAL_SERVER_ERROR, "")

        when(mockTokenCache.token).thenReturn(Failure(new RuntimeException))
        when(
          mockRequestBuilderPost
            .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])
        ).thenReturn(Future.successful(authResponse))

        when(
          mockRequestBuilderGet
            .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])
        ).thenReturn(Future.successful(eventResponse))

        val result = testConnector.getDetails(firstName, lastName, dateOfBirth).futureValue

        verify(mockRequestBuilderGet, times(1))
          .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])

        result                                        shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[UpstreamErrorResponse]
      }

      "return a BirthErrorResponse when token has expired and unable to obtain a new token" in {
        val testConnector: GROEnglandAndWalesConnector =
          new GROEnglandAndWalesConnector(
            testGroConfig,
            mockHttpClient,
            mockAuthenticator
          ) {
            override val http: HttpClientV2               = mockHttpClient
            override val responseHandler: ResponseHandler = mock[ResponseHandler]
          }

        when(testConnector.responseHandler.handle(any())(any(), any())(any()))
          .thenReturn(
            Future.successful(BirthErrorResponse(UpstreamErrorResponse("message", Status.INTERNAL_SERVER_ERROR)))
          )

        implicit val metrics: BRMMetrics = mock[BRMMetrics]

        val firstName   = "adam"
        val lastName    = "smith"
        val dateOfBirth = "2010-10-06"

        val authResponse = HttpResponse.apply(Status.INTERNAL_SERVER_ERROR, "")

        when(testConnector.authenticator.tokenCache.token).thenReturn(Failure(new RuntimeException))
        when(
          mockRequestBuilderPost
            .execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext])
        ).thenReturn(Future.successful(authResponse))

        val result = testConnector.getDetails(firstName, lastName, dateOfBirth).futureValue

        result                                        shouldBe a[BirthErrorResponse]
        result.asInstanceOf[BirthErrorResponse].cause shouldBe a[UpstreamErrorResponse]
      }

    }
  }
}
