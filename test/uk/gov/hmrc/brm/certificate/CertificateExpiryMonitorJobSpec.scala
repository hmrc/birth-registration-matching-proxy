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

import org.apache.pekko.actor.testkit.typed.scaladsl.{ActorTestKit, ManualTime}
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.scaladsl.TimerScheduler
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.{AnyWordSpec, AnyWordSpecLike}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import play.api.Logger
import uk.gov.hmrc.brm.config.GroAppConfig
import uk.gov.hmrc.brm.repositories.CertExpiryJobRepoMongo
import uk.gov.hmrc.brm.time.TimeProvider
import uk.gov.hmrc.brm.utils.{BaseUnitSpec, BrmLogger}

import scala.concurrent.ExecutionContext.Implicits.global
import java.time.{Duration, LocalDateTime, ZoneId, ZonedDateTime}
import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration._

// not using GuiceOneAppPerSuite as the app pops up and instantiates our actor class, which reads our actual test cert making testing impossible
class CertificateExpiryMonitorJobSpec
    extends AnyWordSpec
    with AnyWordSpecLike
    with Matchers
    with OptionValues
    with MockitoSugar
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with BaseUnitSpec {

  val now: LocalDateTime        = LocalDateTime.now(ZoneId.of("UTC")).minusMinutes(1)
  val zonedNow: ZonedDateTime   = ZonedDateTime.now(ZoneId.of("UTC")).minusMinutes(1)
  val oneMinute: FiniteDuration = FiniteDuration(1, MINUTES)

  val testKit: ActorTestKit           = ActorTestKit("CertificateExpiryLoggerSpec", ManualTime.config)
  implicit val manualTime: ManualTime = ManualTime()(testKit.system)
  implicit val brmLogger: BrmLogger   = spy(new BrmLogger(Logger("BrmLogger").logger))
  implicit val instanceId: UUID       = UUID.randomUUID()

  implicit val timeProvider: TimeProvider = spy(new TimeProvider)
  when(timeProvider.now).thenReturn(zonedNow)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(timeProvider, brmLogger)
  }

  override def afterAll(): Unit = {
    super.afterAll()
    testKit.shutdownTestKit()
  }

  private val oneWeekInHours                           = 168
  private val sixtyDaysInHours                         = 1440
  private val certExpiryEarlyWarningThresholdHours     = Duration.ofHours(sixtyDaysInHours)
  private val certExpiryEarlyWarningCheckIntervalHours = Duration.ofHours(oneWeekInHours)
  private val certExpiryWarningThresholdHours          = Duration.ofHours(oneWeekInHours)
  private val certExpiryWarningCheckIntervalHours      = Duration.ofHours(24)
  private val certExpiryCriticalThresholdHours         = Duration.ofHours(24)
  private val certExpiryCriticalCheckIntervalHours     = Duration.ofHours(1)

  "CertificateExpiryLogger" should {

    "behave as expected when time travelling towards certificate expiry from before early warning window" in {

      // set our cert expiry so that we aren't synchronising our interval, and we are outside the early warning window
      val certExpiryHours =
        certExpiryEarlyWarningThresholdHours.toHours + certExpiryEarlyWarningCheckIntervalHours.toHours

      // add one minute to the cert expiry to align with expected intervals and durations for easier testing
      // - our actor waits one minute before checking certificate expiry
      implicit val certificateExpiry: ZonedDateTime = zonedNow.plusHours(certExpiryHours).plusMinutes(1)

      val formattedCertificateExpiryTime =
        certificateExpiry.toLocalDateTime.format(CertificateExpiryMonitorJob.timeFormat)

      val config                                                   = createMockConfig()
      val certExpiryJobRepoMongo = mock[CertExpiryJobRepoMongo]
      var timerSpy: PekkoTimer[CertificateExpiryMonitorJobCommand] = null

      val timer = (scheduler: TimerScheduler[CertificateExpiryMonitorJobCommand]) => {
        val realTimer = new PekkoTimer(scheduler)
        timerSpy = spy(realTimer)
        timerSpy
      }

      when(certExpiryJobRepoMongo.markAlertSent(any,any, any,any)).thenReturn(Future.successful(true))

      testKit.spawn(CertificateExpiryMonitorJob(certificateExpiry.toLocalDateTime, timeProvider, config, timer,certExpiryJobRepoMongo))

      // initial check & before early warning window assertions

      Thread.sleep(100) // allow our actor to pop up

      verify(brmLogger).info(instanceId, "CertificateExpiryMonitorJob", "apply", "Starting initial check")
      verify(timerSpy).startSingleTimer(CheckExpiry, oneMinute)

      val timeBeforeEarlyWarningWindow =
        advanceTimeReturningNewTimeProviderTime(timeToAdvanceInMinutes = Some(1), previousNow = zonedNow)

      Thread.sleep(100)

      verifyNextCheckIntervalLog(timeProvider.now, certExpiryEarlyWarningCheckIntervalHours.toHours)
      verify(timerSpy)
        .startSingleTimer(CheckExpiry, FiniteDuration(certExpiryEarlyWarningCheckIntervalHours.toHours, HOURS))

      reset(timerSpy, timeProvider, brmLogger)

      // early warning window assertions

      val timeAtEarlyWarningThreshold =
        advanceTimeReturningNewTimeProviderTime(
          timeToAdvanceInHours = Some(certExpiryEarlyWarningCheckIntervalHours.toHours.toInt),
          previousNow = timeBeforeEarlyWarningWindow
        )

      Thread.sleep(100)

      verifyWarningLog(daysTillExpiry = Some(Duration.between(timeProvider.now, certificateExpiry).toDays))
      verifyNextCheckIntervalLog(timeProvider.now, certExpiryEarlyWarningCheckIntervalHours.toHours)
      verify(timerSpy)
        .startSingleTimer(CheckExpiry, FiniteDuration(certExpiryEarlyWarningCheckIntervalHours.toHours, HOURS))

      reset(timerSpy, timeProvider, brmLogger)

      // warning window assertions

      val timeToAdvanceInToWarningWindow =
        certExpiryEarlyWarningThresholdHours.toHours - certExpiryWarningThresholdHours.toHours
      val timeAtWarningThreshold         =
        advanceTimeReturningNewTimeProviderTime(
          timeToAdvanceInHours = Some(timeToAdvanceInToWarningWindow.toInt),
          previousNow = timeAtEarlyWarningThreshold
        )

      Thread.sleep(100)

      verifyWarningLog(daysTillExpiry = Some(Duration.between(timeProvider.now, certificateExpiry).toDays))
      verifyNextCheckIntervalLog(timeProvider.now, certExpiryWarningCheckIntervalHours.toHours)
      verify(timerSpy).startSingleTimer(CheckExpiry, FiniteDuration(certExpiryWarningCheckIntervalHours.toHours, HOURS))

      reset(timerSpy, timeProvider, brmLogger)

      // critical window assertions

      // advance 1 extra hour in to window to test 'hours' text in log
      val timeToAdvanceInToCriticalWindow =
        certExpiryWarningThresholdHours.toHours - certExpiryCriticalThresholdHours.toHours + 1

      val timeAtCriticalThreshold =
        advanceTimeReturningNewTimeProviderTime(
          timeToAdvanceInHours = Some(timeToAdvanceInToCriticalWindow.toInt),
          previousNow = timeAtWarningThreshold
        )

      Thread.sleep(100)

      verifyWarningLog(hoursTillExpiry = Some(Duration.between(timeProvider.now, certificateExpiry).toHours))
      verifyNextCheckIntervalLog(timeProvider.now, certExpiryCriticalCheckIntervalHours.toHours)
      verify(timerSpy)
        .startSingleTimer(CheckExpiry, FiniteDuration(certExpiryCriticalCheckIntervalHours.toHours, HOURS))

      reset(timerSpy, timeProvider, brmLogger)

      // expired assertions

      advanceTimeReturningNewTimeProviderTime(
        timeToAdvanceInHours = Some(timeToAdvanceInToCriticalWindow.toInt),
        previousNow = timeAtCriticalThreshold
      )

      Thread.sleep(100)

      verifyNextCheckIntervalLog(timeProvider.now, certExpiryCriticalCheckIntervalHours.toHours)

      verify(brmLogger).warn(
        instanceId,
        "CertificateExpiryMonitorJob",
        "logCertificateExpiry",
        s"Certificate expired at $formattedCertificateExpiryTime"
      )

      verify(timerSpy)
        .startSingleTimer(CheckExpiry, FiniteDuration(certExpiryCriticalCheckIntervalHours.toHours, HOURS))

      reset(timerSpy, timeProvider, brmLogger)
    }

    "calculate synchronised interval" in {

      val now         = LocalDateTime.now(ZoneId.of("UTC"))
      val hoursOffset = 2

      val certificateExpiry = now.plusHours(certExpiryWarningThresholdHours.toHours + hoursOffset)
      val config            = createMockConfig()
      val certExpiryJobRepoMongo = mock[CertExpiryJobRepoMongo]
      val mockTimeProvider  = spy(new TimeProvider)

      when(mockTimeProvider.now).thenReturn(now.atZone(ZoneId.of("UTC")))

      when(certExpiryJobRepoMongo.markAlertSent(any,any, any,any)).thenReturn(Future.successful(true))

      testKit.spawn(CertificateExpiryMonitorJob(certificateExpiry, mockTimeProvider, config,certExpiryJobRepo = certExpiryJobRepoMongo))

      advanceTimeReturningNewTimeProviderTime(timeToAdvanceInMinutes = Some(1), previousNow = zonedNow)

      val nextCheckTime =
        now.plusHours(hoursOffset).format(CertificateExpiryMonitorJob.timeFormat)

      Thread.sleep(100)

      verify(brmLogger).info(
        instanceId,
        "CertificateExpiryMonitorJob",
        "running",
        s"Setting next check interval to $hoursOffset hours at $nextCheckTime UTC"
      )

    }

    "stop when receiving Stop command" in {
      val certificateExpiry = LocalDateTime.now().plusDays(10)
      val config            = createMockConfig()
      val certExpiryJobRepoMongo = mock[CertExpiryJobRepoMongo]
      val mockTimeProvider = spy(new TimeProvider)

      val actor: ActorRef[CertificateExpiryMonitorJobCommand] =
        testKit.spawn(CertificateExpiryMonitorJob(certificateExpiry, mockTimeProvider, config, certExpiryJobRepo = certExpiryJobRepoMongo))

      actor ! Terminate

      Thread.sleep(100)

      verify(brmLogger)
        .info(
          instanceId,
          "CertificateExpiryMonitorJob",
          "running",
          s"Received application lifecycle shutdown hook - Terminating certificate expiry monitoring"
        )

      val probe = testKit.createTestProbe[Nothing]()
      probe.expectTerminated(actor, 3.seconds)
    }

  }

  private def createMockConfig(): GroAppConfig = {
    val config = mock[GroAppConfig]

    when(config.certificateTimes).thenReturn(
      CertificateCheckTimes(
        certExpiryEarlyWarningThresholdHours,
        certExpiryEarlyWarningCheckIntervalHours,
        certExpiryWarningThresholdHours,
        certExpiryWarningCheckIntervalHours,
        certExpiryCriticalThresholdHours,
        certExpiryCriticalCheckIntervalHours
      )
    )

    config
  }

  // keep our TimeProvider and Pekko's time in sync by advancing them together
  private def advanceTimeReturningNewTimeProviderTime(
    timeToAdvanceInHours: Option[Int] = None,
    timeToAdvanceInMinutes: Option[Int] = None,
    previousNow: ZonedDateTime
  )(implicit
    timeProvider: TimeProvider,
    manualTime: ManualTime
  ): ZonedDateTime = {

    val newNow =
      if (timeToAdvanceInHours.isDefined) {
        previousNow.plusHours(timeToAdvanceInHours.get)
      } else {
        previousNow.plusMinutes(timeToAdvanceInMinutes.get)
      }

    when(timeProvider.now).thenReturn(newNow)

    val durationToAdvance =
      if (timeToAdvanceInHours.isDefined) {
        FiniteDuration(timeToAdvanceInHours.get, HOURS)
      } else {
        FiniteDuration(timeToAdvanceInMinutes.get, MINUTES)
      }

    manualTime.timePasses(durationToAdvance)

    newNow
  }

  private def verifyNextCheckIntervalLog(now: ZonedDateTime, expectedIntervalHours: Long)(implicit
    brmLogger: BrmLogger
  ): Unit = {
    val nextCheckTime =
      now.plusHours(expectedIntervalHours).format(CertificateExpiryMonitorJob.timeFormat)

    verify(brmLogger).info(
      instanceId,
      "CertificateExpiryMonitorJob",
      "running",
      s"Setting next check interval to $expectedIntervalHours hours at $nextCheckTime UTC"
    )
  }

  private def verifyWarningLog(
    daysTillExpiry: Option[Long] = None,
    hoursTillExpiry: Option[Long] = None
  )(implicit certificateExpiry: ZonedDateTime): Unit = {
    val formattedCertificateExpiryTime =
      certificateExpiry.toLocalDateTime.format(CertificateExpiryMonitorJob.timeFormat)

    val certExpiryMessage =
      if (daysTillExpiry.isDefined) {
        s"Certificate expires in ${daysTillExpiry.get} days at $formattedCertificateExpiryTime"
      } else {
        s"Certificate expires in ${hoursTillExpiry.get} hours at $formattedCertificateExpiryTime"
      }

    verify(brmLogger).warn(instanceId, "CertificateExpiryMonitorJob", "logCertificateExpiry", certExpiryMessage)
  }

}
