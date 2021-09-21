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

import play.api.http.Status
import uk.gov.hmrc.brm.metrics.BRMMetrics
import uk.gov.hmrc.brm.utils.BrmLogger._
import uk.gov.hmrc.http.HttpResponse

import scala.concurrent.{ExecutionContext, Future}

object ResponseHandler extends ResponseHandler

class ResponseHandler {

  private val CLASS_NAME: String = this.getClass.getSimpleName

  def handle(futureResponse: Future[HttpResponse])(f: HttpResponse => BirthResponse, metrics: BRMMetrics)
            (implicit ec: ExecutionContext): Future[BirthResponse] =
    futureResponse.map { response =>
      info(CLASS_NAME, "handle", s"response received")

      debug("BirthConnector","getChildByReference",s"HttpResponse: $response, BODY: ${response.body}")

      metrics.httpResponseCodeStatus(response.status)

      response.status match {
        case Status.OK =>
          info(CLASS_NAME, "handle", s"[200] Success")
          f(response)
        case Status.NOT_FOUND =>
          info(CLASS_NAME, "handle", s"[404] 404 status response from LEV, no match")
          ErrorHandler.errorWithNotFound(response)
        case status4xx if status4xx >= 400 && status4xx <= 499 =>
          info(CLASS_NAME, "handle", s"[$status4xx] 4xx status response found")
          ErrorHandler.error(response)
        case status5xx if status5xx >= 500 && status5xx <= 599 =>
          warn(CLASS_NAME, "handle", s"[$status5xx] 5xx status response found,  InternalServerError")
          ErrorHandler.error(response)
        case status =>
          error(CLASS_NAME, "handle", s"[$status] Unexpected response found")
          ErrorHandler.error(response)
      }
    }

}
