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

// not using GuiceOneAppPerSuite as the app pops up and instantiates this class by reading our actual test cert, making testing impossible
class CertificateExpiryLoggerSpec
    extends AnyWordSpec
    with AnyWordSpecLike
    with Matchers
    with OptionValues
    with MockitoSugar
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with BaseUnitSpec {

  val testKit: ActorTestKit = ActorTestKit("CertificateExpiryLoggerSpec", ManualTime.config)
  val manualTime            = ManualTime()(testKit.system)

  implicit val spiedLogger: BrmLogger = spy(new BrmLogger(Logger("BrmLogger").logger))

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
    super.afterAll()
  }

  override def afterEach(): Unit = {
    reset(spiedLogger)
    super.afterEach()
  }

  private val oneWeekInHours   = 168
  private val sixtyDaysInHours = 1440

  private val certExpiryEarlyWarningThresholdHours     = sixtyDaysInHours
  private val certExpiryEarlyWarningCheckIntervalHours = oneWeekInHours

  private val certExpiryWarningThresholdHours     = oneWeekInHours
  private val certExpiryWarningCheckIntervalHours = 24

  private val certExpiryCriticalThresholdHours     = 24
  private val certExpiryCriticalCheckIntervalHours = 1

  // Mock configuration for testing
  def createMockConfig(): GroAppConfig = {
    val config = mock[GroAppConfig]

    when(config.certExpiryEarlyWarningThresholdHours).thenReturn(certExpiryEarlyWarningThresholdHours)
    when(config.certExpiryEarlyWarningCheckIntervalHours).thenReturn(certExpiryEarlyWarningCheckIntervalHours)

    when(config.certExpiryWarningThresholdHours).thenReturn(certExpiryWarningThresholdHours)
    when(config.certExpiryWarningCheckIntervalHours).thenReturn(certExpiryWarningCheckIntervalHours)

    when(config.certExpiryCriticalThresholdHours).thenReturn(certExpiryCriticalThresholdHours)
    when(config.certExpiryCriticalCheckIntervalHours).thenReturn(certExpiryCriticalCheckIntervalHours)

    config
  }

  "CertificateExpiryLogger" should {

    "behave as expected for all warning windows" in {

      val now                     = LocalDateTime.now()
      val zonedNow: ZonedDateTime = now.atZone(ZoneId.of("UTC"))

      val mockTimeProvider = spy(new TimeProvider)

      // set our cert expiry so that we aren't synchronising our interval, and we are outside the early warning window
      val certExpiryHours   = certExpiryEarlyWarningThresholdHours + certExpiryEarlyWarningCheckIntervalHours
      val certificateExpiry = zonedNow.plusHours(certExpiryHours)
      val config            = createMockConfig()

      implicit val spiedLogger           = spy(new BrmLogger(Logger("BrmLogger").logger))
      var timerSpy: Timer[LoggerCommand] = null

      val timer = (scheduler: TimerScheduler[LoggerCommand]) => {
        val realTimer = new PekkoTimer(scheduler)
        timerSpy = spy(realTimer)
        timerSpy
      }

      when(mockTimeProvider.now).thenReturn(zonedNow)

      testKit.spawn(CertificateExpiryLogger(certificateExpiry.toLocalDateTime, config, mockTimeProvider, timer))

      Thread.sleep(100) // allow our actor to pop up

      // initial check
      verify(spiedLogger).info("CertificateExpiryLogger$", "Starting initial check")
      verify(timerSpy).startSingleTimer(CheckExpiry, FiniteDuration(0, MINUTES))

      val nextCheckTime =
        LocalDateTime
          .now()
          .plusHours(certExpiryEarlyWarningCheckIntervalHours)
          .format(CertificateExpiryLogger.timeFormat)

      val checkIntervalMessage =
        s"Setting next check interval to $certExpiryEarlyWarningCheckIntervalHours hours at $nextCheckTime"
      verify(spiedLogger).info("CertificateExpiryLogger$", checkIntervalMessage)

      verify(timerSpy).startSingleTimer(CheckExpiry, FiniteDuration(certExpiryEarlyWarningCheckIntervalHours, HOURS))
      reset(timerSpy)


      // #################################### EARLY WARNING TESTING ####################################

      // advance time so we are exactly on EARLY WARNING window,
      // our warning log should now be called with the expected expiry message, and the timer called with the expected interval
      println(s"A - advancing time by $certExpiryEarlyWarningCheckIntervalHours hours")

      val pointA = zonedNow.plusHours(certExpiryEarlyWarningCheckIntervalHours)
      when(mockTimeProvider.now).thenReturn(pointA)
      manualTime.timePasses(FiniteDuration(certExpiryEarlyWarningCheckIntervalHours, HOURS))

      Thread.sleep(100)

      val daysTillExpiry = Duration.between(mockTimeProvider.now, certificateExpiry).toDays

      val certExpiryMessage =
        s"Certificate expires in $daysTillExpiry days at ${certificateExpiry.toLocalDateTime.format(CertificateExpiryLogger.timeFormat)}"

      verify(spiedLogger).warn("CertificateExpiryLogger$", "logCertificateExpiry", certExpiryMessage)
      verify(timerSpy).startSingleTimer(CheckExpiry, FiniteDuration(certExpiryEarlyWarningCheckIntervalHours, HOURS))
      reset(timerSpy)
      reset(mockTimeProvider)

      // #################################### END EARLY WARNING TESTING ####################################



      // ####################################  WARNING TESTING ####################################

      // advance time so we are exactly on WARNING window,
      // our warning log should now be called with the expected expiry message, and the timer called with the expected interval

      val timeToAdvanceInToWarningWindow = certExpiryEarlyWarningThresholdHours - certExpiryWarningThresholdHours
      println(s"B - advancing time by $timeToAdvanceInToWarningWindow hours")

      val pointB = pointA.plusHours(timeToAdvanceInToWarningWindow)

      when(mockTimeProvider.now).thenReturn(pointB)
      manualTime.timePasses(FiniteDuration(timeToAdvanceInToWarningWindow, HOURS))

      Thread.sleep(100)

      val daysTillExpiry2 = Duration.between(mockTimeProvider.now, certificateExpiry).toDays

      val certExpiryMessage2 =
        s"Certificate expires in $daysTillExpiry2 days at ${certificateExpiry.toLocalDateTime.format(CertificateExpiryLogger.timeFormat)}"

      verify(spiedLogger).warn("CertificateExpiryLogger$", "logCertificateExpiry", certExpiryMessage2)
      verify(timerSpy).startSingleTimer(CheckExpiry, FiniteDuration(certExpiryWarningCheckIntervalHours, HOURS))
      reset(timerSpy)
      reset(mockTimeProvider)

      // ####################################  END WARNING TESTING ####################################


      // ####################################  CRITICAL WARNING TESTING ####################################


      // advance time so we are exactly on CRITICAL window,
      // our warning log should now be called with the expected expiry message, and the timer called with the expected interval

      // advance 1 extra hour in to window to test hours log
      val timeToAdvanceInToCriticalWindow = certExpiryWarningThresholdHours - certExpiryCriticalThresholdHours + 1
      println(s"C - advancing time by $timeToAdvanceInToCriticalWindow hours")

      val pointC = pointB.plusHours(timeToAdvanceInToCriticalWindow)

      when(mockTimeProvider.now).thenReturn(pointC)
      manualTime.timePasses(FiniteDuration(timeToAdvanceInToCriticalWindow, HOURS))

      Thread.sleep(100)

      val hoursTillExpiry = Duration.between(mockTimeProvider.now, certificateExpiry).toHours

      val certExpiryMessage3 =
        s"Certificate expires in $hoursTillExpiry hours at ${certificateExpiry.toLocalDateTime.format(CertificateExpiryLogger.timeFormat)}"

      verify(spiedLogger).warn("CertificateExpiryLogger$", "logCertificateExpiry", certExpiryMessage3)
      verify(timerSpy).startSingleTimer(CheckExpiry, FiniteDuration(certExpiryCriticalCheckIntervalHours, HOURS))
      reset(timerSpy)
      reset(mockTimeProvider)

      // ####################################  END CRITICAL WARNING TESTING ####################################


      // ####################################  EXPIRED TESTING ####################################

      val timeToAdvanceInToExpired = certExpiryCriticalThresholdHours
      println(s"D - advancing time by $timeToAdvanceInToExpired hours")

      val pointD = pointC.plusHours(timeToAdvanceInToCriticalWindow)

      when(mockTimeProvider.now).thenReturn(pointD)
      manualTime.timePasses(FiniteDuration(timeToAdvanceInToExpired, HOURS))

      Thread.sleep(100)

      val certExpiryMessage4 =
        s"Certificate expired at ${certificateExpiry.toLocalDateTime.format(CertificateExpiryLogger.timeFormat)}"

      verify(spiedLogger).warn("CertificateExpiryLogger$", "logCertificateExpiry", certExpiryMessage4)
      verify(timerSpy).startSingleTimer(CheckExpiry, FiniteDuration(certExpiryCriticalCheckIntervalHours, HOURS))
      reset(timerSpy)
      reset(mockTimeProvider)

    }

    "stop when receiving Stop command" in {
      val certificateExpiry = LocalDateTime.now().plusDays(10)
      val config            = createMockConfig()

      val mockTimeProvider = spy(new TimeProvider)

      val actor: ActorRef[CertificateExpiryLogger.LoggerCommand] =
        testKit.spawn(CertificateExpiryLogger(certificateExpiry, config, mockTimeProvider))

      actor ! CertificateExpiryLogger.Stop

      val probe = testKit.createTestProbe[Nothing]()
      probe.expectTerminated(actor, 3.seconds)
    }

  }
}
