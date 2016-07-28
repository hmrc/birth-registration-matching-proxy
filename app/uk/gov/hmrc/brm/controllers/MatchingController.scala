package uk.gov.hmrc.brm.controllers

import play.api.Logger
import play.api.mvc.Action
import uk.gov.hmrc.brm.connectors.{GROEnglandAndWalesConnector, BirthConnector}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

/**
 * Created by adamconder on 28/07/2016.
 */

object MatchingController extends MatchingController {
  override val connector = GROEnglandAndWalesConnector
}

trait MatchingController extends BaseController {

  import scala.concurrent.ExecutionContext.global

  val connector : BirthConnector

  def details() = Action.async(parse.json) {
    implicit request =>
      val params = request.queryString
      Logger.debug(s"query parameters: $params")
      Future.successful(Ok(""))
  }

  def reference(reference : String) = Action.async(parse.json) {
    implicit request =>
      Logger.debug(s"reference: $reference")
      Future.successful(Ok(""))
  }

}
