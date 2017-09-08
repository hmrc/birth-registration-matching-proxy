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

import java.net.{InetSocketAddress, PasswordAuthentication, Proxy}

import play.mvc.Http.HeaderNames
import uk.co.bigbeeconsultants.http.util.Base64
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

  private object ProxyAuthenticator extends java.net.Authenticator {

    override def getPasswordAuthentication: PasswordAuthentication = {
      BrmLogger.info("ProxyAuthenticator", "getPasswordAuthentication", s"$username : $password")
      new PasswordAuthentication(username, password.toCharArray)
    }

  }

  def configureProxyAuthenticator = {
    java.net.Authenticator.setDefault(ProxyAuthenticator)
  }

  if(required) configureProxyAuthenticator

//  def setProxyAuthHeader: Map[String, String] = {
//    if (required) {
//      BrmLogger.info("ProxyAuthenticator", "setProxyAuthHeader", "setting header")
//
//      BrmLogger.info("ProxyAuthenticator", "setProxyAuthHeader", s"u - $username")
//      BrmLogger.info("ProxyAuthenticator", "setProxyAuthHeader", s"p - $password")
//
//      val encoded: String = new String(Base64.encodeBytes(s"$username:$password".getBytes))
//
//      BrmLogger.info("ProxyAuthenticator", "setProxyAuthHeader", s"encoded header - $encoded")
//
//      Map(HeaderNames.PROXY_AUTHORIZATION -> s"Basic $encoded")
//
//
//
//    } else {
//      BrmLogger.info("ProxyAuthenticator", "setProxyAuthHeader", "not setting header")
//      Map()
//    }
//  }

  def setProxyHost = {
    if (required) {
      BrmLogger.info("ProxyAuthenticator", "setProxyHost", "setting proxy object")
      val proxyAddress = new InetSocketAddress(hostname, port)

      BrmLogger.info("ProxyAuthenticator", "setProxyHost", s"hostname - $hostname")
      BrmLogger.info("ProxyAuthenticator", "setProxyHost", s"port - $port")

      val proxy: Proxy = new Proxy(Proxy.Type.HTTP, proxyAddress)

      BrmLogger.info("ProxyAuthenticator", "setProxyHost", s"proxy - ${proxy.toString}")

      Some(proxy)
    } else {
      BrmLogger.info("ProxyAuthenticator", "setProxyHost", "not setting proxy object")
      None
    }

  }

}
