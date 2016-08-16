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

import java.nio.ByteBuffer
import java.nio.charset.{Charset, StandardCharsets}

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

//    val base64keystore = keystoreBase64.replaceAll("[\n\r]", "")
//    val base64keystoreKey = keystoreKeyBase64.replaceAll("[\n\r]", "")

    val decodedKeystore = Base64.getDecoder.decode(keystoreBase64.getBytes(StandardCharsets.US_ASCII))
    val decodedKeystoreKey = Base64.getDecoder.decode(keystoreKeyBase64.getBytes(StandardCharsets.US_ASCII))

//    val decodedKeystore = Base64.getDecoder.decode(base64keystore)
//    val decodedKeystoreKey = Base64.getDecoder.decode(base64keystoreKey)

//    val latin1Charset = Charset.forName("US-ASCII")
//    val charBuffer = latin1Charset.decode(ByteBuffer.wrap(decodedKeystoreKey))
    val passwordString = new String(decodedKeystoreKey, StandardCharsets.US_ASCII)
//    val passwordArray = passwordString.toCharArray
    val array = Array[Char]()
    val passwordArray = passwordString.map(x => x).copyToArray(array, 0, passwordString.length)

    Logger.debug(s"\n [Password] decoded: $decodedKeystoreKey string: $passwordString, array: ${passwordArray} directArray: ${"ENzLAZ7Vay9HhGB".toCharArray}")

//    Logger.debug(s"[TLSFactory][getSocketFactory][keystore]: $keystoreBase64 " +
//      s"replaced: $base64keystore " +
//      s"isTheSame: ${keystoreBase64.equals(base64keystore)} ${keystoreKeyBase64.equals(base64keystoreKey)}")
//    Logger.debug(s"[TLSFactory][getSocketFactory][keystoreKey]: $keystoreKeyBase64 replaced: $base64keystoreKey")

//    ks.load(new ByteArrayInputStream(decodedKeystore), keystoreKeyBase64.toCharArray)
    ks.load(new ByteArrayInputStream(decodedKeystore), array)
//    ks.load(new ByteArrayInputStream(decodedKeystore), decodedKeystoreKey)
//    ks.load(new ByteArrayInputStream(decodedKeystore), "ENzLAZ7Vay9HhGB".toCharArray)
    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
//    kmf.init(ks, keystoreKeyBase64.toCharArray)
    kmf.init(ks, array)
//    kmf.init(ks, decodedKeystoreKey)
//    kmf.init(ks, "ENzLAZ7Vay9HhGB".toCharArray)

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
