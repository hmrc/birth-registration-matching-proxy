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

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import uk.gov.hmrc.brm.config.GroAppConfig
import uk.gov.hmrc.brm.repositories.CertExpiryJobRepo
import uk.gov.hmrc.brm.time.TimeProvider
import uk.gov.hmrc.brm.utils.BrmLogger

import java.time.format.DateTimeFormatter
import java.time.{Duration, LocalDateTime}
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

// wraps startSingleTimer calls to Pekko's TimerScheduler to allow assertions in tests
class PekkoTimer[T](scheduler: TimerScheduler[T]) {
  def startSingleTimer(msg: T, delay: FiniteDuration): Unit = scheduler.startSingleTimer(msg, delay)
}

object CertificateExpiryMonitorJob {

  val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm yyyy-MM-dd")
  private val CLASS_NAME = getClass.getSimpleName.dropRight(1)

  def apply(
             certificateExpiry: LocalDateTime,
             timeProvider: TimeProvider,
             config: GroAppConfig,
             timer: TimerScheduler[CertificateExpiryMonitorJobCommand] => PekkoTimer[CertificateExpiryMonitorJobCommand] = new PekkoTimer(_),
             certExpiryJobRepo: CertExpiryJobRepo
           )(implicit
             logger: BrmLogger,
             instanceId: UUID,
             ec: ExecutionContext
           ): Behavior[CertificateExpiryMonitorJobCommand] =
    Behaviors.withTimers { timerScheduler =>
      val pekkoTimer = timer(timerScheduler)
      val certificateCheckSchedule = new CertificateCheckSchedule(config, certificateExpiry)

      logger.info(instanceId, CLASS_NAME, "apply", "Starting initial check")
      pekkoTimer.startSingleTimer(CheckExpiry, 1.minutes) //starts after a minute

      running(certificateExpiry, pekkoTimer, timeProvider, certificateCheckSchedule, certExpiryJobRepo)
    }

  private def running(
                       certificateExpiry: LocalDateTime,
                       timerScheduler: PekkoTimer[CertificateExpiryMonitorJobCommand],
                       timeProvider: TimeProvider,
                       certificateCheckSchedule: CertificateCheckSchedule,
                       certExpiryJobRepo: CertExpiryJobRepo
                     )(implicit logger: BrmLogger, instanceId: UUID,  ec: ExecutionContext): Behavior[CertificateExpiryMonitorJobCommand] =
    Behaviors.receiveMessage {
      case CheckExpiry =>
        val now = timeProvider.now.toLocalDateTime
        val nowEpochMs = timeProvider.now.toInstant.toEpochMilli
        val jobId = "certificate-expiry-monitor-job"

        val thresholdValues: Option[String] = getThresholdInStrFormat(now, certificateCheckSchedule)
        logger.info(instanceId, CLASS_NAME, "running", s"going to check and insert in mongo this  val $thresholdValues")
        thresholdValues.foreach { threshold =>
          certExpiryJobRepo
            .markAlertSent(jobId, certificateExpiry.toString, threshold, nowEpochMs)
            .map {
              case true =>
                logger.info(instanceId, CLASS_NAME, "running", s"sending alerts for threshold=$threshold expiry=$certificateExpiry")
                logCertificateExpiry(
                  certificateCheckSchedule.getTimeUntilCertExpiry(now),
                  certificateExpiry
                )
              case false =>
                logger.info(instanceId, CLASS_NAME, "running", s"Alert already sent threshold=$threshold expiry=$certificateExpiry")

            }.recover { e =>
              logger.error(
                s"error in running ${e.getMessage}"
              )
            }
        }

        val nextCheckIntervalMinutes: FiniteDuration = certificateCheckSchedule.getNextCheckIntervalDurationMinutes(now)
        val nextCheckTime = now.plusNanos(nextCheckIntervalMinutes.toNanos)

        logger.info(
          instanceId,
          CLASS_NAME,
          "running",
          s"Setting next check interval to ${nextCheckIntervalMinutes.toHours} hours at ${nextCheckTime.format(timeFormat)} ${timeProvider.zoneId.toString}"
        )

        timerScheduler.startSingleTimer(CheckExpiry, nextCheckIntervalMinutes)

        Behaviors.same
      case Terminate =>
        logger.info(
          instanceId,
          CLASS_NAME,
          "running",
          "Received application lifecycle shutdown hook - Terminating certificate expiry monitoring"
        )

        Behaviors.stopped
    }


  private def getThresholdInStrFormat(
                                       now: LocalDateTime,
                                       schedule: CertificateCheckSchedule
                                     ): Option[String] = {

    val timeLeft = schedule.getTimeUntilCertExpiry(now)
    val hoursLeft = timeLeft.toHours

    if (timeLeft.isNegative) Some("expired")
    else if (hoursLeft <= schedule.times.certExpiryCriticalThresholdHours.toHours) Some("critical")
    else if (hoursLeft <= schedule.times.certExpiryWarningThresholdHours.toHours) Some("warning")
    else if (hoursLeft <= schedule.times.certExpiryEarlyWarningThresholdHours.toHours) Some("early")
    else None
  }


  private def logCertificateExpiry(timeUntilCertExpiry: Duration, certificateExpiry: LocalDateTime)(implicit
                                                                                                    logger: BrmLogger,
                                                                                                    instanceId: UUID
  ): Unit = {

    val formattedExpiry = certificateExpiry.format(timeFormat)

    if (timeUntilCertExpiry.toDays > 0) {
      logger.warn(
        instanceId,
        CLASS_NAME,
        "logCertificateExpiry",
        s"Certificate expires in ${timeUntilCertExpiry.toDays} days at $formattedExpiry"
      )
    } else if (!timeUntilCertExpiry.isNegative) {
      logger.warn(
        instanceId,
        CLASS_NAME,
        "logCertificateExpiry",
        s"Certificate expires in ${timeUntilCertExpiry.toHours} hours at $formattedExpiry"
      )
    } else {
      logger.warn(instanceId, CLASS_NAME, "logCertificateExpiry", s"Certificate expired at $formattedExpiry")
    }
  }

}
