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

package uk.gov.hmrc.brm.tls

import java.nio.charset.StandardCharsets

import javax.inject.Inject
import uk.co.bigbeeconsultants.http.Config
import uk.gov.hmrc.brm.config.{GroAppConfig, ProxyAppConfig}
import uk.gov.hmrc.brm.connectors.ProxyAuthenticator
import uk.gov.hmrc.brm.utils.BrmLogger._

class TLSFactory @Inject()(groConfig: GroAppConfig,
                           proxyConfig: ProxyAppConfig,
                           proxyAuthenticator: ProxyAuthenticator) {

  import java.io.ByteArrayInputStream
  import java.security.cert.X509Certificate
  import java.security.{KeyStore, SecureRandom}
  import java.util.Base64
  import javax.net.ssl._

  val connectionTimeout: Int = groConfig.connectionTimeout
  val readTimeout: Int = groConfig.readTimeout
  val allowHostNameMismatch: Boolean = groConfig.allowHostNameMismatch
  val tlsMode: String = groConfig.tlsVersion
  val keystoreBase64: String = groConfig.tlsPrivateKeystore
  val keystoreKeyBase64: String = groConfig.tlsPrivateKeystoreKey
  val tlsEnabled: Boolean = groConfig.tlsEnabled

  val CLASS_NAME : String = this.getClass.getSimpleName

  object DumbTrustManager extends X509TrustManager {
    def getAcceptedIssuers: Array[X509Certificate] = null
    def checkClientTrusted(certs: Array[X509Certificate], authType: String) {}
    def checkServerTrusted(certs: Array[X509Certificate], authType: String) {}
  }

  class SimpleHostnameVerifier(allowMismatch: Boolean) extends HostnameVerifier {
    // During handshaking, if the URL's hostname and the server's identification hostname mismatch,
    // the verification mechanism can call back here to determine whether this connection should be allowed.
    def verify(hostname: String, session: SSLSession): Boolean = allowMismatch
  }

  private def hostnameVerifier: Some[SimpleHostnameVerifier] = {
    Some(new SimpleHostnameVerifier(allowHostNameMismatch))
  }

  private def getSocketFactory : Option[SSLSocketFactory] = {
    info(CLASS_NAME, "getSocketFactory", "created SSLSocketFactory")

    val decodedKeystore = Base64.getDecoder.decode(keystoreBase64.getBytes(StandardCharsets.US_ASCII))
    val decodedKeystoreKey = Base64.getDecoder.decode(keystoreKeyBase64.getBytes(StandardCharsets.US_ASCII))
    val keyString = new String(decodedKeystoreKey, StandardCharsets.US_ASCII)

    debug(CLASS_NAME, "getSocketFactory", s"keystore: $keystoreBase64 key: $keystoreKeyBase64")

    try {
      val ks = KeyStore.getInstance("jks")
      ks.load(new ByteArrayInputStream(decodedKeystore), keyString.toCharArray)

      val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
      kmf.init(ks, keyString.toCharArray)

      val tls = SSLContext.getInstance(tlsMode)
      tls.init(kmf.getKeyManagers, Array(DumbTrustManager), new SecureRandom())
      info(CLASS_NAME, "getSocketFactory", "TLS Connection Established")
      Some(tls.getSocketFactory)
    } catch {
      case e : Exception =>
        error(CLASS_NAME, "getSocketFactory", s"exception when creating SSLSocketFactory: ${e.getMessage}")
        throw e
    }
  }

  def getConfig: Config = {
    val sslSocketFactory = if(tlsEnabled) {
      info(CLASS_NAME, "getConfig", "TLS Enabled")
      getSocketFactory
    } else {
      warn(CLASS_NAME, "getConfig", "TLS Disabled")
      None
    }

    Config(
      connectTimeout = connectionTimeout,
      readTimeout = readTimeout,
      sslSocketFactory = sslSocketFactory,
      hostnameVerifier = hostnameVerifier,
      proxy = proxyAuthenticator.setProxyHost()
    )
  }

}
