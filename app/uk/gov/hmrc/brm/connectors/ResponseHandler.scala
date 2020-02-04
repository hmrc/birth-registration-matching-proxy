/*
 * Copyright 2020 HM Revenue & Customs
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

import uk.co.bigbeeconsultants.http.response.{Response, Status}
import uk.gov.hmrc.brm.connectors.ConnectorTypes.Attempts
import uk.gov.hmrc.brm.metrics.BRMMetrics
import uk.gov.hmrc.brm.utils.BrmLogger._

/**
 * Created by adamconder on 14/11/2016.
 */
object ResponseHandler {

  private val CLASS_NAME : String = this.getClass.getCanonicalName

  def handle(response: Response, attempts : Attempts)(f : Response => BirthResponse, metrics : BRMMetrics) = {

    debug(CLASS_NAME, "handle",s"$response")
    info(CLASS_NAME, "handle", s"response received after $attempts attempt(s)")

    metrics.httpResponseCodeStatus(response.status.code)

    response.status match {
      case Status.S200_OK =>
        info(CLASS_NAME, "handleResponse", s"[200] Success, attempt $attempts")
        (f(response), attempts)
      case e @ x if x.isClientError =>
        info(CLASS_NAME, "handleResponse", s"[${e.code}}}] ${e.category}: attempt $attempts")
        (ErrorHandler.error(response), attempts)
      case e : Status =>
        error(CLASS_NAME, "handleResponse", s"[${e.category}}] InternalServerError: attempt $attempts")
        (ErrorHandler.error(response), attempts)
    }
  }

}
