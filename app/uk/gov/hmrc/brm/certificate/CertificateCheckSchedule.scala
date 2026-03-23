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

  private val minCheckDurationMinutes: Int = 60

  def getTimeUntilCertExpiry(now: LocalDateTime): Duration =
    Duration.between(now, certificateExpiry)

  /**
   * Determines which alert threshold the certificate currently falls into,
   * along with the configured check interval for that threshold.
   *
   * Returns None if the certificate expiry is still outside all warning windows.
   */
  def currentThreshold(now: LocalDateTime): Option[ThresholdStatus] = {
    val timeLeft  = getTimeUntilCertExpiry(now)
    val hoursLeft = timeLeft.toHours

    // Ordered most severe first — the first threshold whose boundary
    // contains the current time wins.
    // Expired has thresholdHours of ZERO, so it only matches when
    // hoursLeft <= 0, i.e. the certificate has expired.
    val orderedThresholds = Seq(Expired, CriticalWarning, Warning, EarlyWarning)

    val matchedThreshold = orderedThresholds.find { threshold =>
      val boundary = times.thresholdHours(threshold).toHours
      hoursLeft <= boundary
    }

    matchedThreshold.map { threshold =>
      val interval = times.checkInterval(threshold)
      ThresholdStatus(threshold, interval)
    }
  }

  /**
   * Computes the next timer interval based on the current threshold.
   *
   * Within a warning window, the interval is the shorter of:
   *   - the configured check interval for that window
   *   - the time remaining until the next (more severe) window begins
   *
   * This synchronises checks to window boundaries so that escalations
   * are detected promptly rather than waiting for a full interval to elapse.
   */
  def getNextCheckIntervalDurationMinutes(now: LocalDateTime): FiniteDuration = {
    val timeLeft = getTimeUntilCertExpiry(now)

    val minutes = currentThreshold(now) match {
      case None =>
        // Before any warning window — schedule to arrive at the early warning boundary
        val timeUntilEarlyWarning = timeLeft.minus(times.thresholdHours(EarlyWarning))
        val earlyWarningInterval  = times.checkInterval(EarlyWarning)
        synchronise(timeUntilEarlyWarning, earlyWarningInterval)

      case Some(ThresholdStatus(EarlyWarning, _)) =>
        // Within early warning — synchronise to the warning boundary
        val timeUntilWarning     = timeLeft.minus(times.thresholdHours(Warning))
        val earlyWarningInterval = times.checkInterval(EarlyWarning)
        synchronise(timeUntilWarning, earlyWarningInterval)

      case Some(ThresholdStatus(Warning, _)) =>
        // Within warning — synchronise to the critical boundary
        val timeUntilCritical = timeLeft.minus(times.thresholdHours(CriticalWarning))
        val warningInterval   = times.checkInterval(Warning)
        synchronise(timeUntilCritical, warningInterval)

      case Some(ThresholdStatus(CriticalWarning | Expired, _)) =>
        // Critical or expired — check at the fastest configured interval
        times.checkInterval(CriticalWarning).toMinutes
    }

    val clamped = if (minutes < minCheckDurationMinutes) minCheckDurationMinutes else minutes
    FiniteDuration(clamped, MINUTES)
  }

  // If we're closer to the next window than our current interval, schedule
  // the check at the window boundary instead so we detect the escalation promptly.
  private def synchronise(timeUntilNextWindow: Duration, windowInterval: Duration): Long =
    if (timeUntilNextWindow.toMinutes < windowInterval.toMinutes) {
      timeUntilNextWindow.toMinutes
    } else {
      windowInterval.toMinutes
    }
}
