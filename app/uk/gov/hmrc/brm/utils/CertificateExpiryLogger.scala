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

import java.time.{Duration, Instant, LocalDateTime}
import scala.concurrent.duration._

object CertificateExpiryLogger {

  sealed trait Command
  case object CheckExpiry extends Command
  case object Stop extends Command

  def apply(certificateExpiry: LocalDateTime, groAppConfig: GroAppConfig): Behavior[Command] =
    Behaviors.withTimers { timerScheduler =>
      timerScheduler.startSingleTimer(CheckExpiry, 0.minutes) // start the initial check
      running(certificateExpiry, timerScheduler, groAppConfig)
    }

  private def running(
    certificateExpiry: LocalDateTime,
    timerScheduler: TimerScheduler[Command],
    groAppConfig: GroAppConfig
  ): Behavior[Command] =

    Behaviors.receiveMessage {
      case CheckExpiry =>
        val now                           = Instant.now()
        val timeUntilCertExpiry: Duration = Duration.between(now, certificateExpiry)

        val nextCheckInterval: FiniteDuration =
          getNextCertificateCheckInterval(certificateExpiry, timeUntilCertExpiry, groAppConfig)

        timerScheduler.startSingleTimer(CheckExpiry, nextCheckInterval)

        Behaviors.same
      case Stop        =>
        BrmLogger.info("Stopping certificate expiry monitoring")
        Behaviors.stopped
    }

  // Derives the next interval to check our certificate's expiry time against our configured alert windows.
  // If the expiry time is within any of our alert windows, the output log is caught and pushed to team-ddcels-alert slack.
  private def getNextCertificateCheckInterval(
    certificateExpiry: LocalDateTime,
    timeUntilCertExpiry: Duration,
    config: GroAppConfig
  ): FiniteDuration = {

    val hoursTillExpiry = timeUntilCertExpiry.toHours

    if (hoursTillExpiry > config.certExpiryEarlyWarningHours) {
      7.days
    } else {

      logCertificateExpiry(timeUntilCertExpiry, certificateExpiry)

      if (hoursTillExpiry <= config.certExpiryEarlyWarningHours && hoursTillExpiry > config.certExpiryWarningHours) {
        7.days
      } else if (hoursTillExpiry <= config.certExpiryWarningHours && hoursTillExpiry > config.certExpiryCriticalHours) {
        1.day
      } else { // in critical window
        1.hour
      }
    }

  }

  private def logCertificateExpiry(timeUntilCertExpiry: Duration, certificateExpiry: LocalDateTime): Unit =
    if (timeUntilCertExpiry.toDays > 0) {
      BrmLogger.warn(s"Certificate expires in ${timeUntilCertExpiry.toDays} days at $certificateExpiry")
    } else if (!timeUntilCertExpiry.isNegative) {
      BrmLogger.warn(s"Certificate expires in ${timeUntilCertExpiry.toHours} hours at $certificateExpiry")
    } else {
      BrmLogger.warn(s"Certificate expired at $certificateExpiry")
    }

}
