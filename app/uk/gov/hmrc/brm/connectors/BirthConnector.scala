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
import uk.gov.hmrc.brm.utils.CertificateStatus
import uk.gov.hmrc.brm.utils.BrmLogger._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._

import scala.concurrent.Future

sealed trait BirthResponse

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

  private def throwInternalServerError(response: Response) = {
    BirthErrorResponse(
      Upstream5xxResponse(
        s"[${super.getClass.getName}][InternalServerError]",
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


  protected def parseJson(response: Response) = {
    try {
      val bodyText = response.body.asString
      debug(CLASS_NAME, "parseJson",s"${response.body.asString}")
      val json = Json.parse(bodyText)
      json
    } catch {
      case e: Exception =>
        warn(CLASS_NAME, "parseJson",s"unable to parse json")
        throw new Upstream5xxResponse("unable to parse json", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)
    }
  }

  protected val extractJson: PartialFunction[Response, BirthResponse] = {
    case response: Response =>
      val json = parseJson(response)
      BirthSuccessResponse(json)
  }

  protected val extractAccessToken: PartialFunction[Response, BirthResponse] = {
    case response: Response =>
      val token = parseJson(response).\("access_token")

      // expiry time
      // set time
      // store token here as well

      BirthSuccessResponse(token)
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

  private def requestAuth(body: BirthResponse => BirthResponse)(implicit hc: HeaderCarrier) = {

    if(!CertificateStatus.certificateStatus()) {
      BirthErrorResponse(
        Upstream5xxResponse(
          s"[${super.getClass.getName}][InternalServerError][TLS Certificate expired]",
          INTERNAL_SERVER_ERROR,
          INTERNAL_SERVER_ERROR)
      )
    } else {

      // check expiry of the auth token repo

      val credentials: Map[String, String] = Map(
        "username" -> GROConnectorConfiguration.username,
        "password" -> GROConnectorConfiguration.password
      )

      debug(this, "requestAuth", s"$authEndpoint credentials: $credentials")
      info(this, "requestAuth", s"$authEndpoint")

      val startTime = metrics.startTimer()

      val response = httpClient.post(
        url = authEndpoint,
        body = Some(RequestBody.apply(credentials)),
        requestHeaders = Headers.apply(
          Map("Content-Type" -> "application/x-www-form-urlencoded")
        )
      )

      metrics.endTimer(startTime, "authentication-timer")

      body(handleResponse(response, extractAccessToken, "requestAuth"))
    }
  }

  private def requestReference(reference: String)(implicit hc: HeaderCarrier): BirthResponse = {
    requestAuth(
      token => {
        token match {
          case BirthSuccessResponse(x) =>

            val startTime = metrics.startTimer()

            debug(CLASS_NAME, "requestReference",s"$eventEndpoint headers: ${GROEventHeaderCarrier(x.as[String])}")
            info(CLASS_NAME, "requestReference",s": $eventEndpoint")
            val response = httpClient.get(s"$eventEndpoint/$reference", Headers.apply(GROEventHeaderCarrier(x.as[String])))

            metrics.endTimer(startTime, "reference-match-timer")

            handleResponse(response, extractJson, "requestReference")

          case error@BirthErrorResponse(e) =>
            error
        }
      }
    )
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
}

// $COVERAGE-ON$
