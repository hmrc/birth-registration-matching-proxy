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

object CertificateExpiryLogger {

  sealed trait Command
  case object CheckExpiry extends Command
  case object Stop extends Command

  private val CLASS_NAME = getClass.getSimpleName

  val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm yyyy-MM-dd")

  def apply(certificateExpiry: LocalDateTime, groAppConfig: GroAppConfig)(implicit
    logger: BrmLogger
  ): Behavior[Command] =
    Behaviors.withTimers { timerScheduler =>
      logger.info(CLASS_NAME, "Starting initial check")
      timerScheduler.startSingleTimer(CheckExpiry, 0.minutes) // start the initial check
      running(certificateExpiry, timerScheduler, groAppConfig)
    }

  private def running(
    certificateExpiry: LocalDateTime,
    timerScheduler: TimerScheduler[Command],
    groAppConfig: GroAppConfig
  )(implicit logger: BrmLogger): Behavior[Command] =

    Behaviors.receiveMessage {
      case CheckExpiry =>
        val now                           = LocalDateTime.now()
        val timeUntilCertExpiry: Duration = Duration.between(now, certificateExpiry)

        val nextCheckInterval: FiniteDuration =
          getNextCertificateCheckInterval(certificateExpiry, timeUntilCertExpiry, groAppConfig)

        val nextCheckTime = now.plusNanos(nextCheckInterval.toNanos)

        logger.info(
          CLASS_NAME,
          s"Setting next check interval to $nextCheckInterval at ${nextCheckTime.format(timeFormat)}"
        )

        timerScheduler.startSingleTimer(CheckExpiry, nextCheckInterval)

        Behaviors.same
      case Stop        =>
        logger.info("Stopping certificate expiry monitoring")
        Behaviors.stopped
    }

  // Derives the next interval to check our certificate's expiry time against our configured alert windows.
  // If the expiry time is within any of our alert windows, the output log is caught and pushed to team-ddcels-alert slack.
  private def getNextCertificateCheckInterval(
    certificateExpiry: LocalDateTime,
    timeUntilCertExpiry: Duration,
    conf: GroAppConfig
  )(implicit logger: BrmLogger): FiniteDuration = {

    val hoursTillExpiry = timeUntilCertExpiry.toHours

    if (hoursTillExpiry > conf.certExpiryEarlyWarningHours) {
      FiniteDuration(conf.certExpiryEarlyWarningCheckIntervalHours, HOURS)
    } else {

      logCertificateExpiry(timeUntilCertExpiry, certificateExpiry)

      if (hoursTillExpiry <= conf.certExpiryEarlyWarningHours && hoursTillExpiry > conf.certExpiryWarningHours) {
        FiniteDuration(conf.certExpiryEarlyWarningCheckIntervalHours, HOURS)
      } else if (hoursTillExpiry <= conf.certExpiryWarningHours && hoursTillExpiry > conf.certExpiryCriticalHours) {
        FiniteDuration(conf.certExpiryWarningCheckIntervalHours, HOURS)
      } else { // in critical window
        FiniteDuration(conf.certExpiryCriticalCheckIntervalHours, HOURS)
      }
    }

  }

  private def logCertificateExpiry(timeUntilCertExpiry: Duration, certificateExpiry: LocalDateTime)(implicit
    logger: BrmLogger
  ): Unit =
    if (timeUntilCertExpiry.toDays > 0) {
      logger.warn(
        CLASS_NAME,
        "logCertificateExpiry",
        s"Certificate expires in ${timeUntilCertExpiry.toDays} days at $certificateExpiry"
      )
    } else if (!timeUntilCertExpiry.isNegative) {
      logger.warn(
        CLASS_NAME,
        "logCertificateExpiry",
        s"Certificate expires in ${timeUntilCertExpiry.toHours} hours at $certificateExpiry"
      )
    } else {
      logger.warn(CLASS_NAME, "logCertificateExpiry", s"Certificate expired at $certificateExpiry")
    }

}
