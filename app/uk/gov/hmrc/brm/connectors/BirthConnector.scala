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

import play.api.Logger
import play.api.http.Status
import play.api.libs.json.{JsString, JsValue, Json}
import play.api.libs.ws.WS
import uk.gov.hmrc.brm.config.{GROConnectorConfiguration, WSHttp}
import uk.gov.hmrc.play.config.ServicesConfig

import uk.gov.hmrc.play.http.ws.{WSPost, WSGet, WSHttp}
import uk.gov.hmrc.play.http._

import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

trait BirthConnector extends ServicesConfig {

  protected val version : String = GROConnectorConfiguration.version
  protected val eventUri = s"api/$version/events/birth"
  protected val authUri = s"oauth/login"

  protected lazy val eventEndpoint = s"${GROConnectorConfiguration.serviceUrl}/$eventUri"
  protected lazy val authEndpoint = s"${GROConnectorConfiguration.serviceUrl}/$authUri"

  protected val httpGet : HttpGet = WSHttp
  protected val httpPost : HttpPost = WSHttp

  protected val extractJson : PartialFunction[HttpResponse, JsValue] = { case response : HttpResponse => response.json }
  protected val extractAccessToken : PartialFunction[HttpResponse, JsValue] = { case response : HttpResponse => response.json.\("access_token") }

  protected val keystore = GROConnectorConfiguration.TLSPrivateKeystore
  protected val keystoreKey = GROConnectorConfiguration.TLSPrivateKeystoreKey

  import play.api.Play.current

  private def GROEventHeaderCarrier(token : String) = {
    HeaderCarrier()
      .withExtraHeaders("Authorization" -> s"Bearer $token")
      .withExtraHeaders("X-Auth-Downstream-Username" -> GROConnectorConfiguration.username)
  }

  private def requestAuth(body : String => Future[JsValue])(implicit hc : HeaderCarrier) = {
    val credentials = Map(
      "username" -> Seq(GROConnectorConfiguration.username),
      "password" -> Seq(GROConnectorConfiguration.password)
    )
    Logger.debug(s"[BirthConnector][requestAuth] credentials: $credentials, endpoint: $authEndpoint")
    httpPost.POSTForm(authEndpoint, credentials) map {
      response =>
        body(handleResponse(response, extractAccessToken).as[String])
    }
  }

  private def handleResponse(response: HttpResponse, f : PartialFunction[HttpResponse, JsValue]) = {
    response.status match {
      case Status.OK =>
        f(response)
      case e @ Status.BAD_REQUEST =>
        throw new Upstream4xxResponse(s"[${super.getClass.getName}][BadRequest]", e, Status.BAD_REQUEST)
      case e @ _ =>
        throw new Upstream5xxResponse(s"[${super.getClass.getName}][InternalServerError]", e, Status.INTERNAL_SERVER_ERROR)
    }
  }

  private def requestReference(reference: String)(implicit hc : HeaderCarrier) = {
    Logger.info(s"keystore: $keystore key: $keystoreKey")
    requestAuth(
      token => {
        Logger.debug(s"Request Details. Token: $token")
        httpGet.GET[HttpResponse](s"$eventEndpoint/$reference")(hc = GROEventHeaderCarrier(token), rds = HttpReads.readRaw) map {
          response =>
            handleResponse(response, extractJson)
        }
      }
    )
  }

  private def requestDetails(params : Map[String, String])(implicit hc : HeaderCarrier) = {
    requestAuth(
      token => {
        Logger.debug(s"Request Details. Token: $token")
        val endpoint = WS.url(eventEndpoint).withQueryString(params.toList: _*).url
        httpGet.GET[HttpResponse](endpoint)(hc = GROEventHeaderCarrier(token), rds = HttpReads.readRaw) map {
          response =>
            handleResponse(response, extractJson)
        }
      }
    )
  }


  def getReference(reference: String)(implicit hc : HeaderCarrier) : Future[JsValue] = {
    requestReference(reference) flatMap {
      response =>
        response
    }
  }

  def getChildDetails(params : Map[String, String])(implicit hc : HeaderCarrier) : Future[JsValue] = {
    requestDetails(params) flatMap {
      response =>
        response
    }
  }

}

object GROEnglandAndWalesConnector extends BirthConnector
