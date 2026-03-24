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

class PekkoTimer[T](scheduler: TimerScheduler[T]) {
  def startSingleTimer(msg: T, delay: FiniteDuration): Unit = scheduler.startSingleTimer(msg, delay)
}

object CertificateExpiryMonitorJob {

  val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm yyyy-MM-dd")
  private val CLASS_NAME            = getClass.getSimpleName.dropRight(1)
  private val JOB_ID                = "certificate-expiry-monitor-job"

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
  ): Behavior[CertificateExpiryMonitorJobCommand] = Behaviors.withTimers { timerScheduler =>
    val pekkoTimer = timer(timerScheduler)
    val schedule   = new CertificateCheckSchedule(config, certificateExpiry)

    logger.info(instanceId, CLASS_NAME, "apply", "Starting initial check")
    pekkoTimer.startSingleTimer(CheckExpiry, 1.minutes)

    running(certificateExpiry, pekkoTimer, timeProvider, schedule, certExpiryJobRepo)
  }

  private def running(
    certificateExpiry: LocalDateTime,
    pekkoTimer: PekkoTimer[CertificateExpiryMonitorJobCommand],
    timeProvider: TimeProvider,
    certificateCheckSchedule: CertificateCheckSchedule,
    repo: CertExpiryJobRepo
  )(implicit
    logger: BrmLogger,
    instanceId: UUID,
    ec: ExecutionContext
  ): Behavior[CertificateExpiryMonitorJobCommand] =
    Behaviors.receive { (_, message) =>
      message match {
        case CheckExpiry =>
          onCheckExpiry(pekkoTimer, timeProvider, certificateCheckSchedule, repo, certificateExpiry)
        case Terminate   => onTerminate()
      }
    }

  // determine threshold, attempt to claim, schedule next check
  private def onCheckExpiry(
                             pekkoTimer: PekkoTimer[CertificateExpiryMonitorJobCommand],
                             timeProvider: TimeProvider,
                             certificateCheckSchedule: CertificateCheckSchedule,
                             certExpiryJobRepo: CertExpiryJobRepo,
                             certificateExpiry: LocalDateTime
                           )(implicit
                             logger: BrmLogger,
                             instanceId: UUID,
                             ec: ExecutionContext
                           ): Behavior[CertificateExpiryMonitorJobCommand] = {
    val zonedNow = timeProvider.now
    val now      = zonedNow.toLocalDateTime
    val instant  = zonedNow.toInstant

    certificateCheckSchedule.currentThreshold(now) match {
      case Some(config: ThresholdConfig) =>
        attemptClaim(
          certExpiryJobRepo,
          config.threshold,
          config.checkInterval,
          instant,
          certificateCheckSchedule.getTimeUntilCertExpiry(now),
          certificateExpiry
        )
      case None =>
        logNoThresholdMatched(certificateExpiry)
    }

    pekkoTimer.startSingleTimer(CheckExpiry, 15.minutes)
    Behaviors.same
  }

  private def attemptClaim(
    repository: CertExpiryJobRepo,
    threshold: ExpiryThreshold,
    checkInterval: Duration,
    now: java.time.Instant,
    timeLeft: Duration,
    certificateExpiry: LocalDateTime
  )(implicit ec: ExecutionContext, logger: BrmLogger, instanceId: UUID): Unit =

    repository
      .shouldPerformCertExpiryCheck(JOB_ID, threshold, checkInterval, now)
      .map { shouldCheck =>
        val formattedExpiry = certificateExpiry.format(timeFormat)

        if (shouldCheck) { // key part, this instance is able to call the logCertificateExpiry method
          logger.info(
            instanceId,
            CLASS_NAME,
            "running",
            s"sending alert for threshold=${threshold.value} actualCertExpiryDate=$formattedExpiry"
          )

          logCertificateExpiry(
            timeLeft,
            certificateExpiry
          )

        } else {
          logger.info(
            instanceId,
            CLASS_NAME,
            "running",
            s"alert already handled for threshold=${threshold.value} actualCertExpiryDate=$formattedExpiry"
          )
        }
      }
      .recover { case e: Exception =>
        logger.error(s"error reading from mongo: $e")
      }

  private def onTerminate()(implicit
    logger: BrmLogger,
    instanceId: UUID
  ): Behavior[CertificateExpiryMonitorJobCommand] = {
    logger.info(
      instanceId,
      CLASS_NAME,
      "running",
      "Received application lifecycle shutdown hook - Terminating certificate expiry monitoring"
    )
    Behaviors.stopped
  }

  private def logNoThresholdMatched(
    certificateExpiry: LocalDateTime
  )(implicit logger: BrmLogger, instanceId: UUID): Unit =
    logger.info(
      instanceId,
      CLASS_NAME,
      "running",
      s"no threshold matched for actualCertExpiryDate=${certificateExpiry.format(timeFormat)}"
    )

  private def logCertificateExpiry(
    timeUntilCertExpiry: Duration,
    certificateExpiry: LocalDateTime
  )(implicit logger: BrmLogger, instanceId: UUID): Unit = {
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
