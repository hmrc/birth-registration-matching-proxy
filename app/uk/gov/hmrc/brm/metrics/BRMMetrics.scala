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

import java.util.concurrent.TimeUnit

import uk.gov.hmrc.play.graphite.MicroserviceMetrics
import uk.gov.hmrc.brm.utils.BrmLogger

trait BRMMetrics extends MicroserviceMetrics {

  val prefix: String

  def httpResponseCodeStatus(code: Int): Unit =
    metrics.defaultRegistry.counter(s"$prefix-http-response-code-$code").inc()

  def requestCount(key: String = "request"): Unit = {
    metrics.defaultRegistry.counter(s"$prefix-$key-count").inc()
  }

  def time(diff: Long, unit: TimeUnit, key: String) =
    metrics.defaultRegistry.timer(s"$prefix-$key").update(diff, unit)

  def startTimer(): Long = System.currentTimeMillis()

  def endTimer(start: Long, key: String = "timer") = {
    val end = System.currentTimeMillis() - start
    time(end, TimeUnit.MILLISECONDS, key)
  }
}

object GROReferenceMetrics extends BRMMetrics {
  override val prefix = "gro"
}

object GRODetailsMetrics extends BRMMetrics {
  override val prefix = "gro"
}
