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

import org.mockito.ArgumentMatchers.{any, anyInt, anyString}
import org.mockito.Mockito._
import play.api.http.Status
import uk.gov.hmrc.brm.TestFixture
import uk.gov.hmrc.brm.metrics.BRMMetrics
import uk.gov.hmrc.brm.utils.{AccessTokenRepository, CertificateStatus, TimeProvider}
import uk.gov.hmrc.http.{BadGatewayException, GatewayTimeoutException, HeaderCarrier, HttpReads, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit.SECONDS
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class AuthenticatorSpec extends TestFixture {

  val mockHttpClient: DefaultHttpClient = mock[DefaultHttpClient]

  val testAuthenticator =
    new Authenticator(testGroConfig, mock[CertificateStatus], mockHttpClient, new TimeProvider())

  val mockResponseHandler: ResponseHandler = mock[ResponseHandler]
  val mockErrorHandler: ErrorHandler       = mock[ErrorHandler]
  implicit val hc: HeaderCarrier           = HeaderCarrier()
  implicit val metrics: BRMMetrics         = mock[BRMMetrics]

  val testAuthenticatorMockResponseHandler: Authenticator =
    new Authenticator(testGroConfig, mock[CertificateStatus], mockHttpClient, new TimeProvider()) {
      override val responseHandler: ResponseHandler  = mockResponseHandler
      override val errorHandler: ErrorHandler        = mockErrorHandler
      override val tokenCache: AccessTokenRepository = mock[AccessTokenRepository]
    }

  when(
    mockHttpClient.POSTForm[HttpResponse](anyString(), any[Map[String, Seq[String]]], any[Seq[(String, String)]])(
      any[HttpReads[HttpResponse]],
      any[HeaderCarrier],
      any[ExecutionContext]
    )
  ).thenReturn(Future.successful(HttpResponse.apply(Status.OK, "a response")))
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
        val mockTimeProvider = mock[TimeProvider]

        val testAuthenticator =
          new Authenticator(testGroConfig, mock[CertificateStatus], mockHttpClient, mockTimeProvider)

        val dateTime = ZonedDateTime.now()

        when(mockTimeProvider.now) thenReturn dateTime

        val expiryTime = testAuthenticator.tokenCache.newExpiry(100)
        //expiry time shd be less by 60 sec.
        SECONDS.between(dateTime, expiryTime) shouldBe 40
      }

      "fail if a GatewayTimeoutException is returned by the post" in {

        val toReturn: BirthErrorResponse = BirthErrorResponse(new GatewayTimeoutException("gateway timeout returned"))

        when(mockResponseHandler.handle(any[Future[HttpResponse]])(any(), any[BRMMetrics])(any[ExecutionContext]))
          .thenReturn(Future.successful(BirthErrorResponse(new GatewayTimeoutException("gateway timeout message"))))
        when(testAuthenticatorMockResponseHandler.tokenCache.token)
          .thenReturn(Failure(new Exception("exception message")))
        when(mockErrorHandler.error(anyString(), anyInt())).thenReturn(toReturn)

        testAuthenticatorMockResponseHandler.token().map(birthResponse => birthResponse shouldBe toReturn)

      }

      "fail if a BadGatewayException is returned by the post" in {

        val toReturn: BirthErrorResponse = BirthErrorResponse(new BadGatewayException("bad gateway returned"))

        when(mockResponseHandler.handle(any[Future[HttpResponse]])(any(), any[BRMMetrics])(any[ExecutionContext]))
          .thenReturn(Future.successful(BirthErrorResponse(new BadGatewayException("bad gateway message"))))
        when(testAuthenticatorMockResponseHandler.tokenCache.token)
          .thenReturn(Failure(new Exception("exception message")))
        when(mockErrorHandler.error(anyString(), anyInt())).thenReturn(toReturn)

        testAuthenticatorMockResponseHandler.token().map(birthResponse => birthResponse shouldBe toReturn)

      }

      "fail if an unexpected exception is returned by the post" in {

        val toReturn: BirthErrorResponse = BirthErrorResponse(new Exception("unknown exception returned"))

        when(mockResponseHandler.handle(any[Future[HttpResponse]])(any(), any[BRMMetrics])(any[ExecutionContext]))
          .thenReturn(Future.successful(BirthErrorResponse(new Exception("unknown exception message"))))
        when(testAuthenticatorMockResponseHandler.tokenCache.token)
          .thenReturn(Failure(new Exception("exception message")))
        when(mockErrorHandler.error(anyString(), anyInt())).thenReturn(toReturn)

        testAuthenticatorMockResponseHandler.token().map(birthResponse => birthResponse shouldBe toReturn)

      }

    }

  }

}
