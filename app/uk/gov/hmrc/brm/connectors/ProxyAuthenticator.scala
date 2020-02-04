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

import uk.gov.hmrc.brm.config.ProxyConfiguration
import uk.gov.hmrc.brm.utils.BrmLogger

object ProxyAuthenticator extends ProxyAuthenticator {
  override protected val username: String = ProxyConfiguration.username
  override protected val password: String = ProxyConfiguration.password
  override protected val hostname: String = ProxyConfiguration.hostname
  override protected val port: Int = ProxyConfiguration.port

  override protected def required: Boolean = ProxyConfiguration.required
}

trait ProxyAuthenticator {

  protected val username: String
  protected val password: String
  protected val hostname: String
  protected val port: Int

  protected def required: Boolean

  // $COVERAGE-OFF$
  class ProxyAuthenticator extends java.net.Authenticator {

    override def getPasswordAuthentication: PasswordAuthentication = {
      BrmLogger.info("ProxyAuthenticator", "getPasswordAuthentication", s"sending credentials")
      new PasswordAuthentication(username, password.toCharArray)
    }

  }

  def configureProxyAuthenticator = {
    if(required) {
      java.net.Authenticator.setDefault(new ProxyAuthenticator)
    }
  }
  // $COVERAGE-ON$


  def setProxyHost = {
    if (required) {
      BrmLogger.info("ProxyAuthenticator", "setProxyHost", "setting proxy object")
      val proxyAddress = new InetSocketAddress(hostname, port)
      val proxy: Proxy = new Proxy(Proxy.Type.HTTP, proxyAddress)

      Some(proxy)
    } else {
      BrmLogger.info("ProxyAuthenticator", "setProxyHost", "not setting proxy object")
      None
    }

  }

}
