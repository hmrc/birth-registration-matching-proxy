/*
 * Copyright 2018 HM Revenue & Customs
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

import play.api.http.Status._
import uk.co.bigbeeconsultants.http.response.Response
import uk.gov.hmrc.brm.utils.BrmLogger._
import uk.gov.hmrc.play.http.{Upstream4xxResponse, Upstream5xxResponse}

/**
 * Created by adamconder on 14/11/2016.
 */
object ErrorHandler {

  def wait(delay: Int) = {
    val tick = System.currentTimeMillis() + delay
    info("ErrorHandler", "wait", "waiting before making next request")
    do {} while (System.currentTimeMillis() < tick)
  }


  def error(response: Response) = {
    info("ErrorHandler", "error", s"isServerError: ${response.status.isServerError}")

    val upstream = if (response.status.isServerError) {
      Upstream5xxResponse(
        s"[ErrorHandler][${response.status.toString}]",
        response.status.code,
        response.status.code)
    } else {
      Upstream4xxResponse(
        s"[ErrorHandler][${response.status.toString}]",
        response.status.code,
        response.status.code)
    }

    BirthErrorResponse(upstream)
  }

  def error(message : String) = {
    BirthErrorResponse(
      Upstream5xxResponse(
        s"[ErrorHandler][InternalServerError][$message]",
        INTERNAL_SERVER_ERROR,
        INTERNAL_SERVER_ERROR)
    )
  }

}
