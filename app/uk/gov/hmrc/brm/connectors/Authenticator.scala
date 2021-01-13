/*
 * Copyright 2021 HM Revenue & Customs
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

import java.net.SocketTimeoutException

import javax.inject.Inject
import uk.co.bigbeeconsultants.http.header.MediaType
import uk.co.bigbeeconsultants.http.request.RequestBody
import uk.co.bigbeeconsultants.http.response.Response
import uk.co.bigbeeconsultants.http.{HttpClient, _}
import uk.gov.hmrc.brm.config.{GroAppConfig, ProxyAppConfig}
import uk.gov.hmrc.brm.connectors.ConnectorTypes.{Attempts, DelayAttempts, DelayTime}
import uk.gov.hmrc.brm.metrics.BRMMetrics
import uk.gov.hmrc.brm.tls.HttpClientFactory
import uk.gov.hmrc.brm.utils.BrmLogger._
import uk.gov.hmrc.brm.utils.{AccessTokenRepository, CertificateStatus}

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}


class Authenticator @Inject()(proxyConfig: ProxyAppConfig,
                              groConfig: GroAppConfig,
                              httpClientFactory: HttpClientFactory,
                              proxyAuthenticator: ProxyAuthenticator,
                              certificateStatus: CertificateStatus) {

  val username : String = groConfig.groUsername
  val password : String = groConfig.groPassword
  val clientID: String = groConfig.groClientID
  val clientSecret: String = groConfig.groClientSecret
  val grantType: String = groConfig.groGrantType
  val endpoint : String = groConfig.authenticationServiceUrl + groConfig.authenticationUri
  val http: HttpClient = httpClientFactory.apply()
  val tokenCache : AccessTokenRepository = new AccessTokenRepository
  val delayTime : DelayTime = groConfig.delayAttemptInMilliseconds
  val delayAttempts : DelayAttempts = groConfig.delayAttempts
  val mediaType: MediaType = MediaType.apply("application", "x-www-form-urlencoded").withCharset("ISO-8859-1")

  private val CLASS_NAME : String = this.getClass.getSimpleName

  private def authenticate(attempts : Attempts)(implicit metrics : BRMMetrics) : (BirthResponse, Attempts) = {
    val credentials: Map[String, String] = Map(
      "username" -> username,
      "password" -> password,
      "client_id" -> clientID,
      "client_secret" -> clientSecret,
      "grant_type" -> grantType
    )

    info(CLASS_NAME, "authenticate", s"requesting authentication token $endpoint")

    metrics.requestCount("authentication")

    val startTime = metrics.startTimer()

    val response = http.post(
      url = endpoint,
      body = Some(RequestBody.apply(credentials, mediaType))
    )

    metrics.endTimer(startTime, "authentication-timer")

    ResponseHandler.handle(response, attempts)(saveAccessToken, metrics)
  }

  private lazy val saveAccessToken: PartialFunction[Response, BirthResponse] = {
    case response: Response =>
      info(CLASS_NAME, "saveAccessToken", "parsing response from authentication")
      ResponseParser.parse(response) match {
        case BirthSuccessResponse(body) =>

          val token = body.\("access_token").as[String]
          val seconds = body.\("expires_in").as[Int]

          tokenCache.saveToken(token, tokenCache.newExpiry(seconds))

          BirthAccessTokenResponse(token)
        case e @ BirthErrorResponse(_) =>
          warn(CLASS_NAME, "saveAccessToken", "failed to parse response")
          e
      }
  }

  private def requestNewToken()(implicit metrics : BRMMetrics) = {
    @tailrec
    def authHelper(attempts: Attempts) : BirthResponse = {
      Try(authenticate(attempts)) match {
        case Success((response, _)) => response
        case Failure(exception) =>
          exception match {
            case e : SocketTimeoutException =>
              if (attempts < delayAttempts) {
                ErrorHandler.wait(delayTime)
                authHelper(attempts + 1)
              } else {
                error(CLASS_NAME, "requestNewToken", "socket timeout exception, stop obtaining a new token")
                ErrorHandler.error(e.getMessage)
              }
            case e : Exception =>
              error(CLASS_NAME, "requestNewToken", s"unable to obtain new access token: unexpected exception: $e")
              ErrorHandler.error(e.getMessage)
          }
      }
    }
    authHelper(1)
  }

  def token()(implicit metrics : BRMMetrics) : BirthResponse = {

    // configure authenticator
    proxyAuthenticator.configureProxyAuthenticator()

    val status = certificateStatus

    // $COVERAGE-OFF$
    if(groConfig.tlsEnabled && !status.certificateStatus()) {
      error(CLASS_NAME, "token", "TLS Certificate expired")
      ErrorHandler.error("TLS Certificate expired")
    } else {
      // $COVERAGE-ON$
      tokenCache.token match {
        case Success(cache) =>
          debug(CLASS_NAME, "token", s"cached access_token: $cache")
          BirthAccessTokenResponse(cache)
        case Failure(expired) =>
          info(CLASS_NAME, "token", s"access_token has expired $expired")
          requestNewToken()
      }
    }
  }

}
