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
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._

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
        warn(CLASS_NAME, "parseJson",s"unable to parse json")
        throwInternalServerError(response, "unable to parse json")
    }
  }

  protected val extractJson: PartialFunction[Response, BirthResponse] = {
    case response: Response =>

    parseJson(response)
  }

  protected val extractAccessToken: PartialFunction[Response, BirthResponse] = {
    case response: Response =>

      parseJson(response) match {
        case BirthSuccessResponse(body) =>

          val token = body.\("access_token").as[String]
          val seconds = body.\("expires_in").as[Int]

          // save the new token
          authRepository.saveToken(token, authRepository.newExpiry(seconds))

          BirthAccessTokenResponse(token)
        case e @ BirthErrorResponse(error) =>
          e

      }
  }

  private def handleResponse(response: Response, f: PartialFunction[Response, BirthResponse], method: String): BirthResponse = {
    debug(CLASS_NAME, "handleResponse",s"[$method] : $response")
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
        info(CLASS_NAME, "handleResponse",s"[$method][404] Not Found: $response")
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

  private def requestAuth()(implicit hc: HeaderCarrier) : BirthResponse = {
    if(!CertificateStatus.certificateStatus()) {
      // return an BirthErrorResponse as TLS certificate has expired
      BirthErrorResponse(
        Upstream5xxResponse(
          s"[${super.getClass.getName}][InternalServerError][TLS Certificate expired]",
          INTERNAL_SERVER_ERROR,
          INTERNAL_SERVER_ERROR)
      )
    } else {
      debug(this, "requestAuth", "checking access_token")
      authRepository.token match {
        case Success(token) =>
          debug(this, "requestAuth", s"cached token: $token")
            BirthAccessTokenResponse(token)
        case Failure(noToken) =>

          info(this, "requestAuth", s"token has expired")
          debug(this, "requestAuth", s"${noToken.getMessage}")
          //get new auth token
          val response = getAuthResponse()

          handleResponse(response, extractAccessToken, "requestAuth")
      }
    }
  }


  private def getAuthResponse() : Response = {
    val credentials: Map[String, String] = Map(
      "username" -> GROConnectorConfiguration.username,
      "password" -> GROConnectorConfiguration.password
    )

    debug(this, "requestAuth", s"$authEndpoint credentials: $credentials")
    info(this, "requestAuth", s"$authEndpoint")

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

  private def requestReference(reference: String)(implicit hc: HeaderCarrier): BirthResponse = {
    requestAuth() match {
      case BirthAccessTokenResponse(token) =>
        val startTime = metrics.startTimer()

        val headerCarrier = GROEventHeaderCarrier(token)

        debug(CLASS_NAME, "requestReference", s"$eventEndpoint headers: $headerCarrier")
        info(CLASS_NAME, "requestReference", s": $eventEndpoint")
        val response = httpClient.get(s"$eventEndpoint/$reference", Headers.apply(headerCarrier))

        metrics.endTimer(startTime, "reference-match-timer")
        handleResponse(response, extractJson, "requestReference")
      case error@BirthErrorResponse(e) =>
        error
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
