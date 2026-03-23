/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.brm.certificate

import play.api.libs.json._

sealed trait ExpiryThreshold { def value: String }

object ExpiryThreshold {
  case object Expired extends ExpiryThreshold { val value = "EXPIRED" }
  case object CriticalWarning extends ExpiryThreshold { val value = "CRITICAL_WARNING" }
  case object Warning extends ExpiryThreshold { val value = "WARNING" }
  case object EarlyWarning extends ExpiryThreshold { val value = "EARLY_WARNING" }

  val allConditions: Seq[ExpiryThreshold] = Seq(Expired, CriticalWarning, Warning, EarlyWarning)

  def fromString(stringFromMongo: String): Option[ExpiryThreshold] =
    allConditions.find(_.value == stringFromMongo)

  // Serialise to/from the string value (e.g. "WARNING") for MongoDB storage.
  implicit val format: Format[ExpiryThreshold] = new Format[ExpiryThreshold] {
    def reads(json: JsValue): JsResult[ExpiryThreshold] =
      json.validate[String].flatMap(s => fromString(s).map(JsSuccess(_)).getOrElse(JsError(s"Unknown threshold: $s")))
    def writes(t: ExpiryThreshold): JsValue             = JsString(t.value)
  }

}
