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

import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}

@Singleton
class GroAppConfig @Inject() (val servicesConfig: ServicesConfig) {

  private val tlsConfigPath            = "microservice.services.birth-registration-matching.gro.tls"
  private val authenticationConfigPath = "birth-registration-matching.gro.authentication.v2"

  lazy val serviceUrl: String               = servicesConfig.baseUrl("birth-registration-matching")
  lazy val authenticationServiceUrl: String =
    servicesConfig.baseUrl("birth-registration-matching.gro.authentication.v2")

  lazy val groUsername: String     = servicesConfig.getString(s"microservice.services.$authenticationConfigPath.username")
  lazy val groPassword: String     = servicesConfig.getString(s"microservice.services.$authenticationConfigPath.key")
  lazy val groClientID: String     = servicesConfig.getString(s"microservice.services.$authenticationConfigPath.clientID")
  lazy val groClientSecret: String =
    servicesConfig.getString(s"microservice.services.$authenticationConfigPath.clientSecret")
  lazy val groGrantType: String    = servicesConfig.getString(s"microservice.services.$authenticationConfigPath.grantType")

  lazy val authenticationUri: String = servicesConfig.getString(s"microservice.services.$authenticationConfigPath.uri")

  lazy val tlsPrivateKeystore: String    = servicesConfig.getString(s"$tlsConfigPath.privateKeystore")
  lazy val tlsPrivateKeystoreKey: String = servicesConfig.getString(s"$tlsConfigPath.privateKeystoreKey")
  lazy val certificateExpiryDate: String = servicesConfig.getString(s"$tlsConfigPath.certificateExpiryDate")
  lazy val tlsEnabled: Boolean           = servicesConfig.getBoolean(s"$tlsConfigPath.tlsEnabled")
}
