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

package uk.gov.hmrc.brm.controllers

import java.time.LocalDate

import org.joda.time.format.DateTimeFormat
import play.api.Logger
import play.api.libs.json.{JsObject, JsArray, Json}
import play.api.mvc.{Action, Request, Result}
import uk.gov.hmrc.brm.connectors._
import uk.gov.hmrc.brm.utils.BrmLogger._
import uk.gov.hmrc.brm.utils.KeyHolder
import uk.gov.hmrc.play.http.{Upstream4xxResponse, Upstream5xxResponse}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}


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

  def handleException(method: String, reference: String): PartialFunction[BirthResponse, Future[Result]] = {
    case BirthErrorResponse(Upstream4xxResponse(message, NOT_FOUND, _, _)) =>
      info(CLASS_NAME, "handleException", s"[$method] NotFound: no record found")
      respond(NotFound(ErrorResponses.NOT_FOUND))
    case BirthErrorResponse(Upstream4xxResponse(message, BAD_REQUEST, _, _)) =>
      warn(CLASS_NAME, "handleException", s"[$method] BadRequest: $message")
      respond(BadGateway(ErrorResponses.BAD_REQUEST))
    case BirthErrorResponse(Upstream5xxResponse(message, BAD_GATEWAY, _)) =>
      error(CLASS_NAME, "handleException", s"[$method] BadGateway: $message")
      respond(BadGateway(ErrorResponses.BAD_REQUEST))
    case BirthErrorResponse(Upstream5xxResponse(message, GATEWAY_TIMEOUT, _)) =>
      error(CLASS_NAME, "handleException", s"[$method] GatewayTimeout: $message")
      respond(GatewayTimeout(ErrorResponses.GATEWAY_TIMEOUT))
    case BirthErrorResponse(Upstream5xxResponse(message, INTERNAL_SERVER_ERROR, _)) =>
        error(CLASS_NAME, "handleException",s"[$method] InternalServerError: Connection to GRO is down")
        respond(InternalServerError(ErrorResponses.CONNECTION_DOWN))
    case BirthErrorResponse(e) =>
      error(CLASS_NAME, "handleException",s"[$method] InternalServerError: ${e.getMessage}")
      respond(InternalServerError)
  }

  private def setKey(request : Request[_]) = {
    val brmKey = request.headers.get(BRM_KEY).getOrElse("no-key")
    KeyHolder.setKey(brmKey)
  }

  def success(method: String): PartialFunction[BirthResponse, Future[Result]] = {
    case BirthSuccessResponse(js) =>
      val count = if(js.isInstanceOf[JsArray]) js.as[JsArray].value.length else js.asOpt[JsObject].fold(0)(x => 1)
      info(CLASS_NAME, s"$method", s"success: $count record(s) found")
      respond(Ok(js))
  }

  def reference(reference: String) = Action.async {
    implicit request =>
      setKey(request)

      Logger.debug(s"connector: ${groConnector.get(reference)}")

      groConnector.get(reference).flatMap[Result](
        handleException("getReference", reference)
        orElse success("getReference")
      )
  }

  def details(firstName: String, lastName: String, dateOfBirth: String) = Action.async {
    implicit request =>
      setKey(request)
      groConnector.get(firstName, lastName, dateOfBirth).flatMap[Result](
        handleException("getDetails", dateOfBirth)
        orElse success("getDetails")
      )
  }

}
