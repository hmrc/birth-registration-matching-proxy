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

import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import play.api.Logger
import uk.gov.hmrc.brm.TestFixture
import uk.gov.hmrc.brm.utils.BrmLogger
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.Duration

class CertificateCheckTimesSpec extends TestFixture {

  implicit val servicesConfig = spy(app.injector.instanceOf[ServicesConfig])
  implicit val brmLogger      = spy(new BrmLogger(Logger("BrmLogger").logger))
  implicit val tlsConfigPath  = "microservice.services.birth-registration-matching.gro.tls"

  private def ofHours(hours: Int): Duration = Duration.ofHours(hours)

  private def setConfigValue(key: String, retVal: Int): OngoingStubbing[Int] =
    when(servicesConfig.getInt(s"$tlsConfigPath.$key")).thenReturn(retVal)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(brmLogger)
  }

  private val expectedFailureMessage =
    "Error loading certificate check times. Using defaults. Exception: Window thresholds are invalid. " +
      "Early warning threshold should be greater than warning threshold, which should be greater than critical threshold."

  "CertificateCheckTimes" should {

    "load valid configuration" in {

      setConfigValue("certExpiryEarlyWarningThresholdHours", 1344)
      setConfigValue("certExpiryEarlyWarningCheckIntervalHours", 168)
      setConfigValue("certExpiryWarningThresholdHours", 168)
      setConfigValue("certExpiryWarningCheckIntervalHours", 24)
      setConfigValue("certExpiryCriticalThresholdHours", 24)
      setConfigValue("certExpiryCriticalCheckIntervalHours", 1)

      CertificateCheckTimes.load() shouldBe CertificateCheckTimes(
        certExpiryEarlyWarningThresholdHours = ofHours(1344),
        certExpiryEarlyWarningCheckIntervalHours = ofHours(168),
        certExpiryWarningThresholdHours = ofHours(168),
        certExpiryWarningCheckIntervalHours = ofHours(24),
        certExpiryCriticalThresholdHours = ofHours(24),
        certExpiryCriticalCheckIntervalHours = ofHours(1)
      )

      verify(brmLogger).info("CertificateCheckTimes", "load", "Successfully loaded certificate check times")
    }

    "fallback to default times when loading invalid configuration when warning threshold is greater than early warning threshold" in {

      setConfigValue("certExpiryEarlyWarningThresholdHours", 1)
      setConfigValue("certExpiryEarlyWarningCheckIntervalHours", 1)
      setConfigValue("certExpiryWarningThresholdHours", 2)
      setConfigValue("certExpiryWarningCheckIntervalHours", 1)
      setConfigValue("certExpiryCriticalThresholdHours", 1)
      setConfigValue("certExpiryCriticalCheckIntervalHours", 1)

      val result = CertificateCheckTimes.load()

      result shouldBe CertificateCheckTimes.default
      verify(brmLogger).error("CertificateCheckTimes", "load", expectedFailureMessage)
    }

    "fallback to default times when loading invalid configuration when critical threshold is greater than warning threshold" in {

      setConfigValue("certExpiryEarlyWarningThresholdHours", 3)
      setConfigValue("certExpiryEarlyWarningCheckIntervalHours", 1)
      setConfigValue("certExpiryWarningThresholdHours", 1)
      setConfigValue("certExpiryWarningCheckIntervalHours", 1)
      setConfigValue("certExpiryCriticalThresholdHours", 2)
      setConfigValue("certExpiryCriticalCheckIntervalHours", 1)

      val result = CertificateCheckTimes.load()

      result shouldBe CertificateCheckTimes.default
      verify(brmLogger).error("CertificateCheckTimes", "load", expectedFailureMessage)
    }

  }
}
