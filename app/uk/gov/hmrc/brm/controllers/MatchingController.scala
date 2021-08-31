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

package uk.gov.hmrc.brm.controllers


import javax.inject.{Inject, Singleton}
import play.api.libs.json.{JsArray, JsValue}
import play.api.mvc.{Action, Request, Result}
import uk.gov.hmrc.brm.connectors._
import uk.gov.hmrc.brm.metrics.BRMMetrics
import uk.gov.hmrc.brm.utils.BrmLogger._
import uk.gov.hmrc.brm.utils.KeyHolder
import uk.gov.hmrc.http.{HeaderCarrier, Upstream4xxResponse, Upstream5xxResponse}
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MatchingController @Inject()(val groConnector: GROEnglandAndWalesConnector,
                                   cc: ControllerComponents,
                                   implicit val metrics: BRMMetrics) extends BackendController(cc) {

  val CLASS_NAME : String = this.getClass.getSimpleName
  val HEADER_X_CORRELATION_ID = "X-Correlation-Id"

  implicit val ec: ExecutionContext = cc.executionContext

  private def respond(response: Result): Future[Result] = {
    Future.successful(
      response.as("application/json; charset=utf-8")
    )
  }

  def notFoundException(method: String) : PartialFunction[BirthResponse, Future[Result]] = {
    case Birth404ErrorResponse(Upstream4xxResponse(message, NOT_FOUND, _, _)) =>
      info(CLASS_NAME, "handleException", s"[$method] NotFound: no record found: $message")
      respond(NotFound(ErrorResponses.NOT_FOUND))
  }

  def badRequestException(method: String) : PartialFunction[BirthResponse, Future[Result]] = {
    case BirthErrorResponse(Upstream4xxResponse(message, BAD_REQUEST, _, _)) =>
      warn(CLASS_NAME, "handleException", s"[$method] BadRequest: $message")
      respond(BadRequest(ErrorResponses.BAD_REQUEST))
  }

  def teapotException(method : String) : PartialFunction[BirthResponse, Future[Result]] = {
    case BirthErrorResponse(Upstream4xxResponse(message, IM_A_TEAPOT, _, _)) =>
      warn(CLASS_NAME, "handleException", s"[$method] TeaPot: $message")
      respond(Forbidden(ErrorResponses.TEAPOT))
  }

  def forbiddenException(method: String) : PartialFunction[BirthResponse, Future[Result]] = {
    case BirthErrorResponse(Upstream4xxResponse(message, FORBIDDEN, _, _)) =>
      warn(CLASS_NAME, "handleException", s"[$method] Forbidden [certificate not provided]: $message")
      respond(Forbidden(ErrorResponses.CERTIFICATE_INVALID))
  }

  def badGatewayException(method: String) : PartialFunction[BirthResponse, Future[Result]] = {
    case BirthErrorResponse(Upstream5xxResponse(message, BAD_GATEWAY, _, _)) =>
      error(CLASS_NAME, "handleException", s"[$method] BadGateway: $message")
      respond(BadGateway(ErrorResponses.BAD_GATEWAY))
  }

  def gatewayTimeoutException(method: String) : PartialFunction[BirthResponse, Future[Result]] = {
    case BirthErrorResponse(Upstream5xxResponse(message, GATEWAY_TIMEOUT, _, _)) =>
      error(CLASS_NAME, "handleException", s"[$method] GatewayTimeout: $message")
      respond(GatewayTimeout(ErrorResponses.GATEWAY_TIMEOUT))
  }

  def connectionDown(method: String) : PartialFunction[BirthResponse, Future[Result]] = {
    case BirthErrorResponse(Upstream5xxResponse(message, INTERNAL_SERVER_ERROR, _, _)) =>
      error(CLASS_NAME, "handleException",s"[$method] InternalServerError: Connection to GRO is down: $message")
      respond(InternalServerError(ErrorResponses.CONNECTION_DOWN))
  }

  def serviceUnavailable(method: String) : PartialFunction[BirthResponse, Future[Result]] = {
    case BirthErrorResponse(Upstream5xxResponse(message, SERVICE_UNAVAILABLE, _, _)) =>
      error(CLASS_NAME, "handleException",s"[$method] InternalServerError: Service Unavailable: $message")
      respond(ServiceUnavailable(ErrorResponses.CONNECTION_DOWN))
  }

  def exception(method: String) : PartialFunction[BirthResponse, Future[Result]] = {
    case BirthErrorResponse(e) =>
      error(CLASS_NAME, "handleException",s"[$method] InternalServerError: Unknown exception: $e")
      respond(InternalServerError(ErrorResponses.UNKNOWN_ERROR))
  }

  def success(method: String): PartialFunction[BirthResponse, Future[Result]] = {
    case BirthSuccessResponse(js) =>
      val count = if(js.isInstanceOf[JsArray]) js.as[JsArray].value.length else 1
      info(CLASS_NAME, s"$method", s"success: $count record(s) found")
      respond(Ok(js))
  }

  def handleException(method: String): PartialFunction[BirthResponse, Future[Result]] = Seq(
    notFoundException(method),
    badRequestException(method),
    badGatewayException(method),
    teapotException(method),
    forbiddenException(method),
    gatewayTimeoutException(method),
    connectionDown(method),
    serviceUnavailable(method),
    exception(method),
    success(method)
  ).reduce(_ orElse _)

  def getOrCreateCorrelationID(request: Request[_]): String = {
    debug(CLASS_NAME, "getOrCreateCorrelationID", "Checking for Upstream x-correlation-id, returning new id if none.")
    request.headers.get(HEADER_X_CORRELATION_ID).getOrElse(UUID.randomUUID().toString)
  }

  private def setKey(request : Request[_]): Unit = {
    val brmKey = request.headers.get(BRM_KEY).getOrElse("no-key")
    KeyHolder.setKey(brmKey)
  }

  def reference: Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      implicit val hc: HeaderCarrier = HeaderCarrier().withExtraHeaders((HEADER_X_CORRELATION_ID, getOrCreateCorrelationID(request)))
      setKey(request)

      info(CLASS_NAME, s"reference", s"Reference request received")

      val reference = request.body.\("reference").asOpt[String]

      reference match {
        case Some(r) =>
          groConnector.getReference(r).flatMap[Result](
            handleException("getReference").apply(_)
          )
        case _ =>
          warn(CLASS_NAME, "reference", "Reference not found")
          respond(BadRequest(ErrorResponses.BAD_REQUEST))
      }
  }

  def details(): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      implicit val hc: HeaderCarrier = HeaderCarrier().withExtraHeaders((HEADER_X_CORRELATION_ID, getOrCreateCorrelationID(request)))
      setKey(request)

      info(CLASS_NAME, s"details", s"Details request received")

      val forenames = request.body.\("forenames").asOpt[String]
      val lastname = request.body.\("lastname").asOpt[String]
      val dateofbirth = request.body.\("dateofbirth").asOpt[String]

      debug(CLASS_NAME, "details", s"forenames $forenames, lastName: $lastname, dateOfBirth: $dateofbirth")

      (forenames, lastname, dateofbirth) match {
        case (Some(f), Some(l), Some(d)) =>
          groConnector.getDetails(f, l, d).flatMap[Result](
            handleException("getDetails").apply(_)
          )
        case _ =>
          warn(CLASS_NAME, "reference", "Details not found")
          respond(BadRequest(ErrorResponses.BAD_REQUEST))
      }
  }

}
