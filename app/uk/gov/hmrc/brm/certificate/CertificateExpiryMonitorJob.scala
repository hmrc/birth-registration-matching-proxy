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
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import uk.gov.hmrc.brm.config.GroAppConfig
import uk.gov.hmrc.brm.models.ThresholdStatus
import uk.gov.hmrc.brm.repositories.CertExpiryJobRepo
import uk.gov.hmrc.brm.time.TimeProvider
import uk.gov.hmrc.brm.utils.BrmLogger

import java.time.format.DateTimeFormatter
import java.time.{Duration, LocalDateTime}
import java.util.UUID
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

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
    Behaviors.receive { (context, message) =>
      message match {
        case CheckExpiry    =>
          onCheckExpiry(context, pekkoTimer, timeProvider, certificateCheckSchedule, repo, certificateExpiry)
        case r: AlertResult => onAlertResult(r, certificateExpiry)
        case Terminate      => onTerminate()
      }
    }

  // determine threshold, attempt to claim, schedule next check
  private def onCheckExpiry(
    context: ActorContext[CertificateExpiryMonitorJobCommand],
    pekkoTimer: PekkoTimer[CertificateExpiryMonitorJobCommand],
    timeProvider: TimeProvider,
    certificateCheckSchedule: CertificateCheckSchedule,
    repo: CertExpiryJobRepo,
    certificateExpiry: LocalDateTime
  )(implicit
    logger: BrmLogger,
    instanceId: UUID,
    ec: ExecutionContext
  ): Behavior[CertificateExpiryMonitorJobCommand] = {
    val now = timeProvider.now.toLocalDateTime

    certificateCheckSchedule.currentThreshold(now) match {
      case Some(ThresholdStatus(threshold, interval)) =>
        val instant = timeProvider.now.toInstant
        attemptClaim(context, repo, threshold, interval, instant, certificateCheckSchedule.getTimeUntilCertExpiry(now))

      case None                        =>
        logNoThresholdMatched(certificateExpiry)
    }

    scheduleNextCheck(pekkoTimer, certificateCheckSchedule, now)
    Behaviors.same
  }

  private def attemptClaim(
    context: ActorContext[CertificateExpiryMonitorJobCommand],
    repository: CertExpiryJobRepo,
    threshold: ExpiryThreshold,
    interval: Duration,
    instant: java.time.Instant,
    timeLeft: Duration
  )(implicit ec: ExecutionContext): Unit = {

    val shouldPerformCheck: Future[Boolean] =
      repository.shouldPerformCertExpiryCheck(JOB_ID, threshold, interval, instant)

    // Sends the Mongo result back to this actor's mailbox as an AlertResult message,
    context.pipeToSelf(shouldPerformCheck) {
      case scala.util.Success(claimed) => AlertResult(claimed, threshold, timeLeft)
      case scala.util.Failure(_)       => AlertResult(claimed = false, threshold, timeLeft)
    }
  }

  private def scheduleNextCheck(
    pekkoTimer: PekkoTimer[CertificateExpiryMonitorJobCommand],
    certificateCheckSchedule: CertificateCheckSchedule,
    now: LocalDateTime
  ): Unit = {
    val nextCheck: FiniteDuration = certificateCheckSchedule.getNextCheckIntervalDurationMinutes(now)
    pekkoTimer.startSingleTimer(CheckExpiry, nextCheck)
  }

  private def onAlertResult(
    result: AlertResult,
    certificateExpiry: LocalDateTime
  )(implicit
    logger: BrmLogger,
    instanceId: UUID
  ): Behavior[CertificateExpiryMonitorJobCommand] = {
    val formattedExpiry = certificateExpiry.format(timeFormat)

    if (result.claimed) { // key part, this instance is able to call the logCertificateExpiry method
      logger.info(
        instanceId,
        CLASS_NAME,
        "running",
        s"sending alert for threshold=${result.threshold.value} actualCertExpiryDate=$formattedExpiry"
      )

      logCertificateExpiry(
        result.timeLeft,
        certificateExpiry
      )

    } else {
      logger.info(
        instanceId,
        CLASS_NAME,
        "running",
        s"alert already handled for threshold=${result.threshold.value} actualCertExpiryDate=$formattedExpiry"
      )
    }

    Behaviors.same
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

    if (timeUntilCertExpiry.toDays > 0)
      logger.warn(
        instanceId,
        CLASS_NAME,
        "logCertificateExpiry",
        s"Certificate expires in ${timeUntilCertExpiry.toDays} days at $formattedExpiry"
      )
    else if (!timeUntilCertExpiry.isNegative)
      logger.warn(
        instanceId,
        CLASS_NAME,
        "logCertificateExpiry",
        s"Certificate expires in ${timeUntilCertExpiry.toHours} hours at $formattedExpiry"
      )
    else
      logger.warn(instanceId, CLASS_NAME, "logCertificateExpiry", s"Certificate expired at $formattedExpiry")
  }

}
