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

import play.api.http.Status._
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}


class ErrorHandler {

  def error(response: HttpResponse): BirthErrorResponse = {
    BirthErrorResponse(UpstreamErrorResponse(
      s"[ErrorHandler][${response.status.toString}]",
      response.status,
      response.status)
    )
  }

  def errorWithNotFound(response: HttpResponse): Birth404ErrorResponse = {
    Birth404ErrorResponse(UpstreamErrorResponse(
      s"[ErrorHandler][${response.status.toString}]",
      response.status,
      response.status)
    )
  }

  def error(message: String, status: Int = INTERNAL_SERVER_ERROR): BirthErrorResponse = {
    BirthErrorResponse(
      UpstreamErrorResponse(
        s"[ErrorHandler][$status] $message",
        status,
        status)
    )
  }

}

object ErrorHandler extends ErrorHandler
