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
import uk.gov.hmrc.brm.config.WSHttp
import uk.gov.hmrc.play.config.ServicesConfig

import uk.gov.hmrc.play.http.ws.{WSPost, WSGet, WSHttp}
import uk.gov.hmrc.play.http._

import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

trait BirthConnector extends ServicesConfig {

  protected lazy val serviceUrl = baseUrl("birth-registration-matching")
  protected lazy val username = getConfString("birth-registration-matching.username", throw new RuntimeException("[Configuration][NotFound] birth-registration-matching.username"))
  protected lazy val password = getConfString("birth-registration-matching.key", throw new RuntimeException("[Configuration][NotFound] birth-registration-matching.key"))

  protected val version : String = "v0"
  protected val eventUri = s"api/$version/events/birth"
  protected val authUri = s"oauth/login"

  protected lazy val eventEndpoint = s"$serviceUrl/$eventUri"
  protected lazy val authEndpoint = s"$serviceUrl/$authUri"

  val httpGet : HttpGet = WSHttp
  val httpPost : HttpPost = WSHttp

  import play.api.Play.current

  private def GROEventHeaderCarrier(token : String) = {
    HeaderCarrier()
      .withExtraHeaders("Authorization" -> s"Bearer $token")
      .withExtraHeaders("X-Auth-Downstream-Username" -> username)
  }

  private def requestAuth(body : String => Future[JsValue])(implicit hc : HeaderCarrier) = {
    val credentials = Map(
      "username" -> Seq(username),
      "password" -> Seq(password)
    )
    httpPost.POSTForm(authEndpoint, credentials) map {
      response =>
        response.status match {
          case Status.OK =>
            body(response.json.\("access_token").as[String])
          case e =>
            throw new Upstream5xxResponse("something went wrong", e, Status.INTERNAL_SERVER_ERROR)
        }
    }
  }

  private def requestReference(reference: String)(implicit hc : HeaderCarrier) = {
    requestAuth(
      token =>
        httpGet.GET[HttpResponse](eventEndpoint + s"/$reference")
          (hc = GROEventHeaderCarrier(token), rds = HttpReads.readRaw) map {
          response =>
            handleResponse(response)
        }
    )
  }

  private def requestDetails(params : Map[String, String])(implicit hc : HeaderCarrier) = {
    requestAuth(
      token => {
        val endpoint = WS.url(eventEndpoint).withQueryString(params.toList: _*).url
        httpGet.GET[HttpResponse](endpoint)(hc = GROEventHeaderCarrier(token), rds = HttpReads.readRaw) map {
          response =>
            handleResponse(response)
        }
      }
    )
  }

  private def handleResponse(response : HttpResponse) = {
    response.status match {
      case Status.OK =>
        response.json
      case e =>
        throw new Upstream5xxResponse("[GROEnglandAndWalesConnector][Invalid Response]", e, Status.INTERNAL_SERVER_ERROR)
    }
  }

  def getReference(reference: String)(implicit hc : HeaderCarrier) : Future[JsValue] = {
    Logger.debug(s"[GROEnglandAndWalesConnector][getReference]: $reference")
    requestReference(reference) flatMap {
      response =>
        response
    }
  }

  def getChildDetails(params : Map[String, String])(implicit hc : HeaderCarrier) : Future[JsValue] = {
    Logger.debug(s"[GROEnglandAndWalesConnector][getDetails]: $params")
    requestDetails(params) flatMap {
      response =>
        response
    }
  }

}

object GROEnglandAndWalesConnector extends BirthConnector
