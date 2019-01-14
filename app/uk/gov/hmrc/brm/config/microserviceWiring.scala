/*
 * Copyright 2019 HM Revenue & Customs
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

import akka.actor.ActorSystem
import com.typesafe.config.Config
import play.api.Mode.Mode
import play.api.{Configuration, Play}
import uk.gov.hmrc.http.HttpGet
import uk.gov.hmrc.http.hooks.HttpHooks
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.config.{AppName, RunMode}
import uk.gov.hmrc.play.http.ws.WSGet
import uk.gov.hmrc.play.microservice.config.LoadAuditingConfig

trait Hooks extends HttpHooks with HttpAuditing {
  override val hooks = Seq(AuditingHook)
  override lazy val auditConnector: AuditConnector = MicroserviceAuditConnector
}

trait WSHttp extends HttpGet with WSGet with Hooks with AppName
object WSHttp extends WSHttp {
  override protected def appNameConfiguration: Configuration = Play.current.configuration

  override protected def actorSystem: ActorSystem = Play.current.actorSystem

  override protected val configuration: Option[Config] = Some(Play.current.configuration.underlying)
}

object MicroserviceAuditConnector extends AuditConnector with RunMode {
  override protected def mode: Mode = Play.current.mode
  override protected def runModeConfiguration: Configuration = Play.current.configuration
  override lazy val auditingConfig = LoadAuditingConfig(s"auditing")
}