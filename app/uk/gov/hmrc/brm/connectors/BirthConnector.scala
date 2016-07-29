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

trait BirthConnectorConfig extends ServicesConfig {
  protected val serviceUrl : String
  def username : String
  protected val baseUri : String
  protected val version : String = "v0"
  lazy val endpoint = s"$serviceUrl/$baseUri"

  override def toString = {
    s"endpoint: $endpoint, http, version: $version"
  }
}

case class GROEnglandAndWalesAuthConfig() extends BirthConnectorConfig {
  override val serviceUrl = baseUrl("birth-registration-matching")
  override def username = getConfString("birth-registration-matching.username", throw new RuntimeException("[Configuration][NotFound] birth-registration-matching.username"))
  override val baseUri = s"oauth/login"
  def http : WSPost = WSHttp
  val password = getConfString("birth-registration-matching.password", throw new RuntimeException("[Configuration][NotFound] birth-registration-matching.password"))
}

case class GROEnglandAndWalesEventConfig() extends BirthConnectorConfig {
  override val serviceUrl = baseUrl("birth-registration-matching")
  override def username = getConfString("birth-registration-matching.username", throw new RuntimeException("[Configuration][NotFound] birth-registration-matching.username"))
  override val baseUri = s"api/$version/events/birth"
  def http : WSGet = WSHttp
}

trait BirthConnector {

  def getReference(ref: String)
                  (implicit hc : HeaderCarrier) : Future[JsValue]

  def getDetails(params : Map[String, String])
                (implicit hc : HeaderCarrier) : Future[JsValue]

}

object GROEnglandAndWalesConnector extends BirthConnector {

  val eventConfig = GROEnglandAndWalesEventConfig()
  val authConfig = GROEnglandAndWalesAuthConfig()

  import play.api.Play.current

  def GROEventHeaderCarrier(config : BirthConnectorConfig, token : String) = {
    HeaderCarrier()
      .withExtraHeaders("Authorization" -> s"Bearer $token")
      .withExtraHeaders("X-Auth-Downstream-Username" -> config.username)
  }

  private def requestAuth(body : String => Future[JsValue])(implicit hc : HeaderCarrier) = {
    val credentials = Map(
      "username" -> Seq(authConfig.username),
      "password" -> Seq(authConfig.password)
    )
    GROEnglandAndWalesConnector.authConfig.http.doFormPost(authConfig.endpoint, credentials) map {
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
        GROEnglandAndWalesConnector.eventConfig.http.GET[HttpResponse](eventConfig.endpoint + s"/$reference")
          (hc = GROEventHeaderCarrier(eventConfig, token), rds = HttpReads.readRaw) map {
          response =>
            handleResponse(response)
        }
    )
  }

  private def requestDetails(params : Map[String, String])(implicit hc : HeaderCarrier) = {
    requestAuth(
      token => {
        val endpoint = WS.url(eventConfig.endpoint).withQueryString(params.toList: _*).url
        GROEnglandAndWalesConnector.eventConfig.http.GET[HttpResponse](endpoint)(hc = GROEventHeaderCarrier(eventConfig, token), rds = HttpReads.readRaw) map {
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

  override def getReference(reference: String)(implicit hc : HeaderCarrier) : Future[JsValue] = {
    requestReference(reference) flatMap {
      response =>
        response
    }
  }

  def getDetails(params : Map[String, String])
                (implicit hc : HeaderCarrier) : Future[JsValue] = {
    requestDetails(params) flatMap {
      response =>
        response
    }
  }
}
