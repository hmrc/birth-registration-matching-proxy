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
import uk.gov.hmrc.brm.utils.BrmLogger

import java.io.{File, FileOutputStream}
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.inject.{Inject, Singleton}

@Singleton
class CustomWSConfigParser @Inject() (configuration: Configuration, env: Environment)
    extends WSConfigParser(configuration.underlying, env.classLoader) {

  lazy val className: String = this.getClass.getSimpleName

  override def parse(): WSClientConfig = {

    val internalParser = new WSConfigParser(configuration.underlying, env.classLoader)
    val config         = internalParser.parse()

    val keyStoreConfig: scala.collection.immutable.Seq[KeyStoreConfig] =
      config.ssl.keyManagerConfig.keyStoreConfigs.filter(_.data.forall(_.nonEmpty)).map { ks ⇒
        (ks.storeType.toUpperCase, ks.filePath, ks.data) match {
          case (_, None, Some(data)) =>
            createKeyStoreConfig(ks, data)

          case other =>
            BrmLogger.info(className, "parse", s"Adding ${other._1} type keystore")
            ks
        }
      }

    val wsClientConfig = config.copy(
      ssl = config.ssl
        .withKeyManagerConfig(
          config.ssl.keyManagerConfig
            .withKeyStoreConfigs(keyStoreConfig)
        )
    )

    wsClientConfig
  }

  /**
    * @return absolute file path with the bytes written to the file
    */
  def createTempFileForData(data: String): (String, Array[Byte]) = {
    val file = File.createTempFile(getClass.getSimpleName, ".tmp")
    file.deleteOnExit()
    val os   = new FileOutputStream(file)
    try {
      val bytes = Base64.getDecoder.decode(data.getBytes(StandardCharsets.US_ASCII))
      os.write(bytes)
      os.flush()
      file.getAbsolutePath → bytes
    } finally os.close()
  }

  private def createKeyStoreConfig(ks: KeyStoreConfig, data: String): KeyStoreConfig = {
    BrmLogger.info(className, "createKeyStoreConfig", "Creating key store config")
    val (ksFilePath, _) = createTempFileForData(data)
    BrmLogger.info(className, "createKeyStoreConfig", s"Successfully wrote keystore data to file: $ksFilePath")

    val decryptedPass = ks.password
      .filter(_.nonEmpty)
      .map(pass => Base64.getDecoder.decode(pass.getBytes(StandardCharsets.US_ASCII)))
      .map(new String(_, StandardCharsets.US_ASCII))

    ks.withFilePath(Some(ksFilePath)).withPassword(decryptedPass)
  }

}

class CustomWSConfigParserModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    Seq(
      bind[WSConfigParser].to[CustomWSConfigParser].eagerly
    )

}
