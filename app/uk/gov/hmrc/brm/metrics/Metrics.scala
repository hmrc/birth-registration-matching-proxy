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

/**
  * Created by chrisianson on 15/09/16.
  */

package uk.gov.hmrc.brm.metrics

import java.util.concurrent.TimeUnit
import com.kenshoo.play.metrics.MetricsRegistry
import play.api.Logger

trait Metrics {

  val timer = (name: String) => MetricsRegistry.defaultRegistry.timer(name)
  val counter = (name: String) => MetricsRegistry.defaultRegistry.counter(name)

  def completeResponseTimer(diff: Long, unit: TimeUnit): Unit
  def authenticationResponseTimer(diff: Long, unit: TimeUnit): Unit
  def matchResponseTimer(diff: Long, unit: TimeUnit): Unit
  def httpResponseCodeStatus(code: Int): Unit
  def requestCount(): Unit
}

object Metrics extends Metrics {

  Logger.info("[Metrics][constructor] Preloading metrics keys")

  Seq(
    ("complete-response-time", timer),
    ("authentication-response-time", timer),
    ("match-response-time", timer),
    ("http-response-code-200", counter),
    ("http-response-code-400", counter),
    ("http-response-code-500", counter),
    ("request-count", counter)
  ) foreach { t => t._2(t._1) }

  override def completeResponseTimer(diff: Long, unit: TimeUnit): Unit =
    MetricsRegistry.defaultRegistry.timer("complete-response-time").update(diff, unit)

  override def authenticationResponseTimer(diff: Long, unit: TimeUnit): Unit =
    MetricsRegistry.defaultRegistry.timer("authentication-response-time").update(diff, unit)

  override def matchResponseTimer(diff: Long, unit: TimeUnit): Unit =
    MetricsRegistry.defaultRegistry.timer("match-response-time").update(diff, unit)

  override def httpResponseCodeStatus(code: Int): Unit =
    MetricsRegistry.defaultRegistry.counter(s"http-response-code-$code").inc()

  override def requestCount(): Unit =
    MetricsRegistry.defaultRegistry.counter("request-count").inc()

}
