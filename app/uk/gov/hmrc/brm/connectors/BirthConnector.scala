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

import java.net.{SocketTimeoutException, URLEncoder}

import play.api.http.Status._
import play.api.libs.json._
import uk.co.bigbeeconsultants.http.header.Headers
import uk.co.bigbeeconsultants.http.request.RequestBody
import uk.co.bigbeeconsultants.http.response.{Response, Status}
import uk.co.bigbeeconsultants.http.{HttpClient, _}
import uk.gov.hmrc.brm.config.GROConnectorConfiguration
import uk.gov.hmrc.brm.connectors.ConnectorTypes.{DelayTime, DelayAttempts, AccessToken, Attempts}
import uk.gov.hmrc.brm.metrics.{GroMetrics, Metrics}
import uk.gov.hmrc.brm.tls.TLSFactory
import uk.gov.hmrc.brm.utils.BrmLogger._
import uk.gov.hmrc.brm.utils.{AccessTokenRepository, CertificateStatus}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.util.{Try, Failure, Success}

sealed trait BirthResponse

case class BirthAccessTokenResponse(token : AccessToken) extends BirthResponse
case class BirthSuccessResponse(json: JsValue) extends BirthResponse
case class BirthErrorResponse(cause: Exception) extends BirthResponse

object ConnectorTypes {

  type Attempts = Int
  type AccessToken = String
  type DelayAttempts = Int
  type DelayTime = Int

}

object ErrorHandler {

  def wait(delay: Int) = {
    val tick = System.currentTimeMillis() + delay
    do {
      debug(super.getClass.getName, "wait()", s"Waiting to execute the next request: ${System.currentTimeMillis()}")
    } while (System.currentTimeMillis() < tick)
  }


  def error(response: Response) = {
    info(super.getClass.getName, "error", s"isServerError: ${response.status.isServerError}")
    val upstream = if (response.status.isServerError) {
      Upstream5xxResponse(
        s"[${super.getClass.getName}][${response.status.toString}]",
        response.status.code,
        response.status.code)
    } else {
      Upstream4xxResponse(
        s"[${super.getClass.getName}][${response.status.toString}]",
        response.status.code,
        response.status.code)
    }

    BirthErrorResponse(upstream)
  }

  def error(message : String) = {
    BirthErrorResponse(
      Upstream5xxResponse(
        s"[${super.getClass.getName}][InternalServerError][$message]",
        INTERNAL_SERVER_ERROR,
        INTERNAL_SERVER_ERROR)
    )
  }

}

object ResponseHandler {

  private val CLASS_NAME : String = this.getClass.getCanonicalName

  def handle(response: Response, attempts : Attempts)(f : Response => BirthResponse, metrics : Metrics) = {
    debug(CLASS_NAME, "handle",s"$response")
    info(CLASS_NAME, "handle", s"response received after $attempts attempt(s)")

    response.status match {
      case Status.S200_OK =>
        metrics.httpResponseCodeStatus(OK)
        info(CLASS_NAME, "handleResponse", s"[authenticate][200] Success, attempt $attempts")
        (f(response), attempts)
      case e @ (Status.S400_BadRequest | Status.S404_NotFound) =>
        metrics.httpResponseCodeStatus(BAD_REQUEST)
        warn(CLASS_NAME, "handleResponse", s"[authenticate][${e.code}}}] ${e.category}: $response, attempt $attempts")
        (ErrorHandler.error(response), attempts)
      case e@_ =>
        metrics.httpResponseCodeStatus(INTERNAL_SERVER_ERROR)
        error(CLASS_NAME, "handleResponse", s"[authenticate][5xx] InternalServerError: $response, attemp $attempts")
        (ErrorHandler.error(response), attempts)
    }
  }

}

object ResponseParser {

  private val CLASS_NAME : String = this.getClass.getCanonicalName

  def parse(response: Response) : BirthResponse = {
    info(CLASS_NAME, "parse", "parsing json")
    debug(CLASS_NAME, "parse", s"${response.body.asString}")

    try {
      val bodyText = response.body.asString
      val json = Json.parse(bodyText)

      BirthSuccessResponse(json)
    } catch {
      case e: Exception =>
        warn(CLASS_NAME, "parse", "unable to parse json")
        // from 200 t0 500 or override somehow?
        val error = response.copy(status = Status.S500_InternalServerError)
        ErrorHandler.error(error)
    }
  }

}

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
    // TODO add logic back in for SocketTimeoutException
    @tailrec
    def authHelper(attempts: Attempts) : BirthResponse = {
      Try(authenticate(attempts)) match {
        case Success((r @ BirthAccessTokenResponse(token), _)) => r
        case Success((r @ BirthErrorResponse(_), loop)) =>
          if (loop < delayAttempts) {
            ErrorHandler.wait(delayTime)
            authHelper(attempts + 1)
          }
          else r
        case Failure(exception : SocketTimeoutException) =>
          if (attempts < delayAttempts) {
            ErrorHandler.wait(delayTime)
            authHelper(attempts + 1)
          }
          else ErrorHandler.error(exception.getMessage)
        case Failure(exception) =>
          ErrorHandler.error(exception.getMessage)
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

        //        try {
        //          val response = authenticate()
        //            handleResponse(response, extractAccessToken, "requestAuth")
        //        } catch {
        //          case e : SocketTimeoutException =>
        //            if (count < delayAttempts) {
        //              val tick = System.currentTimeMillis() + delayTime
        //
        //              do {
        //                debug(CLASS_NAME, "requestReference", s"Waiting to execute the next request: ${System.currentTimeMillis()}")
        //              } while (System.currentTimeMillis() < tick)
        //
        //              info(CLASS_NAME, "requestAuth", s"SocketTimeoutException on attempt: $count, error: ${e.getMessage}")
        //              requestAuth(count + 1)
        //            } else {
        //              warn(CLASS_NAME, "requestAuth", s"SocketTimeoutException on all attempts, error: ${e.getMessage}")
        //              BirthErrorResponse(e)
        //            }
        //          case e : Exception =>
        //            warn(CLASS_NAME, "requestAuth", s"Exception: ${e.getMessage}")
        //            BirthErrorResponse(e)
        //        }

      }
    }

  }

}

/**
 * Authenticator factory
 */

object Authenticator {

  private val config = TLSFactory.getConfig
  private val httpClient = new HttpClient(config)

  def apply() : Authenticator = {
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

/**
 * GROEnglandAndWalesConnector
 */

object GROEnglandAndWalesConnector extends BirthConnector {
  private val config = TLSFactory.getConfig
  override val http = new HttpClient(config)
  override val metrics = GroMetrics
  override val authenticator = Authenticator.apply()
  override val delayTime = GROConnectorConfiguration.delayAttemptInMilliseconds
  override val delayAttempts = GROConnectorConfiguration.delayAttempts
  override val version = GROConnectorConfiguration.version
  override val endpoint = s"${GROConnectorConfiguration.serviceUrl}/api/$version/events/birth"
  override val username = GROConnectorConfiguration.username
}

trait BirthConnector extends ServicesConfig {

  private val CLASS_NAME : String = this.getClass.getCanonicalName

  protected val version: String
  protected val endpoint : String

  protected val username : String

  protected val http: HttpClient
  protected val metrics: Metrics

  val authenticator : Authenticator

  protected val delayTime : Int
  protected val delayAttempts : Int

  protected val extractJson: PartialFunction[Response, BirthResponse] = {
    case response: Response =>
      ResponseParser.parse(response)
  }

  private def GROHeaderCarrier(token: String) = {
    Map(
      "Authorization" -> s"Bearer $token",
      "X-Auth-Downstream-Username" -> username
    )
  }

  private[BirthConnector] def getChildByReference(reference : String, token: AccessToken, attempts : Attempts) : (BirthResponse, Attempts) = {
    val headers = GROHeaderCarrier(token)
    metrics.requestCount("reference-match")

    debug(CLASS_NAME, "getChildByReference", s"$endpoint/$reference headers: $headers")
    info(CLASS_NAME, "getChildByReference", s"requesting child's details $endpoint, attempt $attempts")

    val startTime = metrics.startTimer()
    val response = http.get(s"$endpoint/$reference", Headers.apply(headers))
    metrics.endTimer(startTime, "reference-match-timer")

    ResponseHandler.handle(response, attempts)(extractJson, metrics)
  }

  private[BirthConnector] def getChildByDetails(details: Map[String, String], token : AccessToken, attempts: Attempts) : (BirthResponse, Attempts) = {
    val headers = GROHeaderCarrier(token)
    metrics.requestCount("details-match")

    debug(CLASS_NAME, "getChildByDetails", s"$endpoint/ headers: $headers")
    info(CLASS_NAME, "getChildByDetails", s"requesting child's details $endpoint, attempt $attempts")

    val startTime = metrics.startTimer()
    val query = details.map(pair => pair._1 + "=" + URLEncoder.encode(pair._2, "UTF-8")).mkString("&")
    val url = s"$endpoint/?$query"

    debug(CLASS_NAME, "getChildByDetails", s"query: $url")

    val response = http.get(url, Headers.apply(headers))
    metrics.endTimer(startTime, "details-match-timer")
    ResponseHandler.handle(response, attempts)(extractJson, metrics)
  }

  private def request(reference: String, token: AccessToken) : BirthResponse = {

    // TODO add logic back in for SocketTimeoutException
    // TODO refactor the < 3 to be configuration with maxAttempts
    @tailrec
    def referenceHelper(attempts: Attempts) : BirthResponse = {
      Try(getChildByReference(reference, token, attempts)) match {
        case Success((r @ BirthSuccessResponse(js), _)) => r
        case Success((r @ BirthErrorResponse(_), loop)) =>
          if (loop < delayAttempts) {
            ErrorHandler.wait(delayTime)
            referenceHelper(attempts + 1)
          }
          else r
        case Failure(exception : SocketTimeoutException) =>
          if (attempts < delayAttempts) {
            ErrorHandler.wait(delayTime)
            referenceHelper(attempts + 1)
          }
          else ErrorHandler.error(exception.getMessage)
        case Failure(exception) =>
          ErrorHandler.error(exception.getMessage)
      }
    }

    referenceHelper(1)
  }

  private def request(details: Map[String, String], token: AccessToken) : BirthResponse = {

    // TODO add logic back in for SocketTimeoutException
    @tailrec
    def detailsHelper(attempts: Attempts) : BirthResponse = {
      Try(getChildByDetails(details, token, attempts)) match {
        case Success((r @ BirthSuccessResponse(js), _)) => r
        case Success((r @ BirthErrorResponse(_), loop)) =>
          if (loop < delayAttempts) {
            ErrorHandler.wait(delayTime)
            detailsHelper(attempts + 1)
          }
          else r
        case Failure(exception : SocketTimeoutException) =>
          if (attempts < delayAttempts) {
            ErrorHandler.wait(delayTime)
            detailsHelper(attempts + 1)
          }
          else ErrorHandler.error(exception.getMessage)
        case Failure(exception) =>
          ErrorHandler.error(exception.getMessage)
      }
    }

    detailsHelper(1)
  }

  def get(reference: String)
                  (implicit hc: HeaderCarrier): Future[BirthResponse] =
  {
    metrics.requestCount()
    val json = authenticator.token match {
      case BirthAccessTokenResponse(token) =>
        request(reference, token)
      case error @BirthErrorResponse(e) =>
        error
    }
    Future.successful(json)
  }

  def get(firstName: String, lastName: String, dateOfBirth: String)
                (implicit hc: HeaderCarrier) : Future[BirthResponse] =
  {
    metrics.requestCount("details-request")
    val json = authenticator.token match {
      case BirthAccessTokenResponse(token) =>
        val details = Map("forenames" -> firstName, "lastname" -> lastName, "dateofbirth" -> dateOfBirth)
        request(details, token)
      case error @BirthErrorResponse(e) =>
        error
    }
    Future.successful(json)
  }

}