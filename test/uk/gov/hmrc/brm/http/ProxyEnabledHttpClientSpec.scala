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
import com.typesafe.config.ConfigFactory
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.{DefaultWSProxyServer, WSClient, WSProxyServer}
import play.api.test.Injecting
import play.api.{Application, Configuration}
import uk.gov.hmrc.brm.TestFixture
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.HttpAuditing

class ProxyEnabledHttpClientSpec extends TestFixture with Injecting {

  val mockConfiguration: Configuration = inject[Configuration]
  val mockAuditing: HttpAuditing = inject[HttpAuditing]
  val mockWsClient: WSClient = inject[WSClient]
  val mockProxy: WSProxyServer = mock[WSProxyServer]

  def client(proxyServer: Option[WSProxyServer]): ProxyEnabledHttpClient =
    new ProxyEnabledHttpClient(mockConfiguration, mockAuditing, mockWsClient, ActorSystem()) {
      override lazy val wsProxyServer: Option[WSProxyServer] = proxyServer
    }

  val isProxy: Boolean = true

  override lazy val fakeApplication: Application = new GuiceApplicationBuilder()
    .configure(
      Configuration(
        ConfigFactory.parseString(
          s"""
             |microservice.services.proxy.proxyRequiredForThisEnvironment = $isProxy
             |microservice.services.proxy.username = squiduser
             |microservice.services.proxy.password = squiduser
             |microservice.services.proxy.protocol = true
             |microservice.services.proxy.host = localhost
             |microservice.services.proxy.port = 3128
             |""".stripMargin)
      )
    )
    .build()

  "buildRequest" should {
    "call down a proxy server when proxy server is enabled" in {
      val request = client(Some(DefaultWSProxyServer("host", 1, password = Some("password"))))
        .buildRequest("http://testurl.com", Nil)

      request.proxyServer.get.getClass shouldBe classOf[DefaultWSProxyServer]
      request.proxyServer.get.asInstanceOf[DefaultWSProxyServer].host shouldBe "host"
      request.proxyServer.get.asInstanceOf[DefaultWSProxyServer].password shouldBe Some("password")
      fakeApplication.stop()
    }

    "make a call when proxy is disabled" in {
      val request = client(None).buildRequest("http://testurl.com", Nil)

      request.proxyServer shouldBe None
      fakeApplication.stop()

    }
  }

}
