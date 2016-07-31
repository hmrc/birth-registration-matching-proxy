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
import uk.gov.hmrc.play.http.{Upstream4xxResponse, Upstream5xxResponse}
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
      Logger.info(s"params: $params")
      groConnector.getChildDetails(params) map {
        response =>
          respond(Ok(response))
      } recover {
        case e : Upstream4xxResponse =>
          respond(BadRequest(e.message))
        case e : Upstream5xxResponse =>
          respond(InternalServerError(e.message))
      }
  }

  def reference(reference : String) = Action.async {
    implicit request =>
      Logger.debug(s"reference: $reference")
      groConnector.getReference(reference) map {
        response =>
          respond(Ok(response))
      } recover {
        case e : Upstream4xxResponse =>
          respond(BadRequest(e.message))
        case e : Upstream5xxResponse =>
          Logger.error(s"[MatchingController][GROConnector][getReference] InternalServerError: ${e.message}")
          respond(InternalServerError(e.message))
      }
  }

}
