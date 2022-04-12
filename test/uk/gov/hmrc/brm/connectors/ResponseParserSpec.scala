/*
 * Copyright 2022 HM Revenue & Customs
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

import org.mockito.Mockito._
import org.mockito.ArgumentMatchers.{any, anyInt, anyString, refEq}
import play.api.http.Status
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.brm.TestFixture
import uk.gov.hmrc.http.HttpResponse

class ResponseParserSpec extends TestFixture {

  val mockHandler: ErrorHandler = mock[ErrorHandler]

  val responseParser: ResponseParser = new ResponseParser(mockHandler)

  "ResponseParser" must {
    "parse correctly when the body is valid Json" in {
      val response: HttpResponse = HttpResponse.apply(Status.OK,
        """
          |{"this": "isJson"}
          |""".stripMargin)

      val parsed: JsValue = Json.obj("this" -> "isJson")
      val success = responseParser.parse(response)

      success shouldBe BirthSuccessResponse(parsed)
      verify(mockHandler, never()).error(anyString(), anyInt())
      verify(mockHandler, never()).error(any[HttpResponse])
    }

    "return a BirthErrorResponse when the body is not valid Json" in {
      val response: HttpResponse = HttpResponse.apply(Status.OK, "thisIsNotJson")
      val returnException: BirthErrorResponse = BirthErrorResponse(new Exception)

      when(mockHandler.error(any[HttpResponse])).thenReturn(returnException)


      responseParser.parse(response) shouldBe returnException
      verify(mockHandler, times(1))
        .error(refEq(HttpResponse.apply(Status.INTERNAL_SERVER_ERROR, "thisIsNotJson", Map.empty[String, Seq[String]])))
    }

  }
}
