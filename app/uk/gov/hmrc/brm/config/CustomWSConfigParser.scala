package uk.gov.hmrc.brm.config

import play.api.{Configuration, Environment}
import play.api.libs.ws.{WSClientConfig, WSConfigParser}

import javax.inject.{Inject, Singleton}

@Singleton
class CustomWSConfigParser @Inject()(configuration: Configuration, env: Environment)
  extends WSConfigParser(configuration.underlying, env.classLoader) {

  override def parse(): WSClientConfig = {

    val internalParser = new WSConfigParser(configuration.underlying, env.classLoader)
    val config = internalParser.parse()

    val keyStores = config.ssl.keyManagerConfig.keyStoreConfigs.filter(_.data.forall(_.nonEmpty)).map { ks ⇒
      (ks.storeType.toUpperCase, ks.filePath, ks.data) match {
        case (_, None, Some(data)) ⇒
          createKeyStoreConfig(ks, data)

        case other ⇒
          logger.info(s"Adding ${other._1} type keystore")
          ks
      }
    }

    val trustStores = config.ssl.trustManagerConfig.trustStoreConfigs.filter(_.data.forall(_.nonEmpty)).map { ts ⇒
      (ts.filePath, ts.data) match {
        case (None, Some(data)) ⇒
          createTrustStoreConfig(ts, data)

        case _ ⇒
          logger.info(s"Adding ${ts.storeType} type truststore from ${ts.filePath}")
          ts
      }
    }

    val wsClientConfig = config.copy(
      ssl = config.ssl
        .withKeyManagerConfig(config.ssl.keyManagerConfig
          .withKeyStoreConfigs(keyStores))
        .withTrustManagerConfig(config.ssl.trustManagerConfig
          .withTrustStoreConfigs(trustStores))
    )

    wsClientConfig
  }

}
