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

import uk.gov.hmrc.brm.utils.BrmLogger
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.Duration
import scala.util.{Failure, Success, Try}

case class CertificateCheckTimes(
  certExpiryEarlyWarningThresholdHours: Duration,
  certExpiryEarlyWarningCheckIntervalHours: Duration,
  certExpiryWarningThresholdHours: Duration,
  certExpiryWarningCheckIntervalHours: Duration,
  certExpiryCriticalThresholdHours: Duration,
  certExpiryCriticalCheckIntervalHours: Duration
)

object CertificateCheckTimes {

  val default: CertificateCheckTimes = CertificateCheckTimes(
    certExpiryEarlyWarningThresholdHours = Duration.ofHours(1344),
    certExpiryEarlyWarningCheckIntervalHours = Duration.ofHours(168),
    certExpiryWarningThresholdHours = Duration.ofHours(168),
    certExpiryWarningCheckIntervalHours = Duration.ofHours(24),
    certExpiryCriticalThresholdHours = Duration.ofHours(24),
    certExpiryCriticalCheckIntervalHours = Duration.ofHours(1)
  )

  def load()(implicit servicesConfig: ServicesConfig, tlsConfigPath: String, brmLogger: BrmLogger): CertificateCheckTimes = {
    val maybeCertificateCheckTimes = for {
      certExpiryEarlyWarningThresholdHours     <- loadHours("certExpiryEarlyWarningThresholdHours")
      certExpiryEarlyWarningCheckIntervalHours <- loadHours("certExpiryEarlyWarningCheckIntervalHours")
      certExpiryWarningThresholdHours          <- loadHours("certExpiryWarningThresholdHours")
      certExpiryWarningCheckIntervalHours      <- loadHours("certExpiryWarningCheckIntervalHours")
      certExpiryCriticalThresholdHours         <- loadHours("certExpiryCriticalThresholdHours")
      certExpiryCriticalCheckIntervalHours     <- loadHours("certExpiryCriticalCheckIntervalHours")
      _                                        <- {
        val validWindowThresholds =
          certExpiryEarlyWarningThresholdHours.toHours > certExpiryWarningThresholdHours.toHours &&
            certExpiryWarningThresholdHours.toHours > certExpiryCriticalThresholdHours.toHours

        if (validWindowThresholds) {
          Success(true)
        } else {
          Failure(
            new Exception(
              "Window thresholds are invalid. Early warning threshold should be greater than warning threshold, " +
                "which should be greater than critical threshold."
            )
          )
        }

      }
    } yield CertificateCheckTimes(
      certExpiryEarlyWarningThresholdHours,
      certExpiryEarlyWarningCheckIntervalHours,
      certExpiryWarningThresholdHours,
      certExpiryWarningCheckIntervalHours,
      certExpiryCriticalThresholdHours,
      certExpiryCriticalCheckIntervalHours
    )

    maybeCertificateCheckTimes match {
      case Success(value)     =>
        brmLogger.info("CertificateCheckTimes", "load", "Successfully loaded certificate check times")
        value
      case Failure(exception) =>
        brmLogger.error("CertificateCheckTimes", "load", s"Error loading certificate check times. Using defaults. Exception: ${exception.getMessage}")
        CertificateCheckTimes.default
    }

  }

  private def loadHours(key: String)(implicit servicesConfig: ServicesConfig, tlsConfigPath: String): Try[Duration] =
    Try(Duration.ofHours(servicesConfig.getInt(s"$tlsConfigPath.$key")))
}
