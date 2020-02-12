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

package uk.gov.hmrc.brm.connectors

import java.net.Proxy

import uk.gov.hmrc.brm.TestFixture


class ProxyAuthenticatorSpec extends TestFixture {

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

    "return a Proxy object when required is true" in {
      val testConnector: ProxyAuthenticator = new ProxyAuthenticator(testProxyConfig) {
        override def required: Boolean = true
      }
      val proxy = testConnector.setProxyHost()
      proxy.get shouldBe a[Proxy]
    }

    "return None when a proxy isn't required" in {
      val testConnector: ProxyAuthenticator = new ProxyAuthenticator(testProxyConfig)

      val proxy = testConnector.setProxyHost()
      proxy shouldBe None
    }

  }
}
