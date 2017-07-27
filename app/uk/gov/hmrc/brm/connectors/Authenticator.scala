/*
 * Copyright 2017 HM Revenue & Customs
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

import uk.co.bigbeeconsultants.http.header.MediaType
import uk.co.bigbeeconsultants.http.request.RequestBody
import uk.co.bigbeeconsultants.http.response.Response
import uk.co.bigbeeconsultants.http.{HttpClient, _}
import uk.gov.hmrc.brm.config.GROConnectorConfiguration
import uk.gov.hmrc.brm.connectors.ConnectorTypes.{Attempts, DelayAttempts, DelayTime}
import uk.gov.hmrc.brm.metrics.BRMMetrics
import uk.gov.hmrc.brm.tls.HttpClientFactory
import uk.gov.hmrc.brm.utils.BrmLogger._
import uk.gov.hmrc.brm.utils.{AccessTokenRepository, CertificateStatus}

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

class Authenticator(username : String,
                    password : String,
                    clientID: String,
                    clientSecret: String,
                    grantType: String,
                    endpoint : String,
                    val http: HttpClient,
                    val tokenCache : AccessTokenRepository,
                    delayTime : DelayTime,
                    delayAttempts : DelayAttempts,
                    mediaType: MediaType = MediaType.apply("application", "x-www-form-urlencoded").withCharset("ISO-8859-1")) {

  private[Authenticator] val CLASS_NAME : String = this.getClass.getCanonicalName

  private[Authenticator] def authenticate(attempts : Attempts)(implicit metrics : BRMMetrics) : (BirthResponse, Attempts) = {
    val credentials: Map[String, String] = Map(
      "username" -> username,
      "password" -> password,
      "client_id" -> clientID,
      "client_secret" -> clientSecret,
      "grant_type" -> grantType
    )

    debug(CLASS_NAME, "authenticate", s"$endpoint credentials: $credentials")
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

  private[Authenticator] val saveAccessToken: PartialFunction[Response, BirthResponse] = {
    case response: Response =>
      info(CLASS_NAME, "saveAccessToken", "parsing response from authentication")
      ResponseParser.parse(response) match {
        case BirthSuccessResponse(body) =>

          val token = body.\("access_token").as[String]
          val seconds = body.\("expires_in").as[Int]

          tokenCache.saveToken(token, tokenCache.newExpiry(seconds))

          BirthAccessTokenResponse(token)
        case e @ BirthErrorResponse(error) =>
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
              } else { ErrorHandler.error(exception.getMessage) }
            case e : Exception =>
              ErrorHandler.error(exception.getMessage)
          }
      }
    }

    authHelper(1)
  }


  def token()(implicit metrics : BRMMetrics) : BirthResponse = {
    if(!CertificateStatus.certificateStatus()) {
      error(CLASS_NAME, "token", "TLS Certificate expired")
      ErrorHandler.error("TLS Certificate expired")
    } else {
      tokenCache.token match {
        case Success(cache) =>
          info(CLASS_NAME, "token", s"access_token has not expired")
          debug(CLASS_NAME, "token", s"cached access_token: $cache")
          BirthAccessTokenResponse(cache)
        case Failure(expired) =>
          info(CLASS_NAME, "token", s"access_token has expired $expired")
          requestNewToken()
      }
    }

  }

}

/**
 * Authenticator factory
 */
object Authenticator {

  def apply() : Authenticator = {

    val httpClient = HttpClientFactory.apply()
    val username = GROConnectorConfiguration.username
    val password = GROConnectorConfiguration.password
    val clientID = GROConnectorConfiguration.clientID
    val clientSecret = GROConnectorConfiguration.clientSecret
    val grantType = GROConnectorConfiguration.grantType
    val endpoint = GROConnectorConfiguration.authenticationServiceUrl + GROConnectorConfiguration.authenticationUri
    val tokenRepo = new AccessTokenRepository
    val delayTime = GROConnectorConfiguration.delayAttemptInMilliseconds
    val delayAttempts = GROConnectorConfiguration.delayAttempts

    new Authenticator(
      username,
      password,
      clientID,
      clientSecret,
      grantType,
      endpoint,
      httpClient,
      tokenRepo,
      delayTime,
      delayAttempts
    )
  }

}
