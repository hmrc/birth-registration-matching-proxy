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

import akka.actor.ActorSystem
import play.api.Configuration
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.{AhcWSClient, StandaloneAhcWSClient}
import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClient
import uk.gov.hmrc.brm.config.GroAppConfig
import uk.gov.hmrc.brm.connectors.ConnectorTypes.AccessToken
import uk.gov.hmrc.brm.http.ProxyEnabledHttpClient
import uk.gov.hmrc.brm.metrics.BRMMetrics
import uk.gov.hmrc.brm.utils.BrmLogger
import uk.gov.hmrc.brm.utils.BrmLogger.{error, _}
import uk.gov.hmrc.http.HttpReads.Implicits
import uk.gov.hmrc.http.{BadGatewayException, GatewayTimeoutException, HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}


class GROEnglandAndWalesConnector @Inject()(groConfig: GroAppConfig,
                                            httpAuditing: HttpAuditing,
                                            wsClient: WSClient,
                                            system: ActorSystem,
                                            val authenticator: Authenticator,
                                            configuration: Configuration)
  {

    val http: HttpClient = new ProxyEnabledHttpClient(
      configuration,
      httpAuditing,
      wsClient,
      system
    )
    info("this", "that",
      s"so ws client has ${wsClient}")
  private val CLASS_NAME: String = this.getClass.getSimpleName

  val endpoint: String = s"${groConfig.serviceUrl}/api/v0/events/birth"
  val username: String = groConfig.groUsername
  val encoder: Encoder = Encoder
  val responseHandler: ResponseHandler = new ResponseHandler

  protected val extractJson: PartialFunction[HttpResponse, BirthResponse] = {
    case response: HttpResponse =>
      ResponseParser.parse(response)
  }

  private def groHeaderCarrier(token: AccessToken): Seq[(String, String)] = {
    Seq(
      "Authorization" -> s"Bearer $token",
      "X-Auth-Downstream-Username" -> username
    )
  }

  private[GROEnglandAndWalesConnector] def getChildByReference(reference: String,
                                                               token: AccessToken)(
    implicit hc: HeaderCarrier, metrics: BRMMetrics, ec: ExecutionContext): Future[BirthResponse] = {
    val headers = groHeaderCarrier(token)
    metrics.requestCount() // increase counter for attempt to gro reference

    debug(CLASS_NAME, "getChildByReference", s"$endpoint/$reference headers: $headers")
    info(CLASS_NAME, "getChildByReference", s"requesting child's details $endpoint")

    val startTime = metrics.startTimer()

    val response = http.GET(s"$endpoint/$reference", Seq.empty[(String, String)], headers)(
      rds = Implicits.readRaw,
      hc,
      ec
    )

    metrics.endTimer(startTime, "reference-match-timer")

    responseHandler.handle(response)(extractJson, metrics)
  }

  private[GROEnglandAndWalesConnector] def getChildByDetails(details: Map[String, String],
                                                             token: AccessToken)(
    implicit hc: HeaderCarrier,
    metrics: BRMMetrics, ec: ExecutionContext): Future[BirthResponse] = {
    val headers = groHeaderCarrier(token)
    metrics.requestCount("details-request") // increase counter for attempt to gro details

    debug(CLASS_NAME, "getChildByDetails", s"$endpoint/ headers: $headers")
    info(CLASS_NAME, "getChildByDetails", s"requesting child's details $endpoint")

    val startTime = metrics.startTimer()
    val query = encoder.encode(details)
    val url = s"$endpoint?$query"

    debug(CLASS_NAME, "getChildByDetails", s"query: $url")

    val response = http.GET(url, Seq.empty[(String, String)], headers)(
      rds = Implicits.readRaw,
      hc,
      ec
    )

    BrmLogger.debug(s"[BirthConnector][getChildByDetails][HttpResponse][Debug] $response")

    metrics.endTimer(startTime, "details-match-timer")
    responseHandler.handle(response)(extractJson, metrics)
  }

  private def request(reference: String, token: AccessToken)(
    implicit hc: HeaderCarrier, metrics: BRMMetrics, ec: ExecutionContext): Future[BirthResponse] = {

    info(CLASS_NAME, "request", s"[referenceHelper] attempting to find record by reference")

    getChildByReference(reference, token).map {
      case child: BirthSuccessResponse[_] =>
        info(CLASS_NAME, "request", s"[referenceHelper] found record by reference")
        child
      case notFound: Birth4xxErrorResponse =>
        info(CLASS_NAME, "request", s"[referenceHelper] not found record by reference")
        notFound
      case BirthErrorResponse(exception) =>
        exception match {
          case e: GatewayTimeoutException =>
            error(CLASS_NAME, "request", s"[referenceHelper] gateway timeout exception when loading record by reference")
            ErrorHandler.error(e.getMessage)
          case e: BadGatewayException =>
            error(CLASS_NAME, "request", s"[referenceHelper] bad gateway exception when loading record by reference")
            ErrorHandler.error(e.getMessage)
          case e: Exception =>
            error(CLASS_NAME, "request", s"[referenceHelper] failed to load record by reference: unknown exception: $e")
            ErrorHandler.error(e.getMessage)
        }
    }
  }

  private def request(details: Map[String, String], token: AccessToken)(
    implicit hc: HeaderCarrier, metrics: BRMMetrics, ec: ExecutionContext): Future[BirthResponse] = {
    info(CLASS_NAME, "request", s"[detailsHelper] attempting to find record(s) by details")

    getChildByDetails(details, token).map {
      case child: BirthSuccessResponse[_] =>
        info(CLASS_NAME, "request", s"[detailsHelper] found record(s) by details")
        child
      case notFound: Birth4xxErrorResponse =>
        info(CLASS_NAME, "request", s"[detailsHelper] not found record by details")
        notFound
      case BirthErrorResponse(exception) =>
        exception match {
          case e: GatewayTimeoutException =>
            error(CLASS_NAME, "request", s"[detailsHelper] gateway timeout exception when loading record(s) by details")
            ErrorHandler.error(e.getMessage)
          case e: BadGatewayException =>
            error(CLASS_NAME, "request", s"[detailsHelper] bad gateway exception when loading record(s) by details")
            ErrorHandler.error(e.getMessage)
          case e: Exception =>
            error(CLASS_NAME, "request", s"[detailsHelper] failed to load record by details: unknown exception: $e")
            ErrorHandler.error(e.getMessage)
        }
    }
  }

  def getReference(reference: String)(
    implicit hc: HeaderCarrier, metrics: BRMMetrics, ec: ExecutionContext): Future[BirthResponse] = {
    val json = authenticator.token.flatMap {
      case BirthAccessTokenResponse(token) =>
        info(CLASS_NAME, "getReference", s"valid access token obtained")
        request(reference, token)
      case e@BirthErrorResponse(_) =>
        warn(CLASS_NAME, "getReference", s"Failed to obtain access token: $e")
        Future.successful(e)
    }
    json
  }

  def getDetails(forenames: String, lastname: String, dateofbirth: String)(
    implicit hc: HeaderCarrier, metrics: BRMMetrics, ec: ExecutionContext): Future[BirthResponse] = {
    val json = authenticator.token.flatMap {
      case BirthAccessTokenResponse(token) =>
        info(CLASS_NAME, "getDetails", s"valid access token obtained")
        val details = Map("forenames" -> forenames, "lastname" -> lastname, "dateofbirth" -> dateofbirth)
        request(details, token)
      case e@BirthErrorResponse(_) =>
        warn(CLASS_NAME, "getDetails", s"Failed to obtain access token: $e")
        Future.successful(e)
    }
    json
  }

}
