/*
 * Copyright 2017 HM Revenue & Customs
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

import play.api.libs.json.Json

/**
 * Created by adamconder on 10/11/2016.
 */
object ErrorResponses {

  private def error(code: String, message: String) = Json.parse(
    s"""
       |{
       |  "code": "$code",
       |  "description": "$message"
       |}
     """.stripMargin)

  val CONNECTION_DOWN = error("GRO_CONNECTION_DOWN", "Connection to GRO is down")
  val BAD_REQUEST = error("BAD_REQUEST", "Invalid payload provided")
  val TEAPOT = error("TEAPOT", "Invalid argument sent to GRO")
  val NOT_FOUND = error("NOT_FOUND", "Resource not found")
  val GATEWAY_TIMEOUT = error("GATEWAY_TIMEOUT", "Connection to GRO timed out")
  val CERTIFICATE_INVALID = error("INVALID_CERTIFICATE", "TLS certificate was either not provided or was invalid")
  val UNKNOWN_ERROR = error("UNKNOWN_ERROR", "An unknown exception has been thrown")

}
