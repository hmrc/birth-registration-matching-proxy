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
      Metrics shouldBe a[Metrics]
    }

    "measure response time for complete request including authentication and match" in {
      Metrics.completeResponseTimer(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
      MetricsRegistry.defaultRegistry.getTimers.get("complete-response-time").getCount shouldBe 1
    }

    "measure response time for authentication request" in {
      Metrics.authenticationResponseTimer(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
      MetricsRegistry.defaultRegistry.getTimers.get("authentication-response-time").getCount shouldBe 1
    }

    "measure response time for match request" in {
      Metrics.matchResponseTimer(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
      MetricsRegistry.defaultRegistry.getTimers.get("match-response-time").getCount shouldBe 1
    }

    "increment count for http response code 200" in {
      Metrics.httpResponseCodeStatus(200 : Int)
      MetricsRegistry.defaultRegistry.getCounters.get("http-response-code-200").getCount shouldBe 1
    }

    "increment count for http response code 400" in {
      Metrics.httpResponseCodeStatus(400 : Int)
      MetricsRegistry.defaultRegistry.getCounters.get("http-response-code-400").getCount shouldBe 1
    }

    "increment count for http response code 500" in {
      Metrics.httpResponseCodeStatus(500 : Int)
      MetricsRegistry.defaultRegistry.getCounters.get("http-response-code-500").getCount shouldBe 1
    }

    "increment count for request to proxy" in {
      Metrics.requestCount()
      MetricsRegistry.defaultRegistry.getCounters.get("request-count").getCount shouldBe 1
    }

  }

}
