/*
 * Copyright 2019 HM Revenue & Customs
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

import java.net.URL

import play.api.libs.json.JsValue
import uk.co.bigbeeconsultants.http.header.{Headers, MediaType}
import uk.co.bigbeeconsultants.http.request.Request
import uk.co.bigbeeconsultants.http.response.{Response, Status}
import uk.gov.hmrc.play.test.UnitSpec

/**
  * Created by user on 16/05/17.
  */
trait BaseUnitSpec extends UnitSpec {

  val headers = Map(
    "Authorization" -> s"Bearer something",
    "X-Auth-Downstream-Username" -> "hmrc"
  )

  def authSuccessResponse(authRecord :JsValue ):Response = {
    Response.apply(Request.post(new URL("http://localhost:8099"), None),
      Status.S200_OK,
      MediaType.APPLICATION_JSON,
      authRecord.toString())
  }

  def eventResponseWithStatus(status: Status, eventResponse :String):Response = {
    Response.apply(Request.get(new URL("http://localhost:8099"), headers = Headers.apply(headers)), status, MediaType.APPLICATION_JSON, eventResponse)
  }


  def eventSuccessResponse(eventResponse :JsValue ):Response = {
    Response.apply(Request.get(new URL("http://localhost:8099/v0/events/birth"),
      headers = Headers.apply(headers)), Status.S200_OK, MediaType.APPLICATION_JSON, eventResponse.toString())
  }



}
