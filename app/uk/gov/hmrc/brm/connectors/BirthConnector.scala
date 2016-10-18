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

import java.net.SocketTimeoutException

import play.api.Logger
import play.api.http.Status._
import play.api.libs.json._
import uk.co.bigbeeconsultants.http.header.Headers
import uk.co.bigbeeconsultants.http.request.RequestBody
import uk.co.bigbeeconsultants.http.response.{Response, Status}
import uk.co.bigbeeconsultants.http.{HttpClient, _}
import uk.gov.hmrc.brm.config.GROConnectorConfiguration
import uk.gov.hmrc.brm.metrics.{GroMetrics, Metrics}
import uk.gov.hmrc.brm.tls.TLSFactory
import uk.gov.hmrc.brm.utils.BrmLogger._
import uk.gov.hmrc.brm.utils.{AccessTokenRepository, CertificateStatus}
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.config.ServicesConfig

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.util.{Failure, Success}

sealed trait BirthResponse

case class BirthAccessTokenResponse(token : String) extends BirthResponse
case class BirthSuccessResponse(json: JsValue) extends BirthResponse

case class BirthErrorResponse(cause: Exception) extends BirthResponse

trait BirthConnector extends ServicesConfig {

  protected val version: String = GROConnectorConfiguration.version
  protected val eventUri = s"api/$version/events/birth"
  protected val authUri = s"oauth/login"
  protected val CLASS_NAME : String = this.getClass.getCanonicalName

  protected lazy val eventEndpoint = s"${GROConnectorConfiguration.serviceUrl}/$eventUri"
  protected lazy val authEndpoint = s"${GROConnectorConfiguration.serviceUrl}/$authUri"

  protected val httpClient: HttpClient
  protected val metrics: Metrics
  protected val authRepository : AccessTokenRepository

  private def throwInternalServerError(response: Response, message: String = "_") = {
    BirthErrorResponse(
      Upstream5xxResponse(
        s"[${super.getClass.getName}][InternalServerError][$message]",
        response.status.code,
        response.status.code)
    )
  }

  private def throwBadRequest(response: Response) = {
    BirthErrorResponse(
      Upstream4xxResponse(
        s"[${super.getClass.getName}][${response.status.toString}]",
        response.status.code,
        response.status.code)
    )
  }

  protected def parseJson(response: Response) : BirthResponse = {
    try {
      val bodyText = response.body.asString
      debug(CLASS_NAME, "parseJson",s"${response.body.asString}")

      val json = Json.parse(bodyText)
      BirthSuccessResponse(json)
    } catch {
      case e: Exception =>
        warn(CLASS_NAME, "parseJson", "unable to parse json")
        throwInternalServerError(response, "unable to parse json")
    }
  }

  protected val extractJson: PartialFunction[Response, BirthResponse] = {
    case response: Response =>
      info(CLASS_NAME, "extractJson", "parsing json from reference endpoint")
      parseJson(response)
  }

  protected val extractAccessToken: PartialFunction[Response, BirthResponse] = {
    case response: Response =>
      info(CLASS_NAME, "extractAccessToken", "parsing response from authentication")
      parseJson(response) match {
        case BirthSuccessResponse(body) =>

          val token = body.\("access_token").as[String]
          val seconds = body.\("expires_in").as[Int]

          // save the new token
          authRepository.saveToken(token, authRepository.newExpiry(seconds))

          BirthAccessTokenResponse(token)
        case e @ BirthErrorResponse(err) =>
          error(CLASS_NAME, "extractAccessToken", s"BirthErrorResponse received: ${err.getMessage}")
          e
      }
  }

  private def handleResponse(response: Response, f: PartialFunction[Response, BirthResponse], method: String): BirthResponse = {
    debug(CLASS_NAME, "handleResponse",s"[$method] : $response")
    info(CLASS_NAME, "handleResponse", s"[$method] response received")
    response.status match {
      case Status.S200_OK =>
        metrics.httpResponseCodeStatus(OK)
        info(CLASS_NAME, "handleResponse",s"[$method][200] Success")
        f(response)
      case e@Status.S400_BadRequest =>
        metrics.httpResponseCodeStatus(BAD_REQUEST)
        warn(CLASS_NAME, "handleResponse",s"[$method][400] BadRequest: $response")
        throwBadRequest(response)
      case e@Status.S404_NotFound =>
        metrics.httpResponseCodeStatus(BAD_REQUEST)
        warn(CLASS_NAME, "handleResponse",s"[$method][404] Not Found: $response")
        throwBadRequest(response)
      case e@_ =>
        metrics.httpResponseCodeStatus(INTERNAL_SERVER_ERROR)
        error(CLASS_NAME, "handleResponse",s"[$method][5xx] InternalServerError: $response")
        throwInternalServerError(response)
    }
  }

  private def GROEventHeaderCarrier(token: String) = {
    Map(
      "Authorization" -> s"Bearer $token",
      "X-Auth-Downstream-Username" -> GROConnectorConfiguration.username
    )
  }

  @tailrec
  private def requestAuth(count : Int = 1)(implicit hc: HeaderCarrier) : BirthResponse = {
    if(!CertificateStatus.certificateStatus()) {
      // return an BirthErrorResponse as TLS certificate has expired
      error(CLASS_NAME, "requestAuth", "TLS Certificate expired")
      BirthErrorResponse(
        Upstream5xxResponse(
          s"[${super.getClass.getName}][InternalServerError][TLS Certificate expired]",
          INTERNAL_SERVER_ERROR,
          INTERNAL_SERVER_ERROR)
      )
    } else {
      info(CLASS_NAME, "requestAuth", "checking access_token")
      authRepository.token match {
        case Success(token) =>
          info(CLASS_NAME, "requestAuth", s"access_token has not expired")
          debug(CLASS_NAME, "requestAuth", s"cached access_token: $token")
          BirthAccessTokenResponse(token)
        case Failure(noToken) =>
          info(CLASS_NAME, "requestAuth", s"access_token has expired ${noToken.getMessage}")
          //get new auth token

          try {
            val response = getAuthResponse
            handleResponse(response, extractAccessToken, "requestAuth")
          } catch {
            case e : SocketTimeoutException =>
              if (count < 3) {
                info(CLASS_NAME, "requestAuth", s"SocketTimeoutException on attempt: $count, error: ${e.getMessage}")
                // failed to request authentication try again?
                requestAuth(count + 1)
              } else {
                warn(CLASS_NAME, "requestAuth", s"SocketTimeoutException on all attempts, error: ${e.getMessage}")
                BirthErrorResponse(e)
              }
            case e : Exception =>
              warn(CLASS_NAME, "requestAuth", s"Exception: ${e.getMessage}")
              BirthErrorResponse(e)
          }
      }
    }
  }


  private def getAuthResponse : Response = {
    val credentials: Map[String, String] = Map(
      "username" -> GROConnectorConfiguration.username,
      "password" -> GROConnectorConfiguration.password
    )

    debug(CLASS_NAME, "requestAuth", s"$authEndpoint credentials: $credentials")
    info(CLASS_NAME, "requestAuth", s"requesting authentication token $authEndpoint")
    metrics.requestCount("authentication")

    val startTime = metrics.startTimer()
    // request new access token
    val response = httpClient.post(
      url = authEndpoint,
      body = Some(RequestBody.apply(credentials)),
      requestHeaders = Headers.apply(
        Map("Content-Type" -> "application/x-www-form-urlencoded")
      )
    )

    metrics.endTimer(startTime, "authentication-timer")
    response
  }

  @tailrec
  private def requestReference(reference: String, count : Int = 1)(implicit hc: HeaderCarrier): BirthResponse = {
    requestAuth() match {
      case BirthAccessTokenResponse(token) =>
        val startTime = metrics.startTimer()

        val headerCarrier = GROEventHeaderCarrier(token)
        metrics.requestCount("reference-match")

        debug(CLASS_NAME, "requestReference", s"$eventEndpoint/$reference headers: $headerCarrier")
        info(CLASS_NAME, "requestReference", s"requesting child's details $eventEndpoint")

        try {
          val response = httpClient.get(s"$eventEndpoint/$reference", Headers.apply(headerCarrier))

          metrics.endTimer(startTime, "reference-match-timer")
          handleResponse(response, extractJson, "requestReference")
        } catch {
          case e : SocketTimeoutException =>
            if (count < 3) {
              info(CLASS_NAME, "requestReference", s"SocketTimeoutException on attempt: $count error: ${e.getMessage}")
              // failed to request authentication try again?
              requestReference(reference, count + 1)
            } else {
              warn(CLASS_NAME, "requestReference", s"SocketTimeoutException on all attempts, error: ${e.getMessage}")
              BirthErrorResponse(e)
            }
          case e : Exception =>
            warn(CLASS_NAME, "requestReference", s"Exception: ${e.getMessage}")
            BirthErrorResponse(e)
        }
      case birthError @ BirthErrorResponse(e) =>
        warn(CLASS_NAME, "requestReference", "BirthErrorResponse returned from requestAuth()")
        birthError
    }

  }

  def getReference(reference: String)(implicit hc: HeaderCarrier): Future[BirthResponse] = {
    metrics.requestCount()
    val json = requestReference(reference)
    Future.successful(json)
  }

}

// $COVERAGE-OFF$
object GROEnglandAndWalesConnector extends BirthConnector {
  val config = TLSFactory.getConfig
  override val httpClient = new HttpClient(config)
  override val metrics = GroMetrics
  override val authRepository = new AccessTokenRepository
}
// $COVERAGE-ON$
