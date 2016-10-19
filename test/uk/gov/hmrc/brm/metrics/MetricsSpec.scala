/*
 * Copyright 2016 HM Revenue & Customs
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

import java.util.concurrent.TimeUnit

import com.kenshoo.play.metrics.MetricsRegistry
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

class MetricsSpec extends UnitSpec with WithFakeApplication {

  "Metrics" should {

    "initialise Metrics instance" in {
      GroMetrics shouldBe a[Metrics]
      GroMetrics.prefix shouldBe "gro"
    }

    "measure response time for authentication request" in {
      val startTimer = GroMetrics.startTimer()
      GroMetrics.endTimer(startTimer, "authentication-response-time")
      MetricsRegistry.defaultRegistry.getTimers.get(s"${GroMetrics.prefix}-authentication-response-time").getCount shouldBe 1
    }

    "measure response time for match request" in {
      val startTimer = GroMetrics.startTimer()
      GroMetrics.endTimer(startTimer, "match-response-time")
      MetricsRegistry.defaultRegistry.getTimers.get(s"${GroMetrics.prefix}-match-response-time").getCount shouldBe 1
    }

    "increment count for http response code 200" in {
      GroMetrics.httpResponseCodeStatus(200: Int)
      MetricsRegistry.defaultRegistry.getCounters.get(s"${GroMetrics.prefix}-http-response-code-200").getCount shouldBe 1
    }

    "increment count for http response code 400" in {
      GroMetrics.httpResponseCodeStatus(400: Int)
      MetricsRegistry.defaultRegistry.getCounters.get(s"${GroMetrics.prefix}-http-response-code-400").getCount shouldBe 1
    }

    "increment count for http response code 500" in {
      GroMetrics.httpResponseCodeStatus(500: Int)
      MetricsRegistry.defaultRegistry.getCounters.get(s"${GroMetrics.prefix}-http-response-code-500").getCount shouldBe 1
    }

    "increment count for request to proxy" in {
      GroMetrics.requestCount()
      MetricsRegistry.defaultRegistry.getCounters.get(s"${GroMetrics.prefix}-request-count").getCount shouldBe 1
    }

  }

}
