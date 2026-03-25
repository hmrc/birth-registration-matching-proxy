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

package uk.gov.hmrc.brm.utils

import org.apache.pekko.actor.testkit.typed.scaladsl.{ActorTestKit, ManualTime}
import org.mockito.Mockito.{spy, when}
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.Logger
import uk.gov.hmrc.brm.certificate.CertificateCheckTimes
import uk.gov.hmrc.brm.config.GroAppConfig
import uk.gov.hmrc.brm.time.TimeProvider

import java.time.{Duration, LocalDateTime, ZoneId, ZonedDateTime}
import java.util.UUID

class TestHelperUtil {

  val now: LocalDateTime      = LocalDateTime.now(ZoneId.of("UTC")).minusMinutes(1)
  val zonedNow: ZonedDateTime = ZonedDateTime.now(ZoneId.of("UTC")).minusMinutes(1)
  val testKit: ActorTestKit   = ActorTestKit("CertificateExpiryLoggerSpec", ManualTime.config)

  implicit val brmLogger: BrmLogger = spy(new BrmLogger(Logger("BrmLogger").logger))
  implicit val instanceId: UUID     = UUID.randomUUID()

  implicit val timeProvider: TimeProvider = spy(new TimeProvider)
  when(timeProvider.now).thenReturn(zonedNow)

  private val oneWeekInHours                   = 168
  private val sixtyDaysInHours                 = 1440
  val certExpiryEarlyWarningThresholdHours     = Duration.ofHours(sixtyDaysInHours)
  val certExpiryEarlyWarningCheckIntervalHours = Duration.ofHours(oneWeekInHours)
  val certExpiryWarningThresholdHours          = Duration.ofHours(oneWeekInHours)
  val certExpiryWarningCheckIntervalHours      = Duration.ofHours(24)
  val certExpiryCriticalThresholdHours         = Duration.ofHours(24)
  val certExpiryCriticalCheckIntervalHours     = Duration.ofHours(1)

  def createMockConfig(): GroAppConfig = {
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

}
