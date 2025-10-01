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

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import uk.gov.hmrc.brm.config.GroAppConfig
import uk.gov.hmrc.brm.time.{CertificateCheckSchedule, TimeProvider}
import uk.gov.hmrc.brm.utils.BrmLogger

import java.time.format.DateTimeFormatter
import java.time.{Duration, LocalDateTime}
import scala.concurrent.duration._

// wraps startSingleTimer calls to Pekko's TimerScheduler to allow assertions in tests
class PekkoTimer[T](scheduler: TimerScheduler[T]) {
  def startSingleTimer(msg: T, delay: FiniteDuration): Unit = scheduler.startSingleTimer(msg, delay)
}

object CertificateExpiryMonitorJob {

  private val CLASS_NAME = getClass.getSimpleName

  val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm yyyy-MM-dd")

  def apply(
    certificateExpiry: LocalDateTime,
    timeProvider: TimeProvider,
    config: GroAppConfig,
    timer: TimerScheduler[CertificateExpiryMonitorJobCommand] => PekkoTimer[CertificateExpiryMonitorJobCommand] =
      new PekkoTimer(_)
  )(implicit
    logger: BrmLogger
  ): Behavior[CertificateExpiryMonitorJobCommand] =
    Behaviors.withTimers { timerScheduler =>
      val pekkoTimer               = timer(timerScheduler)
      val certificateCheckSchedule = new CertificateCheckSchedule(config, certificateExpiry)

      logger.info(CLASS_NAME, "apply", "Starting initial check")
      pekkoTimer.startSingleTimer(CheckExpiry, 1.minutes)
      running(certificateExpiry, pekkoTimer, timeProvider, certificateCheckSchedule)
    }

  private def running(
    certificateExpiry: LocalDateTime,
    timerScheduler: PekkoTimer[CertificateExpiryMonitorJobCommand],
    timeProvider: TimeProvider,
    certificateCheckSchedule: CertificateCheckSchedule
  )(implicit logger: BrmLogger): Behavior[CertificateExpiryMonitorJobCommand] =
    Behaviors.receiveMessage {
      case CheckExpiry =>
        val now = timeProvider.now.toLocalDateTime

        if (certificateCheckSchedule.isPastEarlyWarningWindow(now)) {
          logCertificateExpiry(certificateCheckSchedule.getTimeUntilCertExpiry(now), certificateExpiry)
        }

        val nextCheckIntervalMinutes: FiniteDuration = certificateCheckSchedule.getNextCheckIntervalDurationMinutes(now)

        val nextCheckTime = now.plusNanos(nextCheckIntervalMinutes.toNanos)

        logger.info(
          CLASS_NAME,
          "running",
          s"Setting next check interval to ${nextCheckIntervalMinutes.toHours} hours at ${nextCheckTime.format(timeFormat)}"
        )

        timerScheduler.startSingleTimer(CheckExpiry, nextCheckIntervalMinutes)

        Behaviors.same
      case Terminate        =>
        logger.info(CLASS_NAME, "running", "Terminating certificate expiry monitoring")
        Behaviors.stopped
    }

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
