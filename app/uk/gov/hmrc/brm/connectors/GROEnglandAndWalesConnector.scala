/*
 * Copyright 2021 HM Revenue & Customs
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

import javax.inject.Inject
import uk.co.bigbeeconsultants.http.header.Headers
import uk.co.bigbeeconsultants.http.response.Response
import uk.co.bigbeeconsultants.http.{HttpClient, _}
import uk.gov.hmrc.brm.config.{GroAppConfig, ProxyAppConfig}
import uk.gov.hmrc.brm.connectors.ConnectorTypes.{AccessToken, Attempts}
import uk.gov.hmrc.brm.metrics.BRMMetrics
import uk.gov.hmrc.brm.tls.HttpClientFactory
import uk.gov.hmrc.brm.utils.BrmLogger
import uk.gov.hmrc.brm.utils.BrmLogger.{error, _}
import uk.gov.hmrc.http.HeaderCarrier

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}


class GROEnglandAndWalesConnector @Inject()(groConfig: GroAppConfig,
                                            proxyConfig: ProxyAppConfig,
                                            httpClientFactory: HttpClientFactory,
                                            val authenticator: Authenticator) {

  private val CLASS_NAME: String = this.getClass.getSimpleName

  val endpoint: String = s"${groConfig.serviceUrl}/api/v0/events/birth"
  val username: String = groConfig.groUsername
  val http: HttpClient = httpClientFactory.apply()
  val encoder: Encoder = Encoder
  val delayTime: Int = groConfig.delayAttemptInMilliseconds
  val delayAttempts: Int = groConfig.delayAttempts

  protected val extractJson: PartialFunction[Response, BirthResponse] = {
    case response: Response =>
      ResponseParser.parse(response)
  }

  private def groHeaderCarrier(token: String): Map[AccessToken, String] = {
    Map(
      "Authorization" -> s"Bearer $token",
      "X-Auth-Downstream-Username" -> username
    )
  }

  private[GROEnglandAndWalesConnector] def getChildByReference(reference: String,
                                                  token: AccessToken,
                                                  attempts: Attempts)(implicit metrics: BRMMetrics, ec: ExecutionContext): (BirthResponse, Attempts) = {
    val headers = groHeaderCarrier(token)
    metrics.requestCount() // increase counter for attempt to gro reference

    debug(CLASS_NAME, "getChildByReference", s"$endpoint/$reference headers: $headers")
    info(CLASS_NAME, "getChildByReference", s"requesting child's details $endpoint, attempt $attempts")

    val startTime = metrics.startTimer()

    val response = http.get(s"$endpoint/$reference", Headers.apply(headers))

    metrics.endTimer(startTime, "reference-match-timer")

    BrmLogger.debug(s"[BirthConnector][getChildByReference][HttpResponse][Debug] $response")

    ResponseHandler.handle(response, attempts)(extractJson, metrics)
  }

  private[GROEnglandAndWalesConnector] def getChildByDetails(details: Map[String, String],
                                                token: AccessToken,
                                                attempts: Attempts)(implicit metrics: BRMMetrics, ec: ExecutionContext): (BirthResponse, Attempts) = {
    val headers = groHeaderCarrier(token)
    metrics.requestCount("details-request") // increase counter for attempt to gro details

    debug(CLASS_NAME, "getChildByDetails", s"$endpoint/ headers: $headers")
    info(CLASS_NAME, "getChildByDetails", s"requesting child's details $endpoint, attempt $attempts")

    val startTime = metrics.startTimer()
    val query = encoder.encode(details)
    val url = s"$endpoint?$query"

    debug(CLASS_NAME, "getChildByDetails", s"query: $url")

    val response = http.get(url, Headers.apply(headers))

    BrmLogger.debug(s"[BirthConnector][getChildByDetails][HttpResponse][Debug] $response")

    metrics.endTimer(startTime, "details-match-timer")
    ResponseHandler.handle(response, attempts)(extractJson, metrics)
  }

  private def request(reference: String, token: AccessToken)(implicit metrics: BRMMetrics): BirthResponse = {

    @tailrec
    def referenceHelper(attempts: Attempts): BirthResponse = {
      info(CLASS_NAME, "request", s"[referenceHelper] attempting to find record by reference, attempt: $attempts")

      Try(getChildByReference(reference, token, attempts)) match {
        case Success((response, _)) =>
          info(CLASS_NAME, "request", s"[referenceHelper] found record by reference")
          response
        case Failure(exception) =>
          exception match {
            case e: SocketTimeoutException =>
              if (attempts < delayAttempts) {
                ErrorHandler.wait(delayTime)
                referenceHelper(attempts + 1)
              } else {
                error(CLASS_NAME, "request", s"[referenceHelper] socket timeout exception when loading record by reference")
                ErrorHandler.error(e.getMessage)
              }
            case e: Exception =>
              error(CLASS_NAME, "request", s"[referenceHelper] failed to load record by reference: unknown exception: $e")
              ErrorHandler.error(e.getMessage)
          }
      }
    }
    referenceHelper(1)
  }

  /**
    * if the failure is caused due to a SocketTimeoutException then retry
    */
  private def request(details: Map[String, String], token: AccessToken)(implicit metrics: BRMMetrics): BirthResponse = {
    @tailrec
    def detailsHelper(attempts: Attempts): BirthResponse = {
      info(CLASS_NAME, "request", s"[detailsHelper] attempting to find record(s) by details, attempt $attempts")

      Try(getChildByDetails(details, token, attempts)) match {
        case Success((response, _)) =>
          info(CLASS_NAME, "request", s"[detailsHelper] found record(s) by details")
          response
        case Failure(exception) =>
          exception match {
            case e: SocketTimeoutException =>
              if (attempts < delayAttempts) {
                ErrorHandler.wait(delayTime)
                detailsHelper(attempts + 1)
              } else {
                error(CLASS_NAME, "request", s"[detailsHelper] socket timeout exception when loading record(s) by details")
                ErrorHandler.error(e.getMessage)
              }
            case e: Exception =>
              error(CLASS_NAME, "request", s"[detailsHelper] failed to load record by details: unknown exception: $e")
              ErrorHandler.error(e.getMessage)
          }
      }
    }
    detailsHelper(1)
  }

  def getReference(reference: String)
                  (implicit hc: HeaderCarrier, metrics: BRMMetrics): Future[BirthResponse] = {
    val json = authenticator.token match {
      case BirthAccessTokenResponse(token) =>
        info(CLASS_NAME, "getReference", s"valid access token obtained")
        request(reference, token)
      case e@BirthErrorResponse(_) =>
        warn(CLASS_NAME, "getReference", s"Failed to obtain access token: $e")
        e
    }
    Future.successful(json)
  }

  def getDetails(forenames: String, lastname: String, dateofbirth: String)
                (implicit hc: HeaderCarrier, metrics: BRMMetrics): Future[BirthResponse] = {
    val json = authenticator.token match {
      case BirthAccessTokenResponse(token) =>
        info(CLASS_NAME, "getDetails", s"valid access token obtained")
        val details = Map("forenames" -> forenames, "lastname" -> lastname, "dateofbirth" -> dateofbirth)
        request(details, token)
      case e@BirthErrorResponse(_) =>
        warn(CLASS_NAME, "getDetails", s"Failed to obtain access token: $e")
        e
    }
    Future.successful(json)
  }

}
