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

package uk.gov.hmrc.brm.utils

import play.api.http.Status
import play.api.libs.json.JsValue
import uk.gov.hmrc.brm.connectors.Encoder
import uk.gov.hmrc.http.HttpResponse

trait BaseUnitSpec {

  val headers = Map(
    "Authorization"              -> Seq(s"Bearer something"),
    "X-Auth-Downstream-Username" -> Seq("hmrc")
  )

  def authSuccessResponse(authRecord: JsValue): HttpResponse =
    HttpResponse.apply(
      Status.OK,
      authRecord.toString()
    )

  def eventResponseWithStatus(status: Int, eventResponse: String): HttpResponse =
    HttpResponse.apply(status, eventResponse)

  def eventSuccessResponse(eventResponse: JsValue): HttpResponse =
    HttpResponse.apply(Status.OK, eventResponse.toString(), headers = headers)

  def getUrlEncodeString(firstName: String, lastname: String, dateOfBirth: String): String = {
    val details = Map(
      "forenames"   -> firstName,
      "lastname"    -> lastname,
      "dateofbirth" -> dateOfBirth
    )
    Encoder.encode(details)
  }

  def getEntireUrl(path: String, firstName: String, lastName: String, dateOfBirth: String): String =
    path + getUrlEncodeString(firstName, lastName, dateOfBirth)

}
