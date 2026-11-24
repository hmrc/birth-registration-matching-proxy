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
import ch.qos.logback.classic.spi.ILoggingEvent
import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.brm.TestFixture
import uk.gov.hmrc.brm.metrics.BRMMetrics
import uk.gov.hmrc.brm.utils.{BrmLogger, KeyHolder}
import uk.gov.hmrc.http.{BadGatewayException, GatewayTimeoutException, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing

import java.io.IOException
import scala.concurrent.Future

class ResponseHandlerSpec extends TestFixture with ScalaFutures with LogCapturing {

  private val responseHandler = new ResponseHandler

  private val body = Json.obj("some" -> "payload").toString()

  private val parse: HttpResponse => BirthResponse = response => BirthSuccessResponse(Json.parse(response.body))

  private val brmKey = "response-handler-spec"
  KeyHolder.setKey(brmKey)

  private def handle(status: Int): BirthResponse = {
    given metrics: BRMMetrics = new BRMMetrics
    responseHandler.handle(Future.successful(HttpResponse.apply(status, body)))(parse, metrics).futureValue
  }

  private def handleFailure(exception: Exception): Future[BirthResponse] = {
    given metrics: BRMMetrics = new BRMMetrics
    responseHandler.handle(Future.failed(exception))(parse, metrics)
  }

  private def handlerLogs(logs: List[ILoggingEvent], level: Level): List[String] =
    logs.collect {
      case event if event.getLevel == level && event.getFormattedMessage.contains("[ResponseHandler]") =>
        event.getFormattedMessage
    }

  private def infoLine(message: String): String = s"[BRM-Key:$brmKey], [ResponseHandler][handle] : $message"

  private def warnOrErrorLine(message: String): String = s"[BRM-Key:$brmKey],[ResponseHandler][handle] : $message"

  "ResponseHandler.handle" when {

    "the request returns a response" should {

      "apply the supplied parser for a 200 response" in
        withCaptureOfLoggingFrom(BrmLogger) { logs =>
          val result = handle(Status.OK)

          result shouldBe BirthSuccessResponse(Json.parse(body))

          handlerLogs(logs, Level.INFO) shouldBe List(infoLine("response received"), infoLine("[200] Success"))

          handlerLogs(logs, Level.WARN)  shouldBe Nil
          handlerLogs(logs, Level.ERROR) shouldBe Nil
        }

      "return a Birth404ErrorResponse for a 404 response" in
        withCaptureOfLoggingFrom(BrmLogger) { logs =>
          val result = handle(Status.NOT_FOUND)

          result shouldBe Birth404ErrorResponse(UpstreamErrorResponse("[ErrorHandler][404]", 404, 404))

          handlerLogs(logs, Level.INFO) shouldBe List(
            infoLine("response received"),
            infoLine("[404] 404 status response from LEV, no match")
          )
        }

      "return a BirthErrorResponse with the status of a 4xx response" in
        withCaptureOfLoggingFrom(BrmLogger) { logs =>
          val result = handle(Status.BAD_REQUEST)

          result shouldBe BirthErrorResponse(UpstreamErrorResponse("[ErrorHandler][400]", 400, 400))

          handlerLogs(logs, Level.INFO) shouldBe List(
            infoLine("response received"),
            infoLine("[400] 4xx status response found")
          )
        }

      "return a BirthErrorResponse with the status of a 5xx response, and log a warning" in
        withCaptureOfLoggingFrom(BrmLogger) { logs =>
          val result = handle(Status.INTERNAL_SERVER_ERROR)

          result shouldBe BirthErrorResponse(UpstreamErrorResponse("[ErrorHandler][500]", 500, 500))

          handlerLogs(logs, Level.INFO) shouldBe List(infoLine("response received"))

          handlerLogs(logs, Level.WARN) shouldBe List(
            warnOrErrorLine("[500] 5xx status response found,  InternalServerError")
          )
        }

      "return a BirthErrorResponse for a status outside 200, 4xx and 5xx, and log an error" in
        withCaptureOfLoggingFrom(BrmLogger) { logs =>
          val result = handle(Status.NO_CONTENT)

          result shouldBe BirthErrorResponse(UpstreamErrorResponse("[ErrorHandler][204]", 204, 204))

          handlerLogs(logs, Level.INFO) shouldBe List(infoLine("response received"))

          handlerLogs(logs, Level.ERROR) shouldBe List(warnOrErrorLine("[204] Unexpected response found"))
        }

      "record the response status against the metrics registry" in {
        given metrics: BRMMetrics = new BRMMetrics

        val counter = metrics.defaultRegistry.counter(s"${metrics.prefix}-http-response-code-${Status.OK}")
        val before  = counter.getCount

        responseHandler.handle(Future.successful(HttpResponse.apply(Status.OK, body)))(parse, metrics).futureValue

        counter.getCount shouldBe before + 1
      }
    }

    "the request fails before a response arrives" should {

      "return a 504 BirthErrorResponse for a GatewayTimeoutException, and log the detail" in {
        val timeout = new GatewayTimeoutException("GET of 'http://gro/record' timed out with message 'timeout'")

        withCaptureOfLoggingFrom(BrmLogger) { logs =>
          val result = handleFailure(timeout).futureValue

          result shouldBe BirthErrorResponse(UpstreamErrorResponse("[ErrorHandler][504] request timed out", 504, 504))

          handlerLogs(logs, Level.WARN) shouldBe List(
            warnOrErrorLine("[504] request timed out: GET of 'http://gro/record' timed out with message 'timeout'")
          )

          handlerLogs(logs, Level.INFO) shouldBe Nil
        }
      }

      "return a 502 BirthErrorResponse for a BadGatewayException, and log the detail" in {
        val refused = new BadGatewayException("GET of 'http://gro/record' failed. Caused by: 'Connection refused'")

        withCaptureOfLoggingFrom(BrmLogger) { logs =>
          val result = handleFailure(refused).futureValue

          result shouldBe BirthErrorResponse(UpstreamErrorResponse("[ErrorHandler][502] connection failed", 502, 502))

          handlerLogs(logs, Level.WARN) shouldBe List(
            warnOrErrorLine(
              "[502] connection failed: GET of 'http://gro/record' failed. Caused by: 'Connection refused'"
            )
          )

          handlerLogs(logs, Level.INFO) shouldBe Nil
        }
      }

      "return a 500 BirthErrorResponse for any other exception, and log it as an error" in {
        val connectionReset = new IOException("Connection reset by peer")

        withCaptureOfLoggingFrom(BrmLogger) { logs =>
          val result = handleFailure(connectionReset).futureValue

          result shouldBe BirthErrorResponse(UpstreamErrorResponse("[ErrorHandler][500] request failed", 500, 500))

          handlerLogs(logs, Level.ERROR) shouldBe List(
            warnOrErrorLine("[500] request failed: java.io.IOException: Connection reset by peer")
          )

          handlerLogs(logs, Level.INFO) shouldBe Nil
          handlerLogs(logs, Level.WARN) shouldBe Nil
        }
      }
    }

    "the parser throws" should {

      "return a 500 BirthErrorResponse, and log it as an error" in {
        val throwingParse: HttpResponse => BirthResponse = _ => throw new IllegalStateException("parser failed")

        withCaptureOfLoggingFrom(BrmLogger) { logs =>
          given metrics: BRMMetrics = new BRMMetrics

          val result = responseHandler
            .handle(Future.successful(HttpResponse.apply(Status.OK, body)))(throwingParse, metrics)
            .futureValue

          result shouldBe BirthErrorResponse(UpstreamErrorResponse("[ErrorHandler][500] request failed", 500, 500))

          handlerLogs(logs, Level.INFO) shouldBe List(infoLine("response received"), infoLine("[200] Success"))

          handlerLogs(logs, Level.ERROR) shouldBe List(
            warnOrErrorLine("[500] request failed: java.lang.IllegalStateException: parser failed")
          )
        }
      }
    }
  }

}
