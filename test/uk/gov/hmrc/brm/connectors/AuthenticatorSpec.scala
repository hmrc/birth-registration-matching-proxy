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

import org.mockito.ArgumentMatchers.{any, anyInt, anyString}
import org.mockito.Mockito.*
import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.brm.TestFixture
import uk.gov.hmrc.brm.certificate.CertificateStatus
import uk.gov.hmrc.brm.config.GroAppConfig
import uk.gov.hmrc.brm.metrics.BRMMetrics
import uk.gov.hmrc.brm.time.TimeProvider
import uk.gov.hmrc.brm.utils.AccessTokenRepository
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{
  BadGatewayException, GatewayTimeoutException, HeaderCarrier, HttpResponse, UpstreamErrorResponse
}

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit.SECONDS
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class AuthenticatorSpec extends TestFixture with ScalaFutures {

  val mockHttpClient: HttpClientV2       = mock(classOf[HttpClientV2])
  val mockRequestBuilder: RequestBuilder = mock(classOf[RequestBuilder])

  val testAuthenticator =
    new Authenticator(testGroConfig, mock(classOf[CertificateStatus]), mockHttpClient, new TimeProvider())

  val mockResponseHandler: ResponseHandler = mock(classOf[ResponseHandler])
  val mockErrorHandler: ErrorHandler       = mock(classOf[ErrorHandler])
  given hc: HeaderCarrier                  = HeaderCarrier()
  given metrics: BRMMetrics                = mock(classOf[BRMMetrics])

  val testAuthenticatorMockResponseHandler: Authenticator =
    new Authenticator(testGroConfig, mock(classOf[CertificateStatus]), mockHttpClient, new TimeProvider()) {
      override val responseHandler: ResponseHandler  = mockResponseHandler
      override val errorHandler: ErrorHandler        = mockErrorHandler
      override val tokenCache: AccessTokenRepository = mock(classOf[AccessTokenRepository])
    }

  when(mockHttpClient.post(any())(any())).thenReturn(mockRequestBuilder)
  when(mockRequestBuilder.withBody(any[JsObject])(any(), any(), any())).thenReturn(mockRequestBuilder)
  when(mockRequestBuilder.withProxy).thenReturn(mockRequestBuilder)

  when(mockRequestBuilder.execute[HttpResponse](any(), any()))
    .thenReturn(Future.successful(HttpResponse.apply(Status.OK, "a response")))

  doNothing().when(metrics).requestCount(any())
  when(metrics.startTimer()).thenReturn(1L)
  doNothing().when(metrics).endTimer(any(), any())

  "Authenticator" when {

    "creating an instance of cache" should {

      "return false for having a token" in {
        testAuthenticator.tokenCache.hasToken   shouldBe false
        testAuthenticator.tokenCache.hasExpired shouldBe true
      }

    }

    "saving a new token" should {

      "insert a token" in {
        testAuthenticator.tokenCache.saveToken("new token", ZonedDateTime.now.plusDays(2))
        testAuthenticator.tokenCache.hasToken   shouldBe true
        testAuthenticator.tokenCache.hasExpired shouldBe false
        testAuthenticator.tokenCache.token      shouldBe Success("new token")
      }

      "generate new expiry" in {
        val mockTimeProvider = mock(classOf[TimeProvider])

        val testAuthenticator =
          new Authenticator(testGroConfig, mock(classOf[CertificateStatus]), mockHttpClient, mockTimeProvider)

        val dateTime = ZonedDateTime.now()

        when(mockTimeProvider.now) thenReturn dateTime

        val expiryTime = testAuthenticator.tokenCache.newExpiry(100)
        // expiry time shd be less by 60 sec.
        SECONDS.between(dateTime, expiryTime) shouldBe 40
      }

      "fail if a GatewayTimeoutException is returned by the post" in {

        val toReturn: BirthErrorResponse = BirthErrorResponse(new GatewayTimeoutException("gateway timeout returned"))

        when(mockResponseHandler.handle(any[Future[HttpResponse]])(any(), any[BRMMetrics])(using any[ExecutionContext]))
          .thenReturn(Future.successful(BirthErrorResponse(new GatewayTimeoutException("gateway timeout message"))))
        when(testAuthenticatorMockResponseHandler.tokenCache.token)
          .thenReturn(Failure(new Exception("exception message")))
        when(mockErrorHandler.error(anyString(), anyInt())).thenReturn(toReturn)

        testAuthenticatorMockResponseHandler.token().map(birthResponse => birthResponse shouldBe toReturn)

      }

      "fail if a BadGatewayException is returned by the post" in {

        val toReturn: BirthErrorResponse = BirthErrorResponse(new BadGatewayException("bad gateway returned"))

        when(mockResponseHandler.handle(any[Future[HttpResponse]])(any(), any[BRMMetrics])(using any[ExecutionContext]))
          .thenReturn(Future.successful(BirthErrorResponse(new BadGatewayException("bad gateway message"))))
        when(testAuthenticatorMockResponseHandler.tokenCache.token)
          .thenReturn(Failure(new Exception("exception message")))
        when(mockErrorHandler.error(anyString(), anyInt())).thenReturn(toReturn)

        testAuthenticatorMockResponseHandler.token().map(birthResponse => birthResponse shouldBe toReturn)

      }

      "fail if an unexpected exception is returned by the post" in {

        val toReturn: BirthErrorResponse = BirthErrorResponse(new Exception("unknown exception returned"))

        when(mockResponseHandler.handle(any[Future[HttpResponse]])(any(), any[BRMMetrics])(using any[ExecutionContext]))
          .thenReturn(Future.successful(BirthErrorResponse(new Exception("unknown exception message"))))
        when(testAuthenticatorMockResponseHandler.tokenCache.token)
          .thenReturn(Failure(new Exception("exception message")))
        when(mockErrorHandler.error(anyString(), anyInt())).thenReturn(toReturn)

        testAuthenticatorMockResponseHandler.token().map(birthResponse => birthResponse shouldBe toReturn)

      }

    }

    "handling the authentication response" should {

      def authenticatorWithRealResponseHandler(): Authenticator =
        new Authenticator(testGroConfig, mock(classOf[CertificateStatus]), mockHttpClient, new TimeProvider())

      "parse the access token from a successful response and cache it" in {
        val authenticator = authenticatorWithRealResponseHandler()

        val authRecord = Json.obj("access_token" -> "a-new-access-token", "expires_in" -> 300)

        when(mockRequestBuilder.execute[HttpResponse](any(), any()))
          .thenReturn(Future.successful(HttpResponse.apply(Status.OK, authRecord.toString())))

        authenticator.token().futureValue shouldBe BirthAccessTokenResponse("a-new-access-token")

        authenticator.tokenCache.hasToken   shouldBe true
        authenticator.tokenCache.hasExpired shouldBe false
        authenticator.tokenCache.token      shouldBe Success("a-new-access-token")
      }

      "return the parse failure when a successful response does not contain valid json" in {
        val authenticator = authenticatorWithRealResponseHandler()

        when(mockRequestBuilder.execute[HttpResponse](any(), any()))
          .thenReturn(Future.successful(HttpResponse.apply(Status.OK, "this is not json")))

        val result = authenticator.token().futureValue

        result shouldBe a[BirthErrorResponse]

        authenticator.tokenCache.hasToken shouldBe false
      }

    }

    "checking the TLS certificate" should {

      def authenticatorWith(tlsEnabled: Boolean, certificateValid: Boolean, http: HttpClientV2): Authenticator = {
        val config = mock(classOf[GroAppConfig])
        when(config.tlsEnabled).thenReturn(tlsEnabled)
        when(config.authenticationServiceUrl).thenReturn("http://localhost:8099")
        when(config.authenticationUri).thenReturn("/auth/token")

        val certificateStatus = mock(classOf[CertificateStatus])
        when(certificateStatus.certificateStatus()).thenReturn(certificateValid)

        new Authenticator(config, certificateStatus, http, new TimeProvider())
      }

      "refuse to authenticate when TLS is enabled and the certificate has expired" in {
        val unusedHttpClient = mock(classOf[HttpClientV2])

        val result =
          authenticatorWith(tlsEnabled = true, certificateValid = false, unusedHttpClient).token().futureValue

        result shouldBe a[BirthErrorResponse]

        val cause = result.asInstanceOf[BirthErrorResponse].cause
        cause          shouldBe a[UpstreamErrorResponse]
        cause.getMessage should include("TLS Certificate expired")

        verify(unusedHttpClient, never()).post(any())(any())
      }

      "request a token when TLS is enabled and the certificate is still valid" in {
        val validCertHttpClient = mock(classOf[HttpClientV2])
        when(validCertHttpClient.post(any())(any())).thenReturn(mockRequestBuilder)

        authenticatorWith(tlsEnabled = true, certificateValid = true, validCertHttpClient).token().futureValue

        verify(validCertHttpClient).post(any())(any())
      }

    }

  }

}
