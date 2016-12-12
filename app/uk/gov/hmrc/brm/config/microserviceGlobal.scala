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

package uk.gov.hmrc.brm.config

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import play.api.{Application, Configuration, Play}
import uk.gov.hmrc.brm.connectors.ConnectorTypes.{DelayAttempts, DelayTime}
import uk.gov.hmrc.play.audit.filters.AuditFilter
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, RunMode, ServicesConfig}
import uk.gov.hmrc.play.http.logging.filters.LoggingFilter
import uk.gov.hmrc.play.microservice.bootstrap.DefaultMicroserviceGlobal


object ControllerConfiguration extends ControllerConfig {
  lazy val controllerConfigs = Play.current.configuration.underlying.as[Config]("controllers")
}

object GROConnectorConfiguration extends ServicesConfig {
  lazy val serviceUrl = baseUrl("birth-registration-matching")
  lazy val username = getConfString("birth-registration-matching.username", throw new RuntimeException("[Configuration][NotFound] birth-registration-matching.username"))
  lazy val password = getConfString("birth-registration-matching.key", throw new RuntimeException("[Configuration][NotFound] birth-registration-matching.key"))
  lazy val version = getConfString("birth-registration-matching.version", throw new RuntimeException("[Configuration][NotFound] birth-registration-matching.version"))

  lazy val tlsPrivateKeystore = getConfString("birth-registration-matching.privateKeystore", throw new RuntimeException("[Configuration][NotFound] birth-registration-matching.privateKeystore"))
  lazy val tlsPrivateKeystoreKey = getConfString("birth-registration-matching.privateKeystoreKey", throw new RuntimeException("[Configuration][NotFound] birth-registration-matching.privateKeystoreKey"))
  lazy val hostname = getConfBool("birth-registration-matching.allowHostnameMismatch", throw new RuntimeException("[Configuration][NotFound] birth-registration-matching.allowHostnameMismatch"))
  lazy val tlsVersion = getConfString("birth-registration-matching.tlsVersion", throw new RuntimeException("[Configuration][NotFound] birth-registration-matching.tlsVersion"))
  lazy val tlsEnabled = getConfBool("birth-registration-matching.tlsEnabled", throw new RuntimeException("[Configuration][NotFound] birth-registration-matching.tlsEnabled"))
  lazy val connectionTimeout = getConfInt("birth-registration-matching.connectionTimeout", throw new RuntimeException("[Configuration][NotFound] birth-registration-matching.connectionTimeout"))
  lazy val readTimeout = getConfInt("birth-registration-matching.readTimeout", throw new RuntimeException("[Configuration][NotFound] birth-registration-matching.readTimeout"))
  lazy val delayAttemptInMilliseconds : DelayTime = getConfInt("birth-registration-matching.delayAttemptInMilliseconds", throw new RuntimeException("[Configuration][NotFound] birth-registration-matching.delayAttemptInMilliseconds"))
  lazy val delayAttempts : DelayAttempts = getConfInt("birth-registration-matching.delayAttempts", throw new RuntimeException("[Configuration][NotFound] birth-registration-matching.delayAttempts"))
}

object MicroserviceAuditFilter extends AuditFilter with AppName {
  override val auditConnector = MicroserviceAuditConnector
  override def controllerNeedsAuditing(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsAuditing
}

object MicroserviceLoggingFilter extends LoggingFilter {
  override def controllerNeedsLogging(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsLogging
}

object MicroserviceGlobal extends DefaultMicroserviceGlobal with RunMode {
  override val auditConnector = MicroserviceAuditConnector

  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = app.configuration.getConfig(s"microservice.metrics")

  override val loggingFilter = MicroserviceLoggingFilter

  override val microserviceAuditFilter = MicroserviceAuditFilter

  override val authFilter = None
}
