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

import uk.co.bigbeeconsultants.http.header.Headers
import uk.co.bigbeeconsultants.http.response.Response
import uk.co.bigbeeconsultants.http.{HttpClient, _}
import uk.gov.hmrc.brm.config.GROConnectorConfiguration
import uk.gov.hmrc.brm.connectors.ConnectorTypes.{AccessToken, Attempts}
import uk.gov.hmrc.brm.metrics.{GroMetrics, Metrics}
import uk.gov.hmrc.brm.tls.{HttpClientFactory, TLSFactory}
import uk.gov.hmrc.brm.utils.BrmLogger._
import uk.gov.hmrc.brm.utils.NameFormat
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
 * GROEnglandAndWalesConnector
 */

object GROEnglandAndWalesConnector extends BirthConnector {
  override val http = HttpClientFactory.apply()
  override val metrics = GroMetrics
  override val authenticator = Authenticator.apply()
  override val delayTime = GROConnectorConfiguration.delayAttemptInMilliseconds
  override val delayAttempts = GROConnectorConfiguration.delayAttempts
  override val version = GROConnectorConfiguration.version
  override val endpoint = s"${GROConnectorConfiguration.serviceUrl}/api/$version/events/birth"
  override val username = GROConnectorConfiguration.username
  override val encoder = Encoder
}

trait BirthConnector extends ServicesConfig {

  private val CLASS_NAME : String = this.getClass.getCanonicalName

  protected val version: String
  protected val endpoint : String

  protected val username : String

  protected val http: HttpClient
  protected val metrics: Metrics

  protected val encoder : Encoder

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
    val query = encoder.encode(details)
    val url = s"$endpoint/?$query"

    debug(CLASS_NAME, "getChildByDetails", s"query: $url")

    val response = http.get(url, Headers.apply(headers))
    metrics.endTimer(startTime, "details-match-timer")
    ResponseHandler.handle(response, attempts)(extractJson, metrics)
  }

  private def request(reference: String, token: AccessToken) : BirthResponse = {
    @tailrec
    def referenceHelper(attempts: Attempts) : BirthResponse = {
      Try(getChildByReference(reference, token, attempts)) match {
        case Success((response, _)) => response
        case Failure(exception) =>
          exception match {
            case e : SocketTimeoutException =>
              if (attempts < delayAttempts) {
                ErrorHandler.wait(delayTime)
                referenceHelper(attempts + 1)
              } else { ErrorHandler.error(exception.getMessage) }
            case e : Exception =>
              ErrorHandler.error(exception.getMessage)
          }
      }
    }

    referenceHelper(1)
  }

  /**
    * if the failure is caused due to a SocketTimeoutException then retry
     */
  private def request(details: Map[String, String], token: AccessToken) : BirthResponse = {
    @tailrec
    def detailsHelper(attempts: Attempts) : BirthResponse = {
      Try(getChildByDetails(details, token, attempts)) match {
        case Success((response, _)) => response
        case Failure(exception) =>
          exception match {
            case e : SocketTimeoutException =>
              if (attempts < delayAttempts) {
                ErrorHandler.wait(delayTime)
                detailsHelper(attempts + 1)
              } else { ErrorHandler.error(exception.getMessage) }
            case e : Exception =>
              ErrorHandler.error(exception.getMessage)
          }
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

  def get(forenames: String, lastname: String, dateofbirth: String)
                (implicit hc: HeaderCarrier) : Future[BirthResponse] =
  {
    metrics.requestCount("details-request")
    val json = authenticator.token match {
      case BirthAccessTokenResponse(token) =>
        val details = Map("forenames" -> NameFormat(forenames), "lastname" -> NameFormat(lastname), "dateofbirth" -> dateofbirth)
        request(details, token)
      case error @BirthErrorResponse(e) =>
        error
    }
    Future.successful(json)
  }

}