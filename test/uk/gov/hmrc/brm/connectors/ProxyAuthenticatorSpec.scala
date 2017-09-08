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

package uk.gov.hmrc.brm.connectors

import org.scalatest.{Tag, TestData}
import org.scalatestplus.play.OneAppPerTest
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.play.test.UnitSpec
import java.net.{Proxy}

/**
  * Created by mew on 01/09/2017.
  */
class ProxyAuthenticatorSpec extends UnitSpec with OneAppPerTest {

  lazy val required: Map[String, _] = Map(
    "microservice.services.proxy.required" -> true
  )

  lazy val notRequired: Map[String, _] = Map(
    "microservice.services.proxy.required" -> false
  )

  override def newAppForTest(testData: TestData) : Application = {
    val config : Map[String, _] = if (testData.tags.contains("enabled")) {
      required
    } else {
      notRequired
    }

    new GuiceApplicationBuilder()
      .configure(config)
      .build()
  }

  "ProxyAuthenticator" should {

//    "return a Map of proxy headers when required" taggedAs Tag("enabled") in {
//      val headers = ProxyAuthenticator.setProxyAuthHeader
//      headers.keys should contain("Proxy-Authorization")
//    }
//
//    "return an empty Map when not required" in {
//      val headers = ProxyAuthenticator.setProxyAuthHeader
//      headers.keys should not contain "Proxy-Authorization"
//    }

    "return a Proxy object when required" taggedAs Tag("enabled") in {
      val proxy = ProxyAuthenticator.setProxyHost
      proxy.get shouldBe a[Proxy]
    }

    "return None when a proxy isn't required" in {
      val proxy = ProxyAuthenticator.setProxyHost
      proxy shouldBe None
    }

  }

}
