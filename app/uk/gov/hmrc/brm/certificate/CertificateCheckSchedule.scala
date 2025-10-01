/*
 * Copyright 2025 HM Revenue & Customs
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
import scala.concurrent.duration.{FiniteDuration, MINUTES}

@Singleton
class CertificateCheckSchedule(conf: GroAppConfig, certificateExpiry: LocalDateTime) {

  private val times = conf.certificateTimes
  private val minCheckDurationMinutes: Int = 60

  // Derives the next interval to check the certificate's expiry time against our configured alert time thresholds.
  // If the expiry time is within any of our alert windows, the output log is caught and pushed to team-ddcels-alert slack.
  // Thresholds are specified with increasing severity and shorter certificate check interval times to raise concern.

  // | <- Early Warning Window -> | <- Warning Window -> | <- Critical Warning Window -> | Expired |
  // |                            |                      |
  // └─ Early Warning Threshold   └─  Warning Threshold  └─ Critical Threshold
  private def getNextCertificateCheckIntervalMinutes(now: LocalDateTime): Long = {

    val timeUntilCertExpiry = getTimeUntilCertExpiry(now)

    if (isBeforeEarlyWarningWindow(now)) {
      val timeUntilEarlyWarningWindow = timeUntilCertExpiry.minus(times.certExpiryEarlyWarningThresholdHours)
      val windowInterval              = times.certExpiryEarlyWarningCheckIntervalHours

      getSynchronisedCheckIntervalMinutes(timeUntilEarlyWarningWindow, windowInterval)
    } else {

      if (isWithinEarlyWarningWindow(timeUntilCertExpiry)) {
        val timeUntilWarningThreshold = timeUntilCertExpiry.minus(times.certExpiryWarningThresholdHours)

        getSynchronisedCheckIntervalMinutes(timeUntilWarningThreshold, times.certExpiryEarlyWarningCheckIntervalHours)

      } else if (isWithinWarningWindow(timeUntilCertExpiry)) {
        val timeUntilCriticalThreshold = timeUntilCertExpiry.minus(times.certExpiryCriticalThresholdHours)

        getSynchronisedCheckIntervalMinutes(timeUntilCriticalThreshold, times.certExpiryWarningCheckIntervalHours)

      } else { // within critical window or expired
        times.certExpiryCriticalCheckIntervalHours.toMinutes
      }
    }
  }

  def getTimeUntilCertExpiry(now: LocalDateTime): Duration = Duration.between(now, certificateExpiry)

  // if we're closer to a window than our current interval, schedule next check at the start of the upcoming window
  private def getSynchronisedCheckIntervalMinutes(timeUntilNextWindow: Duration, windowInterval: Duration): Long =
    if (timeUntilNextWindow.toMinutes < windowInterval.toMinutes) {
      timeUntilNextWindow.toMinutes
    } else {
      windowInterval.toMinutes
    }

  private def isBeforeEarlyWarningWindow(now: LocalDateTime): Boolean =
    Duration.between(now, certificateExpiry).minus(times.certExpiryEarlyWarningThresholdHours).toMinutes > 0

  def isPastEarlyWarningWindow(now: LocalDateTime): Boolean = !isBeforeEarlyWarningWindow(now)

  private def isWithinEarlyWarningWindow(timeUntilExpiry: Duration): Boolean =
    timeUntilExpiry.minus(times.certExpiryEarlyWarningThresholdHours).toMinutes <= 0 && timeUntilExpiry
      .minus(times.certExpiryWarningThresholdHours)
      .toMinutes > 0

  private def isWithinWarningWindow(timeUntilExpiry: Duration): Boolean =
    timeUntilExpiry.minus(times.certExpiryWarningThresholdHours).toMinutes <= 0 && timeUntilExpiry
      .minus(times.certExpiryCriticalThresholdHours)
      .toMinutes > 0

  def getNextCheckIntervalDurationMinutes(now: LocalDateTime): FiniteDuration = {
    val calculatedNextCheckIntervalMinutes = getNextCertificateCheckIntervalMinutes(now)

    val nextCheckMinutes = if (calculatedNextCheckIntervalMinutes < minCheckDurationMinutes) {
      minCheckDurationMinutes
    } else {
      calculatedNextCheckIntervalMinutes
    }

    FiniteDuration(
      nextCheckMinutes,
      MINUTES
    )
  }

}
