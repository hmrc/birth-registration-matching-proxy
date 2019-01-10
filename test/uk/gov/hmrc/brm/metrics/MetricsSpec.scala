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

package uk.gov.hmrc.brm.metrics

import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

class MetricsSpec extends UnitSpec with WithFakeApplication {

  "Metrics" when {

    "making requests to authentication" should {

      "measure response time for authentication request" in {
        val startTimer = GROReferenceMetrics.startTimer()
        GROReferenceMetrics.endTimer(startTimer, "authentication-timer")
        GROReferenceMetrics.metrics.defaultRegistry.timer(s"${GROReferenceMetrics.prefix}-authentication-timer").getCount should not be 0
      }

    }

    "making requests to reference" should {

      "initialise Metrics instance" in {
        GROReferenceMetrics shouldBe a[BRMMetrics]
        GROReferenceMetrics.prefix shouldBe "gro"
      }

      "measure response time for match request" in {
        val startTimer = GROReferenceMetrics.startTimer()
        GROReferenceMetrics.endTimer(startTimer, "reference-match-timer")
        GROReferenceMetrics.metrics.defaultRegistry.timer("gro-reference-match-timer").getCount should not be 0
      }

      "increment count for http response code 200" in {
        GROReferenceMetrics.httpResponseCodeStatus(200: Int)
        GROReferenceMetrics.metrics.defaultRegistry.counter(s"${GROReferenceMetrics.prefix}-http-response-code-200").getCount shouldBe 1
      }

      "increment count for http response code 400" in {

        GROReferenceMetrics.httpResponseCodeStatus(400: Int)
        GROReferenceMetrics.metrics.defaultRegistry.counter(s"${GROReferenceMetrics.prefix}-http-response-code-400").getCount shouldBe 1
      }

      "increment count for http response code 500" in {
        GROReferenceMetrics.httpResponseCodeStatus(500: Int)
        GROReferenceMetrics.metrics.defaultRegistry.counter(s"${GROReferenceMetrics.prefix}-http-response-code-500").getCount shouldBe 1
      }

      "increment count for request to proxy" in {
        GROReferenceMetrics.requestCount()
        GROReferenceMetrics.metrics.defaultRegistry.counter(s"${GROReferenceMetrics.prefix}-request-count").getCount shouldBe 1
      }

    }

    "making requests to details" should {

      "initialise Metrics instance" in {
        GRODetailsMetrics shouldBe a[BRMMetrics]
        GRODetailsMetrics.prefix shouldBe "gro"
      }

      "measure response time for match request" in {
        val startTimer = GRODetailsMetrics.startTimer()
        GRODetailsMetrics.endTimer(startTimer, "details-match-timer")
        GRODetailsMetrics.metrics.defaultRegistry.timer("gro-details-match-timer").getCount should not be 0
      }

      "increment count for http response code 200" in {
        GRODetailsMetrics.httpResponseCodeStatus(200: Int)
        GRODetailsMetrics.metrics.defaultRegistry.counter(s"${GRODetailsMetrics.prefix}-http-response-code-200").getCount should not be 0
      }

      "increment count for http response code 400" in {
        GRODetailsMetrics.httpResponseCodeStatus(400: Int)
        GRODetailsMetrics.metrics.defaultRegistry.counter(s"${GRODetailsMetrics.prefix}-http-response-code-400").getCount should not be 0
      }

      "increment count for http response code 500" in {
        GRODetailsMetrics.httpResponseCodeStatus(500: Int)
        GRODetailsMetrics.metrics.defaultRegistry.counter(s"${GRODetailsMetrics.prefix}-http-response-code-500").getCount should not be 0
      }

      "increment count for request to proxy" in {
        GRODetailsMetrics.requestCount("details-request")
        GRODetailsMetrics.metrics.defaultRegistry.counter(s"${GRODetailsMetrics.prefix}-details-request-count").getCount should not be 0
      }
    }
  }
}
