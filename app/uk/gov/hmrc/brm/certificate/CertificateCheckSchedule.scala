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

import uk.gov.hmrc.brm.certificate.ExpiryThreshold.{CriticalWarning, EarlyWarning, Expired, Warning}
import uk.gov.hmrc.brm.config.GroAppConfig
import uk.gov.hmrc.brm.models.ThresholdStatus

import java.time.{Duration, LocalDateTime}
import javax.inject.Singleton
import scala.concurrent.duration.{FiniteDuration, MINUTES}

@Singleton
class CertificateCheckSchedule(conf: GroAppConfig, certificateExpiry: LocalDateTime) {

  val times = conf.certificateTimes

  def getTimeUntilCertExpiry(now: LocalDateTime): Duration =
    Duration.between(now, certificateExpiry)

  def currentThreshold(now: LocalDateTime): Option[ExpiryThreshold] = {
    val hoursLeft = getTimeUntilCertExpiry(now).toHours

    Seq(Expired, CriticalWarning, Warning, EarlyWarning)
      .find(expiryThreshold => hoursLeft <= times.thresholdHours(expiryThreshold).toHours)
  }
}
