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

import play.api.libs.json.Json
import uk.co.bigbeeconsultants.http.response.{Response, Status}
import uk.gov.hmrc.brm.utils.BrmLogger._

/**
 * Created by adamconder on 14/11/2016.
 */
object ResponseParser {

  private val CLASS_NAME : String = this.getClass.getCanonicalName

  def parse(response: Response) : BirthResponse = {
    info(CLASS_NAME, "parse", "parsing json")
    try {
      val bodyText = response.body.asString
      val json = Json.parse(bodyText)

      BirthSuccessResponse(json)
    } catch {
      case e: Exception =>
        warn(CLASS_NAME, "parse", "unable to parse json")
        // from 200 t0 500 or override somehow?
        val error = response.copy(status = Status.S500_InternalServerError)
        ErrorHandler.error(error)
    }
  }

}
