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

package uk.gov.hmrc.brm.http

import akka.actor.ActorSystem
import play.api.Configuration
import play.api.libs.ws.{WSClient, WSProxyServer}
import play.api.test.Injecting
import uk.gov.hmrc.brm.TestFixture
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.HttpAuditing

class ProxyEnabledHttpClientSpec extends TestFixture with Injecting {

  val mockConfiguration: Configuration = inject[Configuration]
  val mockAuditing: HttpAuditing = inject[HttpAuditing]
  val mockWsClient: WSClient = inject[WSClient]
  val mockProxy: WSProxyServer = mock[WSProxyServer]

  val client = new ProxyEnabledHttpClient(mockConfiguration, mockAuditing, mockWsClient, mockProxy, ActorSystem())

  "buildRequest" should {
    "call down a proxy server when proxy server is enabled" in {
      val request = client.buildRequest("http://testurl.com", Nil)(HeaderCarrier())

      request.proxyServer shouldBe Some(mockProxy)
    }
  }

}
