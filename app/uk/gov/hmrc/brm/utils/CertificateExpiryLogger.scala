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

package uk.gov.hmrc.brm.utils

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import uk.gov.hmrc.brm.config.GroAppConfig

import java.time.format.DateTimeFormatter
import java.time.{Duration, LocalDateTime}
import scala.concurrent.duration._

// wraps .startSingleTimer calls to Pekko's TimerScheduler to allow assertions in tests
class PekkoTimer[T](scheduler: TimerScheduler[T]) {
   def startSingleTimer(msg: T, delay: FiniteDuration): Unit = scheduler.startSingleTimer(msg, delay)
}

object CertificateExpiryLogger {

  sealed trait LoggerCommand
  case object CheckExpiry extends LoggerCommand
  case object Stop extends LoggerCommand

  private val CLASS_NAME = getClass.getSimpleName

  val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm yyyy-MM-dd")

  def apply(
    certificateExpiry: LocalDateTime,
    groAppConfig: GroAppConfig,
    timeProvider: TimeProvider,
    timer: TimerScheduler[LoggerCommand] => PekkoTimer[LoggerCommand] = new PekkoTimer(_)
  )(implicit
    logger: BrmLogger
  ): Behavior[LoggerCommand] =
    Behaviors.withTimers { timerScheduler =>
      val t = timer(timerScheduler)
      logger.info(CLASS_NAME, "Starting initial check")
      t.startSingleTimer(CheckExpiry, 0.minutes) // start the initial check
      running(certificateExpiry, t, timeProvider, groAppConfig)
    }

  private def running(
    certificateExpiry: LocalDateTime,
    timerScheduler: PekkoTimer[LoggerCommand],
    timeProvider: TimeProvider,
    groAppConfig: GroAppConfig
  )(implicit logger: BrmLogger): Behavior[LoggerCommand] =
    Behaviors.receiveMessage {
      case CheckExpiry =>
        val now = timeProvider.now

        val nextCheckIntervalMinutes: FiniteDuration =
          FiniteDuration(
            getNextCertificateCheckIntervalMinutes(certificateExpiry, now.toLocalDateTime, groAppConfig),
            MINUTES
          )

        val nextCheckTime = now.plusNanos(nextCheckIntervalMinutes.toNanos)

        logger.info(
          CLASS_NAME,
          s"Setting next check interval to ${nextCheckIntervalMinutes.toHours} hours at ${nextCheckTime.format(timeFormat)}"
        )

        timerScheduler.startSingleTimer(CheckExpiry, nextCheckIntervalMinutes)

        Behaviors.same
      case Stop        =>
        logger.info("Stopping certificate expiry monitoring")
        Behaviors.stopped
    }

  // Derives the next interval to check the certificate's expiry time against our configured alert time thresholds.
  // If the expiry time is within any of our alert windows, the output log is caught and pushed to team-ddcels-alert slack.
  // Thresholds are specified with increasing severity and shorter certificate check interval times to raise concern.

  // | <- Early Warning Window -> | <- Warning Window -> | <- Critical Warning Window -> | Expired |
  // |                            |                      |
  // └─ Early Warning Threshold   └─  Warning Threshold  └─ Critical Threshold
  def getNextCertificateCheckIntervalMinutes(
    certificateExpiry: LocalDateTime,
    now: LocalDateTime,
    conf: GroAppConfig
  )(implicit logger: BrmLogger): Long = {

    val earlyWarningThresholdHours = Duration.ofHours(conf.certExpiryEarlyWarningThresholdHours)

    val timeUntilCertExpiry = Duration.between(now, certificateExpiry)

    if (isBeforeEarlyWarningWindow(timeUntilCertExpiry, earlyWarningThresholdHours)) {
      val timeUntilEarlyWarningWindow = timeUntilCertExpiry.minus(earlyWarningThresholdHours).toMinutes
      val windowInterval              = Duration.ofHours(conf.certExpiryEarlyWarningCheckIntervalHours).toMinutes

      getSynchronisedCheckIntervalMinutes(timeUntilEarlyWarningWindow, windowInterval)
    } else { // within early warning window or less, log expiry message & get next check interval

      logCertificateExpiry(timeUntilCertExpiry, certificateExpiry)

      val warningThreshold  = Duration.ofHours(conf.certExpiryWarningThresholdHours)
      val criticalThreshold = Duration.ofHours(conf.certExpiryCriticalThresholdHours)

      if (isWithinEarlyWarningWindow(timeUntilCertExpiry, earlyWarningThresholdHours, warningThreshold)) {
        val timeUntilWarningThreshold = timeUntilCertExpiry.minus(warningThreshold)
        val earlyWarningCheckInterval = Duration.ofHours(conf.certExpiryEarlyWarningCheckIntervalHours).toMinutes

        getSynchronisedCheckIntervalMinutes(timeUntilWarningThreshold.toMinutes, earlyWarningCheckInterval)

      } else if (isWithinWarningWindow(timeUntilCertExpiry, warningThreshold, criticalThreshold)) {
        val timeUntilCriticalThreshold = timeUntilCertExpiry.minus(criticalThreshold)
        val warningCheckInterval       = Duration.ofHours(conf.certExpiryWarningCheckIntervalHours).toMinutes

        getSynchronisedCheckIntervalMinutes(timeUntilCriticalThreshold.toMinutes, warningCheckInterval)

      } else { // within critical window or expired
        Duration.ofHours(conf.certExpiryCriticalCheckIntervalHours).toMinutes
      }
    }
  }

  // if we're closer to a window than our current interval, schedule next check at the start of the upcoming window
  private def getSynchronisedCheckIntervalMinutes(timeUntilNextWindow: Long, windowInterval: Long): Long =
    if (timeUntilNextWindow < windowInterval) {
      timeUntilNextWindow
    } else {
      windowInterval
    }

  private def isBeforeEarlyWarningWindow(timeUntilExpiry: Duration, earlyWarningTime: Duration): Boolean =
    timeUntilExpiry.minus(earlyWarningTime).toMinutes > 0

  private def isWithinEarlyWarningWindow(
    timeUntilExpiry: Duration,
    earlyWarningTime: Duration,
    warningTime: Duration
  ): Boolean =
    timeUntilExpiry.minus(earlyWarningTime).toMinutes <= 0 && timeUntilExpiry.minus(warningTime).toMinutes > 0

  private def isWithinWarningWindow(
    timeUntilExpiry: Duration,
    warningTime: Duration,
    criticalWarningTime: Duration
  ): Boolean =
    timeUntilExpiry.minus(warningTime).toMinutes <= 0 && timeUntilExpiry.minus(criticalWarningTime).toMinutes > 0

  private def logCertificateExpiry(timeUntilCertExpiry: Duration, certificateExpiry: LocalDateTime)(implicit
    logger: BrmLogger
  ): Unit = {

    val formattedExpiry = certificateExpiry.format(timeFormat)

    if (timeUntilCertExpiry.toDays > 0) {
      logger.warn(
        CLASS_NAME,
        "logCertificateExpiry",
        s"Certificate expires in ${timeUntilCertExpiry.toDays} days at $formattedExpiry"
      )
    } else if (!timeUntilCertExpiry.isNegative) {
      logger.warn(
        CLASS_NAME,
        "logCertificateExpiry",
        s"Certificate expires in ${timeUntilCertExpiry.toHours} hours at $formattedExpiry"
      )
    } else {
      logger.warn(CLASS_NAME, "logCertificateExpiry", s"Certificate expired at $formattedExpiry")
    }
  }

}
