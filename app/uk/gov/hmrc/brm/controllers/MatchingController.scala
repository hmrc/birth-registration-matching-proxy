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

import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{Action, Result}
import uk.gov.hmrc.brm.connectors._
import uk.gov.hmrc.play.http.{JsValidationException, Upstream4xxResponse, Upstream5xxResponse}
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.brm.utils.BrmLogger._

import scala.concurrent.Future

/**
 * Created by adamconder on 28/07/2016.
 */

object MatchingController extends MatchingController {
  override val groConnector = GROEnglandAndWalesConnector
}

trait MatchingController extends BaseController {

  import scala.concurrent.ExecutionContext.Implicits.global

  val groConnector: BirthConnector

  private def respond(response: Result): Future[Result] = {
    Future.successful(response.as("application/json"))
  }

  def handleException(method: String, reference: String): PartialFunction[BirthResponse, Future[Result]] = {
    case BirthErrorResponse(Upstream4xxResponse(message, NOT_FOUND, _, _)) =>
      info(this, "handleException",s"NotFound: no record found for $reference")
      respond(NotFound(s"$reference"))
    case BirthErrorResponse(Upstream4xxResponse(message, BAD_REQUEST, _, _)) =>
      Logger.warn(s"[MatchingController][GROConnector][$method] BadRequest: $message")
      respond(BadGateway("BadRequest returned from GRO"))
    case BirthErrorResponse(Upstream5xxResponse(message, BAD_GATEWAY, _)) =>
      Logger.warn(s"[MatchingController][GROConnector][$method] BadGateway: $message")
      respond(BadGateway("BadGateway returned from GRO"))
    case BirthErrorResponse(Upstream5xxResponse(message, GATEWAY_TIMEOUT, _)) =>
      Logger.warn(s"[MatchingController][GROConnector][$method][Timeout] GatewayTimeout: $message")
      respond(GatewayTimeout)
    case BirthErrorResponse(Upstream5xxResponse(message, INTERNAL_SERVER_ERROR, _)) =>
      error(this, "handleException",s"InternalServerError: $message")
      respond(InternalServerError("Connection to GRO is down"))
    case BirthErrorResponse(_) =>
      warn(this, "handleException",s"InternalServerError: Exception")
      respond(InternalServerError)

  }

  def reference(reference: String) = Action.async {

    implicit request =>
      val success: PartialFunction[BirthResponse, Future[Result]] = {
        case BirthSuccessResponse(js) =>
          respond(Ok(js))
      }

      groConnector.getReference(reference).flatMap[Result](handleException("getReference", reference) orElse success)

  }
}
