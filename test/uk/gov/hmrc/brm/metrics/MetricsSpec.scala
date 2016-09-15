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

/**
  * Created by chrisianson on 15/09/16.
  */
class MetricsSpec extends UnitSpec with WithFakeApplication {

  "Metrics" should {

    "initialise Metrics instance" in {
      GroMetrics shouldBe a[Metrics]
      GroMetrics.uid shouldBe "gro"
    }

    "measure response time for authentication request" in {
      GroMetrics.authenticationResponseTimer(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
      MetricsRegistry.defaultRegistry.getTimers.get(s"${GroMetrics.uid}-authentication-response-time").getCount shouldBe 1
    }

    "measure response time for match request" in {
      GroMetrics.matchResponseTimer(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
      MetricsRegistry.defaultRegistry.getTimers.get(s"${GroMetrics.uid}-match-response-time").getCount shouldBe 1
    }

    "increment count for http response code 200" in {
      GroMetrics.httpResponseCodeStatus(200: Int)
      MetricsRegistry.defaultRegistry.getCounters.get(s"${GroMetrics.uid}-http-response-code-200").getCount shouldBe 1
    }

    "increment count for http response code 400" in {
      GroMetrics.httpResponseCodeStatus(400: Int)
      MetricsRegistry.defaultRegistry.getCounters.get(s"${GroMetrics.uid}-http-response-code-400").getCount shouldBe 1
    }

    "increment count for http response code 500" in {
      GroMetrics.httpResponseCodeStatus(500: Int)
      MetricsRegistry.defaultRegistry.getCounters.get(s"${GroMetrics.uid}-http-response-code-500").getCount shouldBe 1
    }

    "increment count for request to proxy" in {
      GroMetrics.requestCount()
      MetricsRegistry.defaultRegistry.getCounters.get(s"${GroMetrics.uid}-request-count").getCount shouldBe 1
    }

    "timeDifference should return correct time between start and end time" in {
      val difference = GroMetrics.timeDifference(5000, 10000)
      difference shouldBe 5000
    }

  }

}
