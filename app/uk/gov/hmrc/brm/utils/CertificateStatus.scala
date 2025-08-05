/*
 * Copyright 2024 HM Revenue & Customs
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

import uk.gov.hmrc.brm.config.GroAppConfig
import uk.gov.hmrc.brm.utils.BrmLogger._

import java.io.FileInputStream
import java.security.KeyStore
import java.security.cert.{Certificate, X509Certificate}
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit.DAYS
import java.time.{Duration, LocalDate, LocalDateTime, Period, ZoneId}
import javax.inject.Inject
import scala.jdk.CollectionConverters.EnumerationHasAsScala
import scala.util.{Failure, Success, Try, Using}

class CertificateStatus @Inject() (val groConfig: GroAppConfig) extends CertificateProvider {

  lazy val getExpiryDate: Option[LocalDateTime] = extractExpiryDateFromCertificate()

  protected val CLASS_NAME: String = this.getClass.getSimpleName

  override def loadCertificate(): Try[Certificate] = {
    val keyStore = KeyStore.getInstance("PKCS12")
    Using(new FileInputStream(groConfig.tlsPrivateCertificatePath)) { fis =>
      keyStore.load(fis, groConfig.tlsPrivateKeystorePassword.toCharArray)
      keyStore
        .aliases()
        .asScala
        .map(alias => keyStore.getCertificate(alias))
        .toList
        .head
    }
  }

  def extractExpiryDateFromCertificate(): Option[LocalDateTime] = {
    info(CLASS_NAME, "extractExpiryDateFromCertificate", "start")

    loadCertificate() match {
      case Success(certificate: X509Certificate) =>
        val expiryDate    = certificate.getNotAfter
        val localDateTime = expiryDate.toInstant.atZone(ZoneId.of("UTC")).toLocalDateTime
        info(CLASS_NAME, "extractExpiryDateFromCertificate", s"CERTIFICATE_EXPIRES $localDateTime")
        Some(localDateTime)
      case Success(cert)                         =>
        error(CLASS_NAME, "extractExpiryDateFromCertificate", s"Error loading cert, cert was of type: ${cert.getType}")
        None
      case Failure(exception)                    =>
        error(CLASS_NAME, "extractExpiryDateFromCertificate", s"Error loading cert $exception ")
        None
    }
  }

  private def logCertificate(certificateExpiry: LocalDateTime): Unit = {
    val certificateExpiryDate = certificateExpiry.toLocalDate
    val daysTillExpiry        = DAYS.between(LocalDateTime.now(), certificateExpiry)
    val durationMessage       = DateOutput.formatDurations(Period.between(LocalDate.now(), certificateExpiryDate))
    val formatter             = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    if (daysTillExpiry > groConfig.certExpiryWarningThreshold) {
      info(CLASS_NAME, "logCertificate", s"EXPIRES_IN $durationMessage ($certificateExpiryDate)")
    } else if (
      daysTillExpiry > groConfig.certExpiryCriticalThreshold && daysTillExpiry <= groConfig.certExpiryWarningThreshold
    ) {
      warn(CLASS_NAME, "logCertificate", s"EXPIRES_WITHIN $durationMessage ($certificateExpiryDate)")
    } else if (daysTillExpiry > 0 && daysTillExpiry <= groConfig.certExpiryCriticalThreshold) {
      error(
        CLASS_NAME,
        "logCertificate",
        s"!!!EXPIRES_SOON!!! EXPIRES_WITHIN $durationMessage ($certificateExpiryDate)"
      )
    } else if (Duration.between(certificateExpiry, LocalDateTime.now()).toMillis >= 1) {
      error(CLASS_NAME, "logCertificate", s"EXPIRES_TODAY (${certificateExpiry.format(formatter)})")
    } else {
      error(CLASS_NAME, "logCertificate", s"CERTIFICATE_EXPIRED (${certificateExpiry.format(formatter)})")
    }
  }

  def certificateStatus(): Boolean =
    getExpiryDate match {
      case Some(certificateExpiryDateTime) =>
        logCertificate(certificateExpiryDateTime)
        certificateExpiryDateTime.isAfter(LocalDateTime.now())
      case None                            =>
        false
    }

}
