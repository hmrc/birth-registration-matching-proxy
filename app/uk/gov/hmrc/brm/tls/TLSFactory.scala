/*
 * Copyright 2016 HM Revenue & Customs
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

import play.api.Logger
import uk.co.bigbeeconsultants.http.Config
import uk.gov.hmrc.brm.config.GROConnectorConfiguration

trait TLSFactory {

  import java.io.ByteArrayInputStream
  import java.security.cert.X509Certificate
  import java.security.{KeyStore, SecureRandom}
  import java.util.Base64
  import javax.net.ssl._

  protected val keystoreBase64 : String
  protected val keystoreKeyBase64 : String
  protected val tlsMode : String
  protected val allowHostNameMismatch : Boolean

  object DumbTrustManager extends X509TrustManager {
    def getAcceptedIssuers: Array[X509Certificate] = null

    def checkClientTrusted(certs: Array[X509Certificate], authType: String) {}

    def checkServerTrusted(certs: Array[X509Certificate], authType: String) {}
  }

  class SimpleHostnameVerifier(allowMismatch: Boolean) extends HostnameVerifier {
    // During handshaking, if the URL's hostname and the server's identification hostname mismatch,
    // the verification mechanism can call back here to determine whether this connection should be allowed.
    def verify(hostname: String, session: SSLSession) = allowMismatch
  }

  private def hostnameVerifier = {
    Some(new SimpleHostnameVerifier(allowHostNameMismatch))
  }

  private def getSocketFactory : Option[SSLSocketFactory] = {
    val ks = KeyStore.getInstance("jks")

    val base64keystore = keystoreBase64.replaceAll("[\n\r]", "")
    val base64keystoreKey = keystoreKeyBase64.replaceAll("[\n\r]", "")

    val decodedKeystore = Base64.getDecoder.decode(base64keystore.getBytes(StandardCharsets.US_ASCII))
    val decodedKeystoreKey = Base64.getDecoder.decode(base64keystoreKey.getBytes(StandardCharsets.US_ASCII)).map(_.toChar)

    Logger.debug(s"[TLSFactory][getSocketFactory][keystore]: $keystoreBase64 replaced: $base64keystore")
    Logger.debug(s"[TLSFactory][getSocketFactory][keystoreKey]: $keystoreKeyBase64 replaced: $base64keystoreKey")

    ks.load(new ByteArrayInputStream(decodedKeystore), decodedKeystoreKey)
    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    kmf.init(ks, decodedKeystoreKey)

    val tls = SSLContext.getInstance(tlsMode)
    tls.init(kmf.getKeyManagers, Array(DumbTrustManager), new SecureRandom())
    Some(tls.getSocketFactory)
  }

  def getConfig = {
    val sslSocketFactory = if(GROConnectorConfiguration.tlsEnabled) getSocketFactory else None

    Config(
      connectTimeout = 5000,
      readTimeout = 10000,
      sslSocketFactory = sslSocketFactory,
      hostnameVerifier = hostnameVerifier
    )
  }

}

object TLSFactory extends TLSFactory {
  override val allowHostNameMismatch = GROConnectorConfiguration.hostname
  override val tlsMode = GROConnectorConfiguration.tlsVersion
  override val keystoreBase64 = GROConnectorConfiguration.tlsPrivateKeystore
  override val keystoreKeyBase64 = GROConnectorConfiguration.tlsPrivateKeystoreKey
}
