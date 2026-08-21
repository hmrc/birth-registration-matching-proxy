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

package uk.gov.hmrc.brm

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers
import uk.gov.hmrc.brm.config.GroAppConfig
import uk.gov.hmrc.brm.utils.BaseUnitSpec

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

trait TestFixture
    extends AnyWordSpecLike
    with Matchers
    with OptionValues
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with GuiceOneAppPerSuite
    with BaseUnitSpec {

  given executionContext: ExecutionContext = Helpers.stubControllerComponents().executionContext

  /** Configuration applied to every test application.
    *
    * JVM metrics are off because they register process-wide gauges that clash once more than one suite starts
    * an application in the same JVM. Note that `metrics.enabled` must stay on: the metrics module resets
    * SharedMetricRegistries when an application starts, and BRMMetrics assertions rely on per-suite counts.
    */
  def baseApplicationConfig: Map[String, Any] = Map(
    "metrics.jvm"      -> false,
    "auditing.enabled" -> false
  )

  /** Override in a suite to add to, or replace, entries in [[baseApplicationConfig]]. */
  def extraApplicationConfig: Map[String, Any] = Map.empty

  def baseApplication: GuiceApplicationBuilder =
    GuiceApplicationBuilder().configure(baseApplicationConfig ++ extraApplicationConfig)

  override def fakeApplication(): Application = baseApplication.build()

  val testGroConfig: GroAppConfig = real[GroAppConfig]

  def real[T: ClassTag]: T = app.injector.instanceOf[T]

}
