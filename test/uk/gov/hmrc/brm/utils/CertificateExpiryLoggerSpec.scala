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

import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit
import org.apache.pekko.actor.typed.ActorRef
import org.mockito.Mockito._
import org.scalatest.wordspec.AnyWordSpec
import play.api.Logger
import uk.gov.hmrc.brm.TestFixture
import uk.gov.hmrc.brm.config.GroAppConfig

import java.time.LocalDateTime
import scala.concurrent.duration._

class CertificateExpiryLoggerSpec extends AnyWordSpec with TestFixture {

  val testKit: ActorTestKit = ActorTestKit("CertificateExpiryLoggerSpec")

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

  private val certExpiryEarlyWarningHours              = sixtyDaysInHours
  private val certExpiryEarlyWarningCheckIntervalHours = oneWeekInHours

  private val certExpiryWarningHours              = oneWeekInHours
  private val certExpiryWarningCheckIntervalHours = 24

  private val certExpiryCriticalHours              = 24
  private val certExpiryCriticalCheckIntervalHours = 1

  // Mock configuration for testing
  def createMockConfig(): GroAppConfig = {
    val config = mock[GroAppConfig]

    when(config.certExpiryEarlyWarningHours).thenReturn(certExpiryEarlyWarningHours)
    when(config.certExpiryEarlyWarningCheckIntervalHours).thenReturn(certExpiryEarlyWarningCheckIntervalHours)

    when(config.certExpiryWarningHours).thenReturn(certExpiryWarningHours)
    when(config.certExpiryWarningCheckIntervalHours).thenReturn(certExpiryWarningCheckIntervalHours)

    when(config.certExpiryCriticalHours).thenReturn(certExpiryCriticalHours)
    when(config.certExpiryCriticalCheckIntervalHours).thenReturn(certExpiryCriticalCheckIntervalHours)

    config
  }

  "CertificateExpiryLogger" should {

    "be registered and start initial check" in {
      val certificateExpiry = LocalDateTime.now().plusDays(59)
      val config            = createMockConfig()

      implicit val spiedLogger = spy(new BrmLogger(Logger("BrmLogger").logger))

      testKit.spawn(CertificateExpiryLogger(certificateExpiry, config))

      Thread.sleep(100) // allow our actor to pop up like a comedy rodent

      verify(spiedLogger).info("CertificateExpiryLogger$", "Starting initial check")

      val nextCheckTime =
        LocalDateTime
          .now()
          .plusHours(certExpiryEarlyWarningCheckIntervalHours)
          .format(CertificateExpiryLogger.timeFormat)

      val message = s"Setting next check interval to 168 hours at $nextCheckTime"

      verify(spiedLogger).info("CertificateExpiryLogger$", message)
    }

    "stop when receiving Stop command" in {
      val certificateExpiry = LocalDateTime.now().plusDays(10)
      val config            = createMockConfig()

      val actor: ActorRef[CertificateExpiryLogger.Command] =
        testKit.spawn(CertificateExpiryLogger(certificateExpiry, config))

      actor ! CertificateExpiryLogger.Stop

      val probe = testKit.createTestProbe[Nothing]()
      probe.expectTerminated(actor, 3.seconds)
    }

  }
}
