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

package uk.gov.hmrc.brm.connectors

import ch.qos.logback.classic.Level
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.brm.TestFixture
import uk.gov.hmrc.brm.utils.{BrmLogger, KeyHolder}
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing

class ResponseParserSpec extends TestFixture with LogCapturing {

  private val responseParser = ResponseParser

  private val brmKey = "response-parser-spec"
  KeyHolder.setKey(brmKey)

  "ResponseParser.parse" must {

    "return a BirthSuccessResponse when the body is valid json" in
      withCaptureOfLoggingFrom(BrmLogger) { logs =>
        val result = responseParser.parse(HttpResponse(Status.OK, """{"this": "isJson"}"""))

        result shouldBe BirthSuccessResponse(Json.obj("this" -> "isJson"))

        val infoLogs  = logs.filter(_.getLevel == Level.INFO).map(_.getFormattedMessage)
        val errorLogs = logs.filter(_.getLevel == Level.ERROR).map(_.getFormattedMessage)

        infoLogs  shouldBe List(s"[BRM-Key:$brmKey], [ResponseParser][parse] : parsing json")
        errorLogs shouldBe Nil
      }

    "return a 500 BirthErrorResponse when the body is not valid json" in
      withCaptureOfLoggingFrom(BrmLogger) { logs =>
        val result = responseParser.parse(HttpResponse(Status.OK, "thisIsNotJson"))

        result shouldBe BirthErrorResponse(UpstreamErrorResponse("[ErrorHandler][500]", 500, 500))

        val infoLogs  = logs.filter(_.getLevel == Level.INFO).map(_.getFormattedMessage)
        val errorLogs = logs.filter(_.getLevel == Level.ERROR).map(_.getFormattedMessage)

        infoLogs shouldBe List(s"[BRM-Key:$brmKey], [ResponseParser][parse] : parsing json")

        errorLogs should have size 1

        errorLogs.head should startWith(
          s"[BRM-Key:$brmKey],[ResponseParser][parse] : unable to parse json: " +
            "com.fasterxml.jackson.core.JsonParseException: Unrecognized token 'thisIsNotJson'"
        )
      }
  }

}
