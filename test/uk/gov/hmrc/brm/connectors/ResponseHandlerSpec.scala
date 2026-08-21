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

import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.brm.TestFixture
import uk.gov.hmrc.brm.metrics.BRMMetrics
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}

import scala.concurrent.Future

class ResponseHandlerSpec extends TestFixture with ScalaFutures {

  private val responseHandler = new ResponseHandler

  private val body = Json.obj("some" -> "payload").toString()

  private val parse: HttpResponse => BirthResponse = response => BirthSuccessResponse(Json.parse(response.body))

  private def handle(status: Int): BirthResponse = {
    given metrics: BRMMetrics = new BRMMetrics
    responseHandler.handle(Future.successful(HttpResponse.apply(status, body)))(parse, metrics).futureValue
  }

  private def causeOf(response: BirthResponse): UpstreamErrorResponse = response match {
    case BirthErrorResponse(cause: UpstreamErrorResponse)    => cause
    case Birth404ErrorResponse(cause: UpstreamErrorResponse) => cause
    case other                                               => fail(s"expected an error response but got $other")
  }

  "ResponseHandler.handle" should {

    "apply the supplied parser for a 200 response" in {
      handle(Status.OK) shouldBe BirthSuccessResponse(Json.parse(body))
    }

    "return a Birth404ErrorResponse for a 404 response" in {
      val result = handle(Status.NOT_FOUND)

      result                     shouldBe a[Birth404ErrorResponse]
      causeOf(result).statusCode shouldBe Status.NOT_FOUND
    }

    "return a BirthErrorResponse for a 4xx response" in {
      val result = handle(Status.BAD_REQUEST)

      result                     shouldBe a[BirthErrorResponse]
      causeOf(result).statusCode shouldBe Status.BAD_REQUEST
    }

    "return a BirthErrorResponse for a 5xx response" in {
      val result = handle(Status.INTERNAL_SERVER_ERROR)

      result                     shouldBe a[BirthErrorResponse]
      causeOf(result).statusCode shouldBe Status.INTERNAL_SERVER_ERROR
    }

    "return a BirthErrorResponse for an unexpected status outside the 4xx and 5xx ranges" in {
      val result = handle(Status.FOUND)

      result                     shouldBe a[BirthErrorResponse]
      causeOf(result).statusCode shouldBe Status.FOUND
    }

    "record the response status against the metrics registry" in {
      given metrics: BRMMetrics = new BRMMetrics

      responseHandler
        .handle(Future.successful(HttpResponse.apply(Status.OK, body)))(parse, metrics)
        .futureValue

      metrics.defaultRegistry
        .counter(s"${metrics.prefix}-http-response-code-${Status.OK}")
        .getCount should be > 0L
    }

  }

}
