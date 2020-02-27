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

import java.net.{InetSocketAddress, PasswordAuthentication, Proxy}

import javax.inject.Inject
import uk.gov.hmrc.brm.config.ProxyAppConfig
import uk.gov.hmrc.brm.utils.BrmLogger


class ProxyAuthenticator @Inject()(proxyConfig: ProxyAppConfig) {

  val username: String = proxyConfig.proxyUsername
  val password: String = proxyConfig.proxyPassword
  val hostname: String = proxyConfig.proxyHostname
  val port: String = proxyConfig.proxyPort
  def required: Boolean = proxyConfig.proxyRequired

  // $COVERAGE-OFF$
  class ProxyAuthenticator extends java.net.Authenticator {
    override def getPasswordAuthentication: PasswordAuthentication = {
      BrmLogger.info("ProxyAuthenticator", "getPasswordAuthentication", s"sending credentials")
      new PasswordAuthentication(username, password.toCharArray)
    }
  }

  def configureProxyAuthenticator(): Unit = {
    if(required) {
      java.net.Authenticator.setDefault(new ProxyAuthenticator)
    }
  }
  // $COVERAGE-ON$


  def setProxyHost(): Option[Proxy] = {
    if (required) {
      BrmLogger.info("ProxyAuthenticator", "setProxyHost", "successfully setting proxy")
      val proxyAddress = new InetSocketAddress(hostname, port.toInt)
      val proxy: Proxy = new Proxy(Proxy.Type.HTTP, proxyAddress)

      Some(proxy)
    } else {
      BrmLogger.info("ProxyAuthenticator", "setProxyHost", "not setting proxy")
      None
    }
  }
}