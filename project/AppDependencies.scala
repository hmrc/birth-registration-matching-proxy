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

import play.sbt.PlayImport._
import sbt.{ModuleID, _}

object AppDependencies {

  private val bootstrapPlayVersion = "7.15.0"

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-backend-play-28" % bootstrapPlayVersion,
    "uk.gov.hmrc" %% "domain"                    % "8.1.0-play-28"
  )

  val test: Seq[ModuleID]    = Seq(
    "uk.gov.hmrc"         %% "bootstrap-test-play-28" % bootstrapPlayVersion,
    "org.scalatestplus"   %% "mockito-3-4"            % "3.2.10.0",
    "org.specs2"          %% "specs2-core"            % "4.20.0",
    "org.specs2"          %% "specs2-mock"            % "4.20.0",
    "com.vladsch.flexmark" % "flexmark-all"           % "0.64.0"
  ).map(_ % Test)

  def apply(): Seq[ModuleID] = compile ++ test
}
