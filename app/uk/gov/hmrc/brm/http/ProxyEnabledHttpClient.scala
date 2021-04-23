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
import play.api.libs.ws.{WSClient, WSProxyServer, WSRequest}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient
import uk.gov.hmrc.play.http.ws.WSProxyConfiguration

import javax.inject.Inject

class ProxyEnabledHttpClient @Inject()(
                              config: Configuration,
                              httpAuditing: HttpAuditing,
                              override val wsClient: WSClient,
                              override protected val actorSystem: ActorSystem)
  extends DefaultHttpClient(config, httpAuditing, wsClient, actorSystem) {

  lazy val proxyConfiguration: Option[WSProxyServer] = WSProxyConfiguration("microservice.services.proxy", config)

  override def buildRequest[A](url: String, headers: Seq[(String, String)])(implicit hc: HeaderCarrier): WSRequest = {
    proxyConfiguration match {
      case Some(proxy) => super.buildRequest(url, headers).withProxyServer(proxy)
      case _ => super.buildRequest(url, headers)
    }
  }
}