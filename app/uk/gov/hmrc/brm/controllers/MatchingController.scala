/*
 * Copyright 2017 HM Revenue & Customs
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

import play.api.libs.json.JsArray
import play.api.mvc.{Action, Request, Result}
import uk.gov.hmrc.brm.connectors._
import uk.gov.hmrc.brm.metrics.{GRODetailsMetrics, GroReferenceMetrics}
import uk.gov.hmrc.brm.utils.BrmLogger._
import uk.gov.hmrc.brm.utils.KeyHolder
import uk.gov.hmrc.play.http.{Upstream4xxResponse, Upstream5xxResponse}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future


object MatchingController extends MatchingController {
  override val groConnector = GROEnglandAndWalesConnector
}

trait MatchingController extends BaseController {

  val CLASS_NAME : String = this.getClass.getCanonicalName

  import scala.concurrent.ExecutionContext.Implicits.global

  val groConnector: BirthConnector

  private def respond(response: Result): Future[Result] = {
    Future.successful(
      response.as("application/json; charset=utf-8")
    )
  }

  def notFoundException(method: String) : PartialFunction[BirthResponse, Future[Result]] = {
    case BirthErrorResponse(Upstream4xxResponse(message, NOT_FOUND, _, _)) =>
      info(CLASS_NAME, "handleException", s"[$method] NotFound: no record found")
      respond(NotFound(ErrorResponses.NOT_FOUND))
  }

  def badRequestException(method: String) : PartialFunction[BirthResponse, Future[Result]] = {
    case BirthErrorResponse(Upstream4xxResponse(message, BAD_REQUEST, _, _)) =>
      warn(CLASS_NAME, "handleException", s"[$method] BadRequest: $message")
      respond(BadGateway(ErrorResponses.BAD_REQUEST))
  }

  def badGatewayException(method: String) : PartialFunction[BirthResponse, Future[Result]] = {
    case BirthErrorResponse(Upstream5xxResponse(message, BAD_GATEWAY, _)) =>
      error(CLASS_NAME, "handleException", s"[$method] BadGateway: $message")
      respond(BadGateway(ErrorResponses.BAD_REQUEST))
  }

  def gatewayTimeoutException(method: String) : PartialFunction[BirthResponse, Future[Result]] = {
    case BirthErrorResponse(Upstream5xxResponse(message, GATEWAY_TIMEOUT, _)) =>
      error(CLASS_NAME, "handleException", s"[$method] GatewayTimeout: $message")
      respond(GatewayTimeout(ErrorResponses.GATEWAY_TIMEOUT))
  }

  def connectionDown(method: String) : PartialFunction[BirthResponse, Future[Result]] = {
    case BirthErrorResponse(Upstream5xxResponse(message, INTERNAL_SERVER_ERROR, _)) =>
      error(CLASS_NAME, "handleException",s"[$method] InternalServerError: Connection to GRO is down")
      respond(InternalServerError(ErrorResponses.CONNECTION_DOWN))
  }

  def exception(method: String) : PartialFunction[BirthResponse, Future[Result]] = {
    case BirthErrorResponse(e) =>
      error(CLASS_NAME, "handleException",s"[$method] InternalServerError: ${e.getMessage}")
      respond(InternalServerError)
  }


  def success(method: String): PartialFunction[BirthResponse, Future[Result]] = {
    case BirthSuccessResponse(js) =>
      val count = if(js.isInstanceOf[JsArray]) js.as[JsArray].value.length else 1
      info(CLASS_NAME, s"$method", s"success: $count record(s) found")
      respond(Ok(js))
  }

  def handle(method: String) = Seq(
    notFoundException(method),
    badRequestException(method),
    badGatewayException(method),
    gatewayTimeoutException(method),
    connectionDown(method),
    exception(method),
    success(method)
  ).reduce(_ orElse _)

  private def setKey(request : Request[_]) = {
    val brmKey = request.headers.get(BRM_KEY).getOrElse("no-key")
    KeyHolder.setKey(brmKey)
  }

  def reference = Action.async(parse.json) {
    implicit request =>
      setKey(request)

      implicit val metrics = GroReferenceMetrics

      val reference = request.body.\("reference").asOpt[String]
      reference match {
        case Some(r) =>
          groConnector.get(r).flatMap[Result](
            handle("getReference").apply(_)
          )
        case _ =>
          respond(BadRequest)
      }
  }

  def details() = Action.async(parse.json) {
    implicit request =>
      setKey(request)

      implicit val metrics = GRODetailsMetrics

      val firstname = request.body.\("forenames").asOpt[String]
      val lastname = request.body.\("lastname").asOpt[String]
      val dateofbirth = request.body.\("dateofbirth").asOpt[String]
      debug(CLASS_NAME, "details", s"firstName $firstname, lastName: $lastname, dateOfBirth: $dateofbirth")

      (firstname, lastname, dateofbirth) match {
        case (Some(f), Some(l), Some(d)) =>
          groConnector.get(f, l, d).flatMap[Result](
            handle("getDetails").apply(_)
          )
        case _ =>
          respond(BadRequest)
      }
  }

}
