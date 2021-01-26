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

import akka.actor.ActorSystem
import com.google.inject.{AbstractModule, Provides}
import play.api.Configuration
import play.api.libs.ws.WSClient
import uk.gov.hmrc.brm.http.ProxyEnabledHttpClient
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.bootstrap.http.{DefaultHttpClient, HttpClient}
import uk.gov.hmrc.play.http.ws.WSProxyConfiguration

class ModuleBindings extends AbstractModule {
  override def configure(): Unit = ()

  @Provides
  def proxyHttpClient(
                       configuration: Configuration,
                       auditing: HttpAuditing,
                       client: WSClient,
                       actorSystem: ActorSystem
                     ): HttpClient = {
    WSProxyConfiguration("microservice.services.proxy", configuration) match {
      case Some(proxyServer) =>
        new ProxyEnabledHttpClient(configuration, auditing, client, proxyServer, actorSystem)
      case None =>
        new DefaultHttpClient(configuration, auditing, client, actorSystem)
    }
  }

}
