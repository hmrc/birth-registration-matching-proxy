/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.brm.config

import com.typesafe.config.ConfigFactory
import play.api.{Application, Configuration}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSClient
import uk.gov.hmrc.brm.TestFixture
import uk.gov.hmrc.brm.http.ProxyEnabledHttpClient
import uk.gov.hmrc.play.audit.http.HttpAuditing

class ModuleBindingsSpec extends TestFixture {

  override lazy val fakeApplication: Application = new GuiceApplicationBuilder()
    .configure(
      Configuration(
        ConfigFactory.parseString(
          s"""
            |microservice.services.proxy.proxyRequiredForThisEnvironment = true
            |microservice.services.proxy.username = squiduser
            |microservice.services.proxy.password = squiduser
            |microservice.services.proxy.protocol = true
            |microservice.services.proxy.host = localhost
            |microservice.services.proxy.port = 3128
            |""".stripMargin)
      )
    )
    .build()

  val testModuleBindings: ModuleBindings = new ModuleBindings

  "ModuleBindings" should {
    "provide a proxy client when proxy is on" in {
      testModuleBindings.createClient(
        fakeApplication.configuration, mock[HttpAuditing], mock[WSClient], fakeApplication.actorSystem
      ).getClass shouldBe classOf[ProxyEnabledHttpClient]
    }

  }
}
