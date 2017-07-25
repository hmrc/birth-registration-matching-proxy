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

package uk.gov.hmrc.brm.config

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import play.api.{Application, Configuration, Play}
import uk.gov.hmrc.brm.connectors.ConnectorTypes.{DelayAttempts, DelayTime}
import uk.gov.hmrc.play.audit.filters.AuditFilter
import uk.gov.hmrc.play.filters.MicroserviceFilterSupport
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, RunMode, ServicesConfig}
import uk.gov.hmrc.play.http.logging.filters.LoggingFilter
import uk.gov.hmrc.play.microservice.bootstrap.{DefaultMicroserviceGlobal, MicroserviceFilters}
import net.ceedubs.ficus.Ficus._


object ControllerConfiguration extends ControllerConfig {
  lazy val controllerConfigs = Play.current.configuration.underlying.as[Config]("controllers")
}

object GROConnectorConfiguration extends ServicesConfig {

  private val tlsConfigPath = "birth-registration-matching.gro.tls"
  private val authenticationConfigPath = "birth-registration-matching.gro.authentication.v2"

  def message(key:String):String = {
    s"[Configuration][NotFound] birth-registration-matching.$key"
  }

  lazy val serviceUrl = baseUrl("birth-registration-matching")

  lazy val username = getConfString(s"$authenticationConfigPath.username",throw new RuntimeException(message("username")))
  lazy val password = getConfString(s"$authenticationConfigPath.key", throw new RuntimeException(message("key")))
  lazy val clientID = getConfString(s"$authenticationConfigPath.clientID",throw new RuntimeException(message("clientID")))
  lazy val clientSecret = getConfString(s"$authenticationConfigPath.clientSecret", throw new RuntimeException(message("clientSecret")))
  lazy val grantType = getConfString(s"$authenticationConfigPath.grantType", throw new RuntimeException(message("grantType")))

  lazy val authenticationUri = getConfString(s"$authenticationConfigPath.uri", throw new RuntimeException(message("uri")))
  lazy val delayAttemptInMilliseconds : DelayTime = getConfInt(s"birth-registration-matching.gro.http.delayAttemptInMilliseconds",
    throw new RuntimeException(message("delayAttemptInMilliseconds")))
  lazy val delayAttempts : DelayAttempts = getConfInt(s"birth-registration-matching.gro.http.delayAttempts",
    throw new RuntimeException(s"[Configuration][NotFound] ${authenticationConfigPath}delayAttempts"))

  lazy val tlsPrivateKeystore = getConfString(s"$tlsConfigPath.privateKeystore", throw new RuntimeException(message("privateKeystore")))
  lazy val tlsPrivateKeystoreKey = getConfString(s"$tlsConfigPath.privateKeystoreKey", throw new RuntimeException(message("privateKeystoreKey")))
  lazy val certificateExpiryDate = getConfString(s"$tlsConfigPath.certificateExpiryDate", throw new RuntimeException(message("certificateExpiryDate")))
  lazy val allowHostNameMismatch = getConfBool(s"$tlsConfigPath.allowHostnameMismatch", throw new RuntimeException(message("allowHostnameMismatch")))
  lazy val tlsVersion = getConfString(s"$tlsConfigPath.tlsVersion", throw new RuntimeException(message("tlsVersion")))
  lazy val tlsEnabled = getConfBool(s"$tlsConfigPath.tlsEnabled", throw new RuntimeException(message("tlsEnabled")))
  lazy val connectionTimeout = getConfInt("birth-registration-matching.gro.http.connectionTimeout",throw new RuntimeException(message("connectionTimeout")))
  lazy val readTimeout = getConfInt("birth-registration-matching.gro.http.readTimeout",throw new RuntimeException(message("readTimeout")))

   def  blockedBodyWords =   Play.current.configuration.getStringSeq(s"$rootServices.birth-registration-matching.features.audit.excludedWords")
   def disableAuditingLogging = getConfBool("birth-registration-matching.features.audit.disableAuditingLogging", true)

}

object MicroserviceAuditFilter extends AuditFilter with AppName with MicroserviceFilterSupport{
  override val auditConnector = MicroserviceAuditConnector
  override def controllerNeedsAuditing(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsAuditing
}

object MicroserviceLoggingFilter extends LoggingFilter with MicroserviceFilterSupport {
  override def controllerNeedsLogging(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsLogging
}

object MicroserviceGlobal extends DefaultMicroserviceGlobal with RunMode with MicroserviceFilters {
  override val auditConnector = MicroserviceAuditConnector

  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = app.configuration.getConfig(s"microservice.metrics")

  override val loggingFilter = MicroserviceLoggingFilter

  override val microserviceAuditFilter = MicroserviceAuditFilter

  override val authFilter = None
}
