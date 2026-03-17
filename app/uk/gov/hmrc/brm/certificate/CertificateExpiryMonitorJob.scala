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
import uk.gov.hmrc.brm.certificate.ExpiryThreshold.{CriticalWarning, EarlyWarning, Expired, Warning}
import uk.gov.hmrc.brm.config.GroAppConfig
import uk.gov.hmrc.brm.repositories.CertExpiryJobRepo
import uk.gov.hmrc.brm.time.TimeProvider
import uk.gov.hmrc.brm.utils.BrmLogger

import java.time.format.DateTimeFormatter
import java.time.{Duration, Instant, LocalDateTime}
import java.util.UUID
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

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
             timer: TimerScheduler[CertificateExpiryMonitorJobCommand] => PekkoTimer[CertificateExpiryMonitorJobCommand] =
             new PekkoTimer(_),
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
      pekkoTimer.startSingleTimer(CheckExpiry, 1.minutes) // starts after a minute

      running(certificateExpiry, pekkoTimer, timeProvider, certificateCheckSchedule, certExpiryJobRepo)
    }

  private def running(
                       certificateExpiry: LocalDateTime,
                       timerScheduler: PekkoTimer[CertificateExpiryMonitorJobCommand],
                       timeProvider: TimeProvider,
                       certificateCheckSchedule: CertificateCheckSchedule,
                       certExpiryJobRepo: CertExpiryJobRepo
                     )(implicit logger: BrmLogger, instanceId: UUID, ec: ExecutionContext): Behavior[CertificateExpiryMonitorJobCommand] =
    Behaviors.receiveMessage {
      case CheckExpiry =>
        val now = timeProvider.now.toLocalDateTime
        val jobId = "certificate-expiry-monitor-job"
        val thresholdValues: Option[(ExpiryThreshold, Duration)] = getThresholdAndIntervalTime(now, certificateCheckSchedule)
        thresholdValues match {
          case Some((threshold, leftTime)) =>

            val expiryDateInstant: Instant =
              Instant.now().plus(leftTime).truncatedTo(java.time.temporal.ChronoUnit.SECONDS)

            for {
              exists <- certExpiryJobRepo.getAlertDetails(jobId, expiryDateInstant, threshold.value)
              result <- if (!exists)
                certExpiryJobRepo.insertAlertDetails(jobId, expiryDateInstant, threshold.value)
              else
                Future.successful(false)
            } yield {
              if (result) {
                logger.info(
                  instanceId,
                  CLASS_NAME,
                  "running",
                  s"sending alerts for threshold=${threshold.value} actualCertExpiryDate=$certificateExpiry mongo doc expiryDate=$expiryDateInstant"
                )
                logCertificateExpiry(
                  certificateCheckSchedule.getTimeUntilCertExpiry(now),
                  certificateExpiry
                )
              } else {
                logger.info(
                  instanceId,
                  CLASS_NAME,
                  "running",
                  s"alert already sent for threshold=${threshold.value} actualCertExpiryDate=$certificateExpiry mongo doc expiryDate=$expiryDateInstant"
                )
              }
            }
          case None =>
            Future.successful(
              logger.info(
                instanceId,
                CLASS_NAME,
                "running",
                s"no threshold matched for actualCertExpiryDate=$certificateExpiry"
              )
            )
        }

        timerScheduler.startSingleTimer(CheckExpiry, 1.minutes) //runs every 15 minutes

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

  private def getThresholdAndIntervalTime(
                                       now: LocalDateTime,
                                       schedule: CertificateCheckSchedule
                                     ): Option[(ExpiryThreshold, Duration)] = {

    val timeLeft = schedule.getTimeUntilCertExpiry(now)
    val hoursLeft = timeLeft.toHours

    if (timeLeft.isNegative) Some(Expired, timeLeft)
    else if (hoursLeft <= schedule.times.certExpiryCriticalThresholdHours.toHours)
      Some(CriticalWarning, schedule.times.certExpiryCriticalCheckIntervalHours)
    else if (hoursLeft <= schedule.times.certExpiryWarningThresholdHours.toHours)
      Some(Warning, schedule.times.certExpiryWarningCheckIntervalHours)
    else if (hoursLeft <= schedule.times.certExpiryEarlyWarningThresholdHours.toHours)
      Some(EarlyWarning, schedule.times.certExpiryEarlyWarningCheckIntervalHours)
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
