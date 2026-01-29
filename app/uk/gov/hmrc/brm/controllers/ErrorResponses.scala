/*
 * Copyright 2026 HM Revenue & Customs
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

import play.api.libs.json.{JsValue, Json}

object ErrorResponses {

  private def error(code: String, message: String) = Json.parse(s"""
       |{
       |  "code": "$code",
       |  "message": "$message"
       |}
     """.stripMargin)

  val CONNECTION_DOWN: JsValue     = error("GRO_CONNECTION_DOWN", "Connection to GRO is down")
  val BAD_REQUEST: JsValue         = error("BAD_REQUEST", "Invalid payload provided")
  val TEAPOT: JsValue              = error("TEAPOT", "Invalid argument sent to GRO")
  val NOT_FOUND: JsValue           = error("NOT_FOUND", "Resource not found")
  val GATEWAY_TIMEOUT: JsValue     = error("GATEWAY_TIMEOUT", "Connection to GRO timed out")

  val CERTIFICATE_INVALID: JsValue =
    error("INVALID_CERTIFICATE", "TLS certificate was either not provided or was invalid")

  val UNKNOWN_ERROR: JsValue       = error("UNKNOWN_ERROR", "An unknown exception has been thrown")
  val BAD_GATEWAY: JsValue         = error("BAD_GATEWAY", "GRO returned bad gateway")

}
