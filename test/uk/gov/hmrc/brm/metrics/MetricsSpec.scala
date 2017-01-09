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

package uk.gov.hmrc.brm.metrics

import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

class MetricsSpec extends UnitSpec with WithFakeApplication {

  "Metrics" when {

    "making requests to authentication" should {

      "measure response time for authentication request" in {
        val startTimer = GroReferenceMetrics.startTimer()
        GroReferenceMetrics.endTimer(startTimer, "authentication-response-time")
        GroReferenceMetrics.metrics.defaultRegistry.counter(s"${GroReferenceMetrics.prefix}-authentication-response-time").getCount shouldBe 1
      }

    }

    "making requests to reference" should {

      "initialise Metrics instance" in {
        GroReferenceMetrics shouldBe a[Metrics]
        GroReferenceMetrics.prefix shouldBe "gro"
      }

      "measure response time for match request" in {
        val startTimer = GroReferenceMetrics.startTimer()
        GroReferenceMetrics.endTimer(startTimer, "match-response-time")
        GroReferenceMetrics.metrics.defaultRegistry.counter(s"${GroReferenceMetrics.prefix}-match-response-time").getCount shouldBe 1
      }

      "increment count for http response code 200" in {
        GroReferenceMetrics.httpResponseCodeStatus(200: Int)
        GroReferenceMetrics.metrics.defaultRegistry.counter(s"${GroReferenceMetrics.prefix}-http-response-code-200").getCount shouldBe 1
      }

      "increment count for http response code 400" in {
        GroReferenceMetrics.httpResponseCodeStatus(400: Int)
        GroReferenceMetrics.metrics.defaultRegistry.counter(s"${GroReferenceMetrics.prefix}-http-response-code-400").getCount shouldBe 1
      }

      "increment count for http response code 500" in {
        GroReferenceMetrics.httpResponseCodeStatus(500: Int)
        GroReferenceMetrics.metrics.defaultRegistry.counter(s"${GroReferenceMetrics.prefix}-http-response-code-500").getCount shouldBe 1
      }

      "increment count for request to proxy" in {
        GroReferenceMetrics.requestCount()
        GroReferenceMetrics.metrics.defaultRegistry.counter(s"${GroReferenceMetrics.prefix}-request-count").getCount shouldBe 1
      }

    }

    "making requests to details" should {

      "initialise Metrics instance" in {
        GRODetailsMetrics shouldBe a[Metrics]
        GRODetailsMetrics.prefix shouldBe "gro-details"
      }

      "measure response time for match request" in {
        val startTimer = GRODetailsMetrics.startTimer()
        GRODetailsMetrics.endTimer(startTimer, "match-response-time")
        GroReferenceMetrics.metrics.defaultRegistry.counter(s"${GRODetailsMetrics.prefix}-match-response-time").getCount shouldBe 1
      }

      "increment count for http response code 200" in {
        GRODetailsMetrics.httpResponseCodeStatus(200: Int)
        GroReferenceMetrics.metrics.defaultRegistry.counter(s"${GRODetailsMetrics.prefix}-http-response-code-200").getCount shouldBe 1
      }

      "increment count for http response code 400" in {
        GRODetailsMetrics.httpResponseCodeStatus(400: Int)
        GroReferenceMetrics.metrics.defaultRegistry.counter(s"${GRODetailsMetrics.prefix}-http-response-code-400").getCount shouldBe 1
      }

      "increment count for http response code 500" in {
        GRODetailsMetrics.httpResponseCodeStatus(500: Int)
        GroReferenceMetrics.metrics.defaultRegistry.counter(s"${GRODetailsMetrics.prefix}-http-response-code-500").getCount shouldBe 1
      }

      "increment count for request to proxy" in {
        GRODetailsMetrics.requestCount()
        GroReferenceMetrics.metrics.defaultRegistry.counter(s"${GRODetailsMetrics.prefix}-request-count").getCount shouldBe 1
      }
    }
  }
}
