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

import uk.co.bigbeeconsultants.http.header.Headers
import uk.co.bigbeeconsultants.http.request.RequestBody
import uk.co.bigbeeconsultants.http.response.Response
import uk.co.bigbeeconsultants.http.{HttpClient, _}
import uk.gov.hmrc.brm.config.GROConnectorConfiguration
import uk.gov.hmrc.brm.connectors.ConnectorTypes.{Attempts, DelayAttempts, DelayTime}
import uk.gov.hmrc.brm.metrics.{GroMetrics, Metrics}
import uk.gov.hmrc.brm.tls.{HttpClientFactory, TLSFactory}
import uk.gov.hmrc.brm.utils.BrmLogger._
import uk.gov.hmrc.brm.utils.{AccessTokenRepository, CertificateStatus}

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

/**
 * Created by adamconder on 14/11/2016.
 */

class Authenticator(username : String,
                    password : String,
                    endpoint : String,
                    val http: HttpClient,
                    val tokenCache : AccessTokenRepository,
                    metrics : Metrics,
                    delayTime : DelayTime,
                    delayAttempts : DelayAttempts) {

  private[Authenticator] val CLASS_NAME : String = this.getClass.getCanonicalName

  private[Authenticator] def authenticate(attempts : Attempts) : (BirthResponse, Attempts) = {
    val credentials: Map[String, String] = Map(
      "username" -> username,
      "password" -> password
    )

    debug(CLASS_NAME, "requestAuth", s"$endpoint credentials: $credentials")
    info(CLASS_NAME, "requestAuth", s"requesting authentication token $endpoint")

    metrics.requestCount("authentication")

    val startTime = metrics.startTimer()
    // request new access token
    val response = http.post(
      url = endpoint,
      body = Some(RequestBody.apply(credentials)),
      requestHeaders = Headers.apply(
        Map("Content-Type" -> "application/x-www-form-urlencoded")
      )
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

          // save the new token
          tokenCache.saveToken(token, tokenCache.newExpiry(seconds))

          BirthAccessTokenResponse(token)
        case e @ BirthErrorResponse(error) =>
          e
      }
  }

  private def requestNewToken() = {
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


  def token : BirthResponse = {
    if(!CertificateStatus.certificateStatus()) {
      // return an BirthErrorResponse as TLS certificate has expired
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
          //get new auth token
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
    val endpoint = s"${GROConnectorConfiguration.serviceUrl}/oauth/login"
    val tokenRepo = new AccessTokenRepository
    val metrics = GroMetrics
    val delayTime = GROConnectorConfiguration.delayAttemptInMilliseconds
    val delayAttempts = GROConnectorConfiguration.delayAttempts

    new Authenticator(
      username,
      password,
      endpoint,
      httpClient,
      tokenRepo,
      metrics,
      delayTime,
      delayAttempts
    )
  }

}
