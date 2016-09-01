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
import play.api.mvc.{Result, Action}
import uk.gov.hmrc.brm.connectors.{GROEnglandAndWalesConnector, BirthConnector}
import uk.gov.hmrc.play.http.{JsValidationException, Upstream4xxResponse, Upstream5xxResponse}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

/**
 * Created by adamconder on 28/07/2016.
 */

object MatchingController extends MatchingController {
  override val groConnector = GROEnglandAndWalesConnector
}

trait MatchingController extends BaseController {

  import scala.concurrent.ExecutionContext.Implicits.global

  val groConnector : BirthConnector

  private def respond(response : Result) = {
    response.as("application/json")
  }

  def handleException(method: String) : PartialFunction[Throwable, Result] = {
    case e : JsValidationException =>
      Logger.warn(s"[MatchingController][GROConnector][$method] JsValidationException")
      respond(InternalServerError("Invalid json returned from GRO"))
    case Upstream4xxResponse(message, BAD_REQUEST, _, _) =>
      Logger.warn(s"[MatchingController][GROConnector][$method] BadRequest: $message")
      respond(BadRequest("BadRequest returned from GRO"))
    case Upstream5xxResponse(message, INTERNAL_SERVER_ERROR, _) =>
      Logger.error(s"[MatchingController][GROConnector][$method] InternalServerError: $message")
      respond(InternalServerError("Connection to GRO is down"))
    case _ =>
      Logger.error(s"[MatchingController][GROConnector][$method] InternalServerError")
      respond(InternalServerError("Internal server error"))
  }

  def details = Action.async {
    implicit request =>
      val (firstName, lastName, dob) = (
        request.getQueryString("firstName").getOrElse(""),
        request.getQueryString("lastName").getOrElse(""),
        request.getQueryString("dateOfBirth").getOrElse("")
        )
      val params = Map(
        "firstName" -> firstName,
        "lastName" -> lastName,
        "dateOfBirth" -> dob
      )

      groConnector.getChildDetails(params) map {
        response =>
          respond(Ok(response))
      } recover handleException("getChildDetails")
  }

  def reference(reference : String) = Action.async {
    implicit request =>
      groConnector.getReference(reference) map {
        response =>
          respond(Ok(response))
      } recover handleException("getReference")
  }

}
