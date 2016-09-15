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

  Logger.info(s"[${this.getClass.toString}][constructor] metrics keys")

  val uid : String
  val timer = (name: String) => MetricsRegistry.defaultRegistry.timer(name)
  val counter = (name: String) => MetricsRegistry.defaultRegistry.counter(name)

  def authenticationResponseTimer(diff: Long, unit: TimeUnit): Unit
  def matchResponseTimer(diff: Long, unit: TimeUnit): Unit
  def httpResponseCodeStatus(code: Int): Unit
  def requestCount(): Unit

  Seq(
    (s"$uid-authentication-response-time", timer),
    (s"$uid-match-response-time", timer),
    (s"$uid-http-response-code-200", counter),
    (s"$uid-http-response-code-400", counter),
    (s"$uid-http-response-code-500", counter),
    (s"$uid-request-count", counter)
  ) foreach { t => t._2(t._1) }



  def timeDifference(start : Long ,end :Long) : Long =
    end-start
}

object GroMetrics extends Metrics {

  override val uid = "gro"

  override def authenticationResponseTimer(diff: Long, unit: TimeUnit): Unit =
    MetricsRegistry.defaultRegistry.timer(s"$uid-authentication-response-time").update(diff, unit)

  override def matchResponseTimer(diff: Long, unit: TimeUnit): Unit =
    MetricsRegistry.defaultRegistry.timer(s"$uid-match-response-time").update(diff, unit)

  override def httpResponseCodeStatus(code: Int): Unit =
    MetricsRegistry.defaultRegistry.counter(s"$uid-http-response-code-$code").inc()

  override def requestCount(): Unit =
    MetricsRegistry.defaultRegistry.counter(s"$uid-request-count").inc()

}
