/*
 * Copyright 2020 HM Revenue & Customs
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

import uk.gov.hmrc.brm.TestFixture

class MetricsSpec extends TestFixture {

  val testMetrics: BRMMetrics = new BRMMetrics(testProxyConfig)

  "Metrics" should {

    "making requests to authentication" should {

      "measure response time for authentication request" in {
        val startTimer = testMetrics.startTimer()
        testMetrics.endTimer(startTimer, "authentication-timer")
        testMetrics.defaultRegistry.timer(s"${testMetrics.prefix}-authentication-timer").getCount should not be 0
      }
    }

    "making requests to reference" should {

      "initialise Metrics instance" in {
        testMetrics shouldBe a[BRMMetrics]
        testMetrics.prefix shouldBe "gro"
      }

      "measure response time for match request" in {
        val startTimer = testMetrics.startTimer()
        testMetrics.endTimer(startTimer, "reference-match-timer")
        testMetrics.defaultRegistry.timer("gro-reference-match-timer").getCount should not be 0
      }

      "increment count for http response code 200" in {
        testMetrics.httpResponseCodeStatus(200: Int)
        testMetrics.defaultRegistry.counter(s"${testMetrics.prefix}-http-response-code-200").getCount shouldBe 1
      }

      "increment count for http response code 400" in {
        testMetrics.httpResponseCodeStatus(400: Int)
        testMetrics.defaultRegistry.counter(s"${testMetrics.prefix}-http-response-code-400").getCount shouldBe 1
      }

      "increment count for http response code 500" in {
        testMetrics.httpResponseCodeStatus(500: Int)
        testMetrics.defaultRegistry.counter(s"${testMetrics.prefix}-http-response-code-500").getCount shouldBe 1
      }

      "increment count for request to proxy" in {
        testMetrics.requestCount()
        testMetrics.defaultRegistry.counter(s"${testMetrics.prefix}-request-count").getCount shouldBe 1
      }

    }

    "making requests to details" should {

      "initialise Metrics instance" in {
        testMetrics shouldBe a[BRMMetrics]
        testMetrics.prefix shouldBe "gro"
      }

      "measure response time for match request" in {
        val startTimer = testMetrics.startTimer()
        testMetrics.endTimer(startTimer, "details-match-timer")
        testMetrics.defaultRegistry.timer("gro-details-match-timer").getCount should not be 0
      }

      "increment count for http response code 200" in {
        testMetrics.httpResponseCodeStatus(200: Int)
        printf(testMetrics.defaultRegistry.counter(s"${testMetrics.prefix}-http-response-code-200").getCount.toString)
        testMetrics.defaultRegistry.counter(s"${testMetrics.prefix}-http-response-code-200").getCount should not be 0
      }

      "increment count for http response code 400" in {
        testMetrics.httpResponseCodeStatus(400: Int)
        testMetrics.defaultRegistry.counter(s"${testMetrics.prefix}-http-response-code-400").getCount should not be 0
      }

      "increment count for http response code 500" in {
        testMetrics.httpResponseCodeStatus(500: Int)
        testMetrics.defaultRegistry.counter(s"${testMetrics.prefix}-http-response-code-500").getCount should not be 0
      }

      "increment count for request to proxy" in {
        testMetrics.requestCount("details-request")
        testMetrics.defaultRegistry.counter(s"${testMetrics.prefix}-details-request-count").getCount should not be 0
      }

    }

  }

}
