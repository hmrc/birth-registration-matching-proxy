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

import play.api.mvc.{Action, Request, Result}
import uk.gov.hmrc.brm.connectors._
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

  def handleException(method: String, reference: String): PartialFunction[BirthResponse, Future[Result]] = {
    case BirthErrorResponse(Upstream4xxResponse(message, NOT_FOUND, _, _)) =>
      info(CLASS_NAME, "handleException", s"NotFound: no record found")
      respond(NotFound(s"$reference"))
    case BirthErrorResponse(Upstream4xxResponse(message, BAD_REQUEST, _, _)) =>
      warn(CLASS_NAME, "handleException",s"[$method] BadRequest: $message")
      respond(BadGateway("BadRequest returned from GRO"))
    case BirthErrorResponse(Upstream5xxResponse(message, BAD_GATEWAY, _)) =>
      error(CLASS_NAME, "handleException",s"[$method] BadGateway: $message")
      respond(BadGateway("BadGateway returned from GRO"))
    case BirthErrorResponse(Upstream5xxResponse(message, GATEWAY_TIMEOUT, _)) =>
      error(CLASS_NAME, "handleException",s"[MatchingController][GROConnector][$method][Timeout] GatewayTimeout: $message")
      respond(GatewayTimeout)
    case BirthErrorResponse(Upstream5xxResponse(message, INTERNAL_SERVER_ERROR, _)) =>
      error(CLASS_NAME, "handleException",s"InternalServerError: $message")
      respond(InternalServerError("Connection to GRO is down"))
    case BirthErrorResponse(_) =>
      error(CLASS_NAME, "handleException",s"InternalServerError: Exception")
      respond(InternalServerError)
  }

  private def setKey(request : Request[_]) = {
    val brmKey = request.headers.get(BRM_KEY).getOrElse("no-key")
    KeyHolder.setKey(brmKey)
  }

  def success: PartialFunction[BirthResponse, Future[Result]] = {
    case BirthSuccessResponse(js) =>
      info(CLASS_NAME, "getReference", s"record(s) found")
      debug(CLASS_NAME, "reference", s"success.")
      respond(Ok(js))
  }

  def reference(reference: String) = Action.async {
    implicit request =>
      setKey(request)
      groConnector.getReference(reference).flatMap[Result](
        handleException("getReference", reference)
        orElse success
      )
  }

  def details(firstName: String, lastName: String, dateOfBirth: String) = Action.async {
    implicit request =>
      setKey(request)

      Future.successful(Ok(""))
  }

}
