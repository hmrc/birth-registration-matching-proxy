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

import uk.gov.hmrc.brm.config.GroAppConfig
import uk.gov.hmrc.brm.metrics.BRMMetrics
import uk.gov.hmrc.brm.utils.BrmLogger._
import uk.gov.hmrc.brm.utils.{AccessTokenRepository, CertificateStatus, TimeProvider}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{BadGatewayException, GatewayTimeoutException, HeaderCarrier, HttpReads, HttpResponse, StringContextOps}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class Authenticator @Inject() (
  groConfig: GroAppConfig,
  certificateStatus: CertificateStatus,
  val http: HttpClientV2,
  val timeProvider: TimeProvider
) {

  val username: String                  = groConfig.groUsername
  val password: String                  = groConfig.groPassword
  val clientID: String                  = groConfig.groClientID
  val clientSecret: String              = groConfig.groClientSecret
  val grantType: String                 = groConfig.groGrantType
  val endpoint: String                  = groConfig.authenticationServiceUrl + groConfig.authenticationUri
  val tokenCache: AccessTokenRepository = new AccessTokenRepository(timeProvider)
  val responseHandler: ResponseHandler  = new ResponseHandler
  val errorHandler: ErrorHandler        = new ErrorHandler

  private val CLASS_NAME: String = this.getClass.getSimpleName

  private def authenticate()(implicit
    hc: HeaderCarrier,
    metrics: BRMMetrics,
    ec: ExecutionContext
  ): Future[BirthResponse] = {
    val credentials: Map[String, String] = Map(
      "username"      -> username,
      "password"      -> password,
      "client_id"     -> clientID,
      "client_secret" -> clientSecret,
      "grant_type"    -> grantType
    )

    val newHc = hc.withExtraHeaders(("Content-Type" -> "application/x-www-form-urlencoded; charset=utf-8"))
    info(CLASS_NAME, "authenticate", s"requesting authentication token $endpoint")

    metrics.requestCount("authentication")

    val startTime = metrics.startTimer()

    val response: Future[HttpResponse] = http
      .post(url"$endpoint")(newHc)
      .withProxy
      .withBody(credentials.map(cred => cred._1 -> Seq(cred._2)))
      .execute[HttpResponse](HttpReads.Implicits.readRaw, ec)

    metrics.endTimer(startTime, "authentication-timer")

    responseHandler.handle(response)(saveAccessToken, metrics)
  }

  private lazy val saveAccessToken: PartialFunction[HttpResponse, BirthResponse] = { case response: HttpResponse =>
    info(CLASS_NAME, "saveAccessToken", "parsing response from authentication")
    ResponseParser.parse(response) match {
      case BirthSuccessResponse(body) =>
        val token   = body.\("access_token").as[String]
        val seconds = body.\("expires_in").as[Int]

        tokenCache.saveToken(token, tokenCache.newExpiry(seconds))

        BirthAccessTokenResponse(token)
      case e @ BirthErrorResponse(_)  =>
        debug(CLASS_NAME, "saveAccessToken", s"Response from home office: ${response.toString()}")
        warn(CLASS_NAME, "saveAccessToken", "failed to parse response")
        e
    }
  }

  private def requestNewToken()(implicit
    hc: HeaderCarrier,
    metrics: BRMMetrics,
    ec: ExecutionContext
  ): Future[BirthResponse] =
    authenticate().map {
      case BirthErrorResponse(exception) =>
        exception match {
          case e: GatewayTimeoutException =>
            error(CLASS_NAME, "requestNewToken", "gateway timeout exception, stop obtaining a new token")
            errorHandler.error(e.getMessage)
          case e: BadGatewayException     =>
            error(CLASS_NAME, "requestNewToken", "bad gateway exception, stop obtaining a new token")
            errorHandler.error(e.getMessage)
          case e: Exception               =>
            error(CLASS_NAME, "requestNewToken", s"unable to obtain new access token: unexpected exception: $e")
            errorHandler.error(e.getMessage)
        }
      case authenticated                 =>
        authenticated
    }

  def token()(implicit hc: HeaderCarrier, metrics: BRMMetrics, ec: ExecutionContext): Future[BirthResponse] =
    // $COVERAGE-OFF$
    if (groConfig.tlsEnabled && !certificateStatus.certificateStatus()) {
      error(CLASS_NAME, "token", "TLS Certificate expired")
      Future.successful(errorHandler.error("TLS Certificate expired"))
    } else {
      // $COVERAGE-ON$
      tokenCache.token match {
        case Success(cache)   =>
          debug(CLASS_NAME, "token", s"cached access_token: $cache")
          Future.successful(BirthAccessTokenResponse(cache))
        case Failure(expired) =>
          info(CLASS_NAME, "token", s"access_token has expired $expired")
          requestNewToken()
      }
    }

}
