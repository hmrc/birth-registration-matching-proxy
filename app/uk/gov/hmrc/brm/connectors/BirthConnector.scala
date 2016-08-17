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

import java.io.IOException

import play.api.Logger
import play.api.libs.json._
import uk.co.bigbeeconsultants.http.header.Headers
import uk.co.bigbeeconsultants.http.request.RequestBody
import uk.co.bigbeeconsultants.http.response.{Response, Status}
import uk.co.bigbeeconsultants.http.{HttpClient, _}
import uk.gov.hmrc.brm.config.GROConnectorConfiguration
import uk.gov.hmrc.brm.tls.TLSFactory
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HeaderCarrier, Upstream4xxResponse, Upstream5xxResponse}

import scala.concurrent.Future

trait BirthConnector extends ServicesConfig {

  protected val version : String = GROConnectorConfiguration.version
  protected val eventUri = s"api/$version/events/birth"
  protected val authUri = s"oauth/login"

  protected lazy val eventEndpoint = s"${GROConnectorConfiguration.serviceUrl}/$eventUri"
  protected lazy val authEndpoint = s"${GROConnectorConfiguration.serviceUrl}/$authUri"

  protected val httpClient : HttpClient

  private def throwInternalServerError(response: Response) = throw new Upstream5xxResponse(s"[${super.getClass.getName}][InternalServerError]",  response.status.code, 500)
  private def throwBadRequest(response : Response) = throw new Upstream4xxResponse(s"[${super.getClass.getName}][BadRequest]", response.status.code, 400)

  protected def parseJson(response: Response) = {
    try {
      val bodyText = response.body.asString
      Logger.debug(s"[BirthConnector][parseJson] ${response.body.asString}")
      val json = Json.parse(bodyText)
      json
    } catch {
      case e : Exception =>
        Logger.warn(s"[BirthConnector][parseJson] unable to parse json")
        throwInternalServerError(response)
    }
  }

  protected val extractJson : PartialFunction[Response, JsValue] = {
    case response : Response =>
      parseJson(response)
  }
  protected val extractAccessToken : PartialFunction[Response, JsValue] = {
    case response : Response =>
      parseJson(response).\("access_token")
  }

  private def handleResponse(response: Response, f : PartialFunction[Response, JsValue], method: String) = {
    Logger.debug(s"[BirthConnector][handleResponse][$method] : $response")
    response.status match {
      case Status.S200_OK =>
        Logger.info(s"[BirthConnector][handleResponse][$method][200] Success")
        f(response)
      case e @ Status.S400_BadRequest =>
        Logger.warn(s"[BirthConnector][handleResponse][$method][4xx] BadRequest: $response")
        throwBadRequest(response)
      case e @ _ =>
        Logger.error(s"[BirthConnector][handleResponse][$method][5xx] InternalServerError: $response")
        throwInternalServerError(response)
    }
  }

  private def GROEventHeaderCarrier(token : String) = {
    Map(
      "Authorization" -> s"Bearer $token",
      "X-Auth-Downstream-Username" -> GROConnectorConfiguration.username
    )
  }

  private def requestAuth(body : String => JsValue)(implicit hc : HeaderCarrier) = {
    val credentials : Map[String, String] = Map(
      "username" -> GROConnectorConfiguration.username,
      "password" -> GROConnectorConfiguration.password
    )

    Logger.debug(s"[BirthConnector][requestAuth]: $authEndpoint credentials: $credentials")
    Logger.info(s"[BirthConnector][requestAuth]: $authEndpoint")
    val response = httpClient.post(
      url = authEndpoint,
      body = Some(RequestBody.apply(credentials)),
      requestHeaders = Headers.apply(
        Map("Content-Type" -> "application/x-www-form-urlencoded")
      )
    )
    body(handleResponse(response, extractAccessToken, "requestAuth").as[String])
  }

  private def requestReference(reference: String)(implicit hc : HeaderCarrier) = {
    requestAuth(
      token => {
        Logger.debug(s"[BirthConnector][requestReference]: $eventEndpoint headers: ${GROEventHeaderCarrier(token)}")
        Logger.info(s"[BirthConnector][requestReference]: $eventEndpoint")
        val response = httpClient.get(s"$eventEndpoint/$reference", Headers.apply(GROEventHeaderCarrier(token)))
        handleResponse(response, extractJson, "requestReference")
      }
    )
  }

  private def requestDetails(params : Map[String, String])(implicit hc : HeaderCarrier) = {
    requestAuth(
      token => {
        val response = httpClient.get(s"$eventEndpoint", Headers.apply(GROEventHeaderCarrier(token)))
        handleResponse(response, extractJson, "requestDetails")
      }
    )
  }

  def getReference(reference: String)(implicit hc : HeaderCarrier) : Future[JsValue] = {
    val json = requestReference(reference)
    Future.successful(json)
  }

  def getChildDetails(params : Map[String, String])(implicit hc : HeaderCarrier) : Future[JsValue] = {
    val json = requestDetails(params)
    Future.successful(json)
  }

}

// $COVERAGE-OFF$
object GROEnglandAndWalesConnector extends BirthConnector {
  val config = TLSFactory.getConfig
  override val httpClient = new HttpClient(config)
}
// $COVERAGE-ON$
