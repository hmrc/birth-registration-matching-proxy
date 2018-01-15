/*
 * Copyright 2018 HM Revenue & Customs
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

import org.scalatest.Suite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeApplication
import uk.gov.hmrc.play.test.WithFakeApplication

trait BRMFakeApplication extends WithFakeApplication {
  this: Suite =>

  val config: Map[String, _] = Map(
    "Test.microservice.services.auth.host" -> "localhost",
    "Test.microservice.services.auth.port" -> "8500"
  )

  override lazy val fakeApplication = GuiceApplicationBuilder(
    disabled = Seq(classOf[com.kenshoo.play.metrics.PlayModule])
  ).configure(config)
    .build()
}
