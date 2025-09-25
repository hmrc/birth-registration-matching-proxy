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

import org.apache.pekko.actor.testkit.typed.scaladsl.{ActorTestKit, ManualTime}
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.scaladsl.TimerScheduler
import org.mockito.Mockito._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.{AnyWordSpec, AnyWordSpecLike}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import play.api.Logger
import uk.gov.hmrc.brm.config.GroAppConfig
import uk.gov.hmrc.brm.utils.CertificateExpiryLogger.{CheckExpiry, LoggerCommand}

import java.time.{Duration, LocalDateTime, ZoneId, ZonedDateTime}
import scala.concurrent.duration._

// not using GuiceOneAppPerSuite as the app pops up and instantiates our actor class, which reads our actual test cert making testing impossible
class CertificateExpiryLoggerSpec
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

  implicit val timeProvider: TimeProvider = spy(new TimeProvider)
  when(timeProvider.now).thenReturn(zonedNow)

  override def beforeEach(): Unit = {
    reset(timeProvider, brmLogger)
    super.beforeEach()
  }

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
    super.afterAll()
  }

  private val oneWeekInHours                           = 168
  private val sixtyDaysInHours                         = 1440
  private val certExpiryEarlyWarningThresholdHours     = sixtyDaysInHours
  private val certExpiryEarlyWarningCheckIntervalHours = oneWeekInHours
  private val certExpiryWarningThresholdHours          = oneWeekInHours
  private val certExpiryWarningCheckIntervalHours      = 24
  private val certExpiryCriticalThresholdHours         = 24
  private val certExpiryCriticalCheckIntervalHours     = 1

  "CertificateExpiryLogger" should {

    "behave as expected when time travelling towards certificate expiry from before early warning window" in {

      // set our cert expiry so that we aren't synchronising our interval, and we are outside the early warning window
      val certExpiryHours = certExpiryEarlyWarningThresholdHours + certExpiryEarlyWarningCheckIntervalHours

      // add one minute to the cert expiry to align with expected intervals and durations for easier testing
      // - our actor waits one minute before checking certificate expiry
      implicit val certificateExpiry: ZonedDateTime = zonedNow.plusHours(certExpiryHours).plusMinutes(1)

      val formattedCertificateExpiryTime = certificateExpiry.toLocalDateTime.format(CertificateExpiryLogger.timeFormat)

      val config                              = createMockConfig()
      var timerSpy: PekkoTimer[LoggerCommand] = null

      val timer = (scheduler: TimerScheduler[LoggerCommand]) => {
        val realTimer = new PekkoTimer(scheduler)
        timerSpy = spy(realTimer)
        timerSpy
      }

      testKit.spawn(CertificateExpiryLogger(certificateExpiry.toLocalDateTime, config, timeProvider, timer))

      // initial check & before early warning window assertions

      Thread.sleep(100) // allow our actor to pop up

      verify(brmLogger).info("CertificateExpiryLogger$", "apply", "Starting initial check")
      verify(timerSpy).startSingleTimer(CheckExpiry, oneMinute)

      val timeBeforeEarlyWarningWindow =
        advanceTimeReturningNewTimeProviderTime(timeToAdvanceInMinutes = Some(1), previousNow = zonedNow)

      Thread.sleep(100)

      verifyNextCheckIntervalLog(timeProvider.now, certExpiryEarlyWarningCheckIntervalHours)
      verify(timerSpy).startSingleTimer(CheckExpiry, FiniteDuration(certExpiryEarlyWarningCheckIntervalHours, HOURS))

      reset(timerSpy, timeProvider, brmLogger)

      // early warning window assertions

      val timeAtEarlyWarningThreshold =
        advanceTimeReturningNewTimeProviderTime(
          timeToAdvanceInHours = Some(certExpiryEarlyWarningCheckIntervalHours),
          previousNow = timeBeforeEarlyWarningWindow
        )

      Thread.sleep(100)

      verifyWarningLog(daysTillExpiry = Some(Duration.between(timeProvider.now, certificateExpiry).toDays))
      verifyNextCheckIntervalLog(timeProvider.now, certExpiryEarlyWarningCheckIntervalHours)
      verify(timerSpy).startSingleTimer(CheckExpiry, FiniteDuration(certExpiryEarlyWarningCheckIntervalHours, HOURS))

      reset(timerSpy, timeProvider, brmLogger)

      // warning window assertions

      val timeToAdvanceInToWarningWindow = certExpiryEarlyWarningThresholdHours - certExpiryWarningThresholdHours
      val timeAtWarningThreshold         =
        advanceTimeReturningNewTimeProviderTime(
          timeToAdvanceInHours = Some(timeToAdvanceInToWarningWindow),
          previousNow = timeAtEarlyWarningThreshold
        )

      Thread.sleep(100)

      verifyWarningLog(daysTillExpiry = Some(Duration.between(timeProvider.now, certificateExpiry).toDays))
      verifyNextCheckIntervalLog(timeProvider.now, certExpiryWarningCheckIntervalHours)
      verify(timerSpy).startSingleTimer(CheckExpiry, FiniteDuration(certExpiryWarningCheckIntervalHours, HOURS))

      reset(timerSpy, timeProvider, brmLogger)

      // critical window assertions

      // advance 1 extra hour in to window to test 'hours' text in log
      val timeToAdvanceInToCriticalWindow = certExpiryWarningThresholdHours - certExpiryCriticalThresholdHours + 1

      val timeAtCriticalThreshold =
        advanceTimeReturningNewTimeProviderTime(
          timeToAdvanceInHours = Some(timeToAdvanceInToCriticalWindow),
          previousNow = timeAtWarningThreshold
        )

      Thread.sleep(100)

      verifyWarningLog(hoursTillExpiry = Some(Duration.between(timeProvider.now, certificateExpiry).toHours))
      verifyNextCheckIntervalLog(timeProvider.now, certExpiryCriticalCheckIntervalHours)
      verify(timerSpy).startSingleTimer(CheckExpiry, FiniteDuration(certExpiryCriticalCheckIntervalHours, HOURS))

      reset(timerSpy, timeProvider, brmLogger)

      // expired assertions

      advanceTimeReturningNewTimeProviderTime(
        timeToAdvanceInHours = Some(timeToAdvanceInToCriticalWindow),
        previousNow = timeAtCriticalThreshold
      )

      Thread.sleep(100)

      verifyNextCheckIntervalLog(timeProvider.now, certExpiryCriticalCheckIntervalHours)

      verify(brmLogger).warn(
        "CertificateExpiryLogger$",
        "logCertificateExpiry",
        s"Certificate expired at $formattedCertificateExpiryTime"
      )

      verify(timerSpy).startSingleTimer(CheckExpiry, FiniteDuration(certExpiryCriticalCheckIntervalHours, HOURS))

      reset(timerSpy, timeProvider, brmLogger)
    }

    "calculate synchronised interval" in {

      val now         = LocalDateTime.now(ZoneId.of("UTC"))
      val hoursOffset = 2

      val certificateExpiry = now.plusHours(certExpiryWarningThresholdHours + hoursOffset)
      val config            = createMockConfig()
      val mockTimeProvider  = spy(new TimeProvider)

      when(mockTimeProvider.now).thenReturn(now.atZone(ZoneId.of("UTC")))

      testKit.spawn(CertificateExpiryLogger(certificateExpiry, config, mockTimeProvider))

      advanceTimeReturningNewTimeProviderTime(timeToAdvanceInMinutes = Some(1), previousNow = zonedNow)

      val nextCheckTime =
        now.plusHours(hoursOffset).format(CertificateExpiryLogger.timeFormat)

      Thread.sleep(100)

      verify(brmLogger).info(
        "CertificateExpiryLogger$",
        "running",
        s"Setting next check interval to $hoursOffset hours at $nextCheckTime"
      )

    }

    "stop when receiving Stop command" in {
      val certificateExpiry = LocalDateTime.now().plusDays(10)
      val config            = createMockConfig()

      val mockTimeProvider = spy(new TimeProvider)

      val actor: ActorRef[CertificateExpiryLogger.LoggerCommand] =
        testKit.spawn(CertificateExpiryLogger(certificateExpiry, config, mockTimeProvider))

      actor ! CertificateExpiryLogger.Stop

      Thread.sleep(100)

      verify(brmLogger).info(
        "CertificateExpiryLogger$",
        "running",
        s"Stopping certificate expiry monitoring"
      )

      val probe = testKit.createTestProbe[Nothing]()
      probe.expectTerminated(actor, 3.seconds)
    }

  }

  private def createMockConfig(): GroAppConfig = {
    val config = mock[GroAppConfig]

    when(config.certExpiryEarlyWarningThresholdHours).thenReturn(certExpiryEarlyWarningThresholdHours)
    when(config.certExpiryEarlyWarningCheckIntervalHours).thenReturn(certExpiryEarlyWarningCheckIntervalHours)

    when(config.certExpiryWarningThresholdHours).thenReturn(certExpiryWarningThresholdHours)
    when(config.certExpiryWarningCheckIntervalHours).thenReturn(certExpiryWarningCheckIntervalHours)

    when(config.certExpiryCriticalThresholdHours).thenReturn(certExpiryCriticalThresholdHours)
    when(config.certExpiryCriticalCheckIntervalHours).thenReturn(certExpiryCriticalCheckIntervalHours)

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

  private def verifyNextCheckIntervalLog(now: ZonedDateTime, expectedIntervalHours: Int)(implicit
    brmLogger: BrmLogger
  ): Unit = {
    val nextCheckTime =
      now.plusHours(expectedIntervalHours).format(CertificateExpiryLogger.timeFormat)

    verify(brmLogger).info(
      "CertificateExpiryLogger$",
      "running",
      s"Setting next check interval to $expectedIntervalHours hours at $nextCheckTime"
    )
  }

  private def verifyWarningLog(
    daysTillExpiry: Option[Long] = None,
    hoursTillExpiry: Option[Long] = None
  )(implicit certificateExpiry: ZonedDateTime): Unit = {
    val formattedCertificateExpiryTime = certificateExpiry.toLocalDateTime.format(CertificateExpiryLogger.timeFormat)

    val certExpiryMessage =
      if (daysTillExpiry.isDefined) {
        s"Certificate expires in ${daysTillExpiry.get} days at $formattedCertificateExpiryTime"
      } else {
        s"Certificate expires in ${hoursTillExpiry.get} hours at $formattedCertificateExpiryTime"
      }

    verify(brmLogger).warn("CertificateExpiryLogger$", "logCertificateExpiry", certExpiryMessage)
  }

}
