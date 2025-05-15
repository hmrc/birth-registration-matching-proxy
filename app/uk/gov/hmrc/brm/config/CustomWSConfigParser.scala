/*
 * Copyright 2023 HM Revenue & Customs
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

import com.typesafe.sslconfig.ssl.KeyStoreConfig
import play.api.inject.{Binding, Module}
import play.api.libs.ws.{WSClientConfig, WSConfigParser}
import play.api.{Configuration, Environment}

import java.io.FileInputStream
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.inject.{Inject, Singleton}
import scala.jdk.CollectionConverters.EnumerationHasAsScala
import scala.util.{Failure, Success, Try}

@Singleton
class CustomWSConfigParser @Inject() (configuration: Configuration, env: Environment)
    extends WSConfigParser(configuration.underlying, env.classLoader) {

  lazy val className: String = this.getClass.getSimpleName

  def extractCertificateDates(
    keystorePath: String = "conf/cert/certificate.p12",
    keystorePassword: String = "cGFzc3dvcmQ="
  ): Try[(String, java.util.Date, java.util.Date)] =
    Try {

      val keystore    = KeyStore.getInstance("PKCS12")
      val inputStream = new FileInputStream(keystorePath)

      try {
        keystore.load(inputStream, keystorePassword.toCharArray)

        val aliases    = keystore.aliases.asScala.toList
        val firstAlias = aliases.head

        keystore.getCertificate(firstAlias) match {
          case certificate: X509Certificate => (firstAlias, certificate.getNotBefore, certificate.getNotAfter)
          case _                            => throw new Exception("Error getting certificate")
        }

      } finally inputStream.close()
    }

  // Usage example
  def printCertificateDates(
    keystorePath: String = "conf/cert/certificate.p12",
    keystorePassword: String = "cGFzc3dvcmQ="
  ): Unit =
    extractCertificateDates(keystorePath, keystorePassword) match {
      case Success((alias, notBefore, notAfter)) =>
        println("====================== CERTIFICATE DETAILS ======================")
        println()
        println(s"Certificate alias: $alias")
        println(s"Valid from: $notBefore")
        println(s"Expires on: $notAfter")
        println()
        println("====================== CERTIFICATE DETAILS ======================")

      case Failure(ex) =>
        println(s"Error extracting certificate dates: ${ex.getMessage}")
    }

  override def parse(): WSClientConfig = {

    val internalParser = new WSConfigParser(configuration.underlying, env.classLoader)
    val config         = internalParser.parse()

    val keyStoreConfig: scala.collection.immutable.Seq[KeyStoreConfig] =
      config.ssl.keyManagerConfig.keyStoreConfigs

    val wsClientConfig = config.copy(
      ssl = config.ssl
        .withKeyManagerConfig(
          config.ssl.keyManagerConfig
            .withKeyStoreConfigs(keyStoreConfig)
        )
    )

    println()
    printCertificateDates()
    println()

    wsClientConfig
  }

}

class CustomWSConfigParserModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    Seq(
      bind[WSConfigParser].to[CustomWSConfigParser].eagerly()
    )

}
