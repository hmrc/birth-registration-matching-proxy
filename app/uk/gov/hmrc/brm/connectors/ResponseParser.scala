/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.brm.utils.BrmLogger
import uk.gov.hmrc.brm.utils.BrmLogger._
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.HttpResponse.unapply

import scala.util.{Failure, Success, Try}

class ResponseParser(errorHandler: ErrorHandler) {

  private val CLASS_NAME: String = this.getClass.getSimpleName

  def parse(response: HttpResponse): BirthResponse = {
    info(CLASS_NAME, "parse", "parsing json")
    Try {
      val bodyText = response.body
      val json     = Json.parse(bodyText)

      BirthSuccessResponse(json)
    } match {
      case Success(successResponse) => successResponse
      case Failure(exception)       =>
        BrmLogger.error(CLASS_NAME, "parse", s"unable to parse json: $exception")
        val (_, body, headers): (Int, String, Map[String, Seq[String]]) = unapply(response).get
        val error                                                       = HttpResponse.apply(Status.INTERNAL_SERVER_ERROR, body, headers)
        errorHandler.error(error)
    }
  }

}

object ResponseParser extends ResponseParser(ErrorHandler)
