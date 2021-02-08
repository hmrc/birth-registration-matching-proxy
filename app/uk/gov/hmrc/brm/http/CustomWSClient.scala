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

import akka.stream.Materializer
import com.typesafe.sslconfig.ssl.SSLConfigSettings
import play.api._
import play.api.libs.ws._
import play.api.libs.ws.ahc.{AhcConfigBuilder, AhcWSClient, AhcWSClientConfig, AhcWSClientConfigFactory, AhcWSClientConfigParser}
import play.core.ApplicationProvider
import play.core.server.ServerConfig
import play.core.server.ssl.DefaultSSLEngineProvider
import play.shaded.ahc.io.netty.handler.ssl.{ClientAuth, JdkSslContext}
import play.shaded.ahc.org.asynchttpclient.{AsyncHttpClientConfig, DefaultAsyncHttpClientConfig}
import uk.gov.hmrc.brm.config.{CustomWSConfigParser, GroAppConfig}

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.security.{KeyStore, SecureRandom}
import java.security.cert.X509Certificate
import java.util.Base64
import javax.inject.Inject
import javax.net.ssl.{KeyManagerFactory, SSLContext, SSLEngine, X509TrustManager}

class CustomWSClient
//@Inject()(configuration: Configuration,
//                               environment: Environment,
//                               groConfig: GroAppConfig,
//                               implicit materializer: Materializer) {
//
//  object DumbTrustManager extends X509TrustManager {
//    def getAcceptedIssuers: Array[X509Certificate] = null
//    def checkClientTrusted(certs: Array[X509Certificate], authType: String) {}
//    def checkServerTrusted(certs: Array[X509Certificate], authType: String) {}
//  }
//
//  val tlsMode: String = "TLSv1.2"
//  val keystoreBase64: String = groConfig.tlsPrivateKeystore
//  val keystoreKeyBase64: String = groConfig.tlsPrivateKeystoreKey
//
//  val decodedKeystore = Base64.getDecoder.decode(keystoreBase64.getBytes(StandardCharsets.US_ASCII))
//  val decodedKeystoreKey = Base64.getDecoder.decode(keystoreKeyBase64.getBytes(StandardCharsets.US_ASCII))
//  val keyString = new String(decodedKeystoreKey, StandardCharsets.US_ASCII)
//
//  val parser = new CustomWSConfigParser(configuration, environment)
//  val config: AhcWSClientConfig = new AhcWSClientConfigParser(parser.parse(), configuration.underlying, environment.classLoader).parse()
//  val builder = new AhcConfigBuilder(config)
//
//  val ks = KeyStore.getInstance("jks")
//  ks.load(new ByteArrayInputStream(decodedKeystore), keyString.toCharArray)
//
//  val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
//  kmf.init(ks, keyString.toCharArray)
//
//  val sslContext = SSLContext.getInstance(tlsMode)
//  sslContext.init(kmf.getKeyManagers, Array(DumbTrustManager), new SecureRandom())
//
//  val nettySslContext = new JdkSslContext(sslContext, true, ClientAuth.NONE)
//
//  val ahcBuilder: DefaultAsyncHttpClientConfig.Builder = builder.configure()
//  ahcBuilder.setSslContext(nettySslContext)
//
//  val ahcConfig = AhcWSClientConfigFactory.forConfig(configuration.underlying, environment.classLoader)
//  val wsClient = AhcWSClient(ahcConfig.copy(wsClientConfig = ahcConfig.wsClientConfig.copy(ssl = sslContext)))
//}

class CustomSslEngineProvider(serverConfig: ServerConfig,
                              appProvider: ApplicationProvider,
                              groAppConfig: GroAppConfig) extends DefaultSSLEngineProvider(serverConfig, appProvider) {

  val tlsMode: String = "TLSv1.2"
  val keystoreBase64: String = groAppConfig.tlsPrivateKeystore
  val keystoreKeyBase64: String = groAppConfig.tlsPrivateKeystoreKey

  val decodedKeystore = Base64.getDecoder.decode(keystoreBase64.getBytes(StandardCharsets.US_ASCII))
  val decodedKeystoreKey = Base64.getDecoder.decode(keystoreKeyBase64.getBytes(StandardCharsets.US_ASCII))
  val keyString = new String(decodedKeystoreKey, StandardCharsets.US_ASCII)
    object DumbTrustManager extends X509TrustManager {
      def getAcceptedIssuers: Array[X509Certificate] = null
      def checkClientTrusted(certs: Array[X509Certificate], authType: String) {}
      def checkServerTrusted(certs: Array[X509Certificate], authType: String) {}
    }


  override def createSSLContext(applicationProvider: ApplicationProvider): SSLContext = {
    println("GOT HERE!!!!")
    val ks = KeyStore.getInstance("jks")
    ks.load(new ByteArrayInputStream(decodedKeystore), keyString.toCharArray)

    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    kmf.init(ks, keyString.toCharArray)

    println("I am getting the new SSL!!!")

    val tls = SSLContext.getInstance(tlsMode)
    tls.init(kmf.getKeyManagers, Array(DumbTrustManager), new SecureRandom())
    tls
  }

  override def createSSLEngine: SSLEngine = {
    println("goot to creating engine")
    sslContext.createSSLEngine()
  }


}