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

import uk.gov.hmrc.brm.config.GroAppConfig

import java.time.{Duration, LocalDateTime}
import javax.inject.Singleton

@Singleton
class CertificateCheckSchedule(conf: GroAppConfig, certificateExpiry: LocalDateTime) {

  val times: CertificateCheckTimes = conf.certificateTimes

  def getTimeUntilCertExpiry(now: LocalDateTime): Duration =
    Duration.between(now, certificateExpiry)

  def getCurrentCheckInterval(now: LocalDateTime): Option[Duration] = {
    val timeLeft: Duration = getTimeUntilCertExpiry(now)

    if (timeLeft.isNegative) {
      Some(times.certExpiryCriticalCheckIntervalHours)
    } else if (timeLeft.compareTo(times.certExpiryCriticalThresholdHours) <= 0) {
      Some(times.certExpiryCriticalCheckIntervalHours)
    } else if (timeLeft.compareTo(times.certExpiryWarningThresholdHours) <= 0) {
      Some(times.certExpiryWarningCheckIntervalHours)
    } else if (timeLeft.compareTo(times.certExpiryEarlyWarningThresholdHours) <= 0) {
      Some(times.certExpiryEarlyWarningCheckIntervalHours)
    } else {
      None
    }
  }

}
