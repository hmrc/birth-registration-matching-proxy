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

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.testkit.typed.scaladsl.{ActorTestKit, ManualTime}
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.scaladsl.TimerScheduler
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{verify, _}
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import org.scalatest.wordspec.{AnyWordSpec, AnyWordSpecLike}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Logger
import uk.gov.hmrc.brm.TestFixture
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
  private val oneWeekInMinutes = 10080
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

    "behave as expected before the early warning window" in {

      val now                     = LocalDateTime.now()
      val zonedNow: ZonedDateTime = now.atZone(ZoneId.of("UTC"))

      val mockTimeProvider = spy(new TimeProvider)

      // set expiry so that we aren't synchronising our interval, and expecting the next check to be at the normal interval
      val certExpiryHours   = certExpiryEarlyWarningThresholdHours + certExpiryEarlyWarningCheckIntervalHours + 1
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

      verify(timerSpy).startSingleTimer(CheckExpiry, FiniteDuration(oneWeekInMinutes, MINUTES))

      // advance time, our warning log should now be called with the expected expiry message, and the timer called with the expected interval
      when(mockTimeProvider.now).thenReturn(zonedNow.plusHours(certExpiryEarlyWarningCheckIntervalHours + 25))
      manualTime.timePasses(FiniteDuration(certExpiryEarlyWarningCheckIntervalHours + 25, HOURS))
      reset(timerSpy)

      Thread.sleep(100)

      val daysTillExpiry = Duration.between(mockTimeProvider.now, certificateExpiry).toDays

      val certExpiryMessage =
        s"Certificate expires in $daysTillExpiry days at ${certificateExpiry.toLocalDateTime.format(CertificateExpiryLogger.timeFormat)}"
      verify(spiedLogger).warn("CertificateExpiryLogger$", "logCertificateExpiry", certExpiryMessage)
      verify(timerSpy).startSingleTimer(CheckExpiry, FiniteDuration(oneWeekInMinutes, MINUTES))

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
