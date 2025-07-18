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
import java.time.temporal.ChronoUnit.DAYS
import java.time.{LocalDate, Period, ZoneId}
import javax.inject.Inject
import scala.jdk.CollectionConverters.EnumerationHasAsScala
import scala.util.{Failure, Success, Try, Using}

class CertificateStatus @Inject() (val groConfig: GroAppConfig) extends CertificateProvider {

  lazy val getExpiryDate: Option[LocalDate] = extractExpiryDateFromCertificate()

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

  def extractExpiryDateFromCertificate(): Option[LocalDate] = {
    info(CLASS_NAME, "extractExpiryDateFromCertificate", "start")

    loadCertificate() match {
      case Success(certificate: X509Certificate) =>
        val expiryDate = certificate.getNotAfter
        val localDate  = expiryDate.toInstant.atZone(ZoneId.systemDefault()).toLocalDate
        info(CLASS_NAME, "extractExpiryDateFromCertificate", s"CERTIFICATE_EXPIRES $localDate")
        Some(localDate)
      case Success(cert)                         =>
        error(CLASS_NAME, "", s"Error loading cert, cert was of type: ${cert.getType}")
        None
      case Failure(exception)                    =>
        error(CLASS_NAME, "", s"Error loading cert $exception ")
        None
    }
  }

  private def difference(expiryDate: LocalDate, comparisonDate: LocalDate): (Long, String) = {
    val days = DAYS.between(comparisonDate, expiryDate)
    (days, DateOutput.formatDurations(Period.between(comparisonDate, expiryDate)))
  }

  private def expiresToday(certificateExpiryDate: LocalDate): PartialFunction[Long, Unit] = { case 0 =>
    error(CLASS_NAME, "logCertificate", s"EXPIRES_TODAY ($certificateExpiryDate)")
  }

  private def expiresWithin60Days(message: String, certificateExpiryDate: LocalDate): PartialFunction[Long, Unit] = {
    case d if d > 0 && d <= 60 =>
      error(CLASS_NAME, "logCertificate", s"!!!EXPIRES_SOON!!! EXPIRES_WITHIN $message ($certificateExpiryDate)")
  }

  private def expiresWithin90Days(message: String, certificateExpiryDate: LocalDate): PartialFunction[Long, Unit] = {
    case d if d > 60 && d <= 90 =>
      warn(CLASS_NAME, "logCertificate", s"EXPIRES_WITHIN $message ($certificateExpiryDate)")
  }

  private def expiresAfter90Days(message: String, certificateExpiryDate: LocalDate): PartialFunction[Long, Unit] = {
    case d if d > 90 =>
      info(CLASS_NAME, "logCertificate", s"EXPIRES_IN $message ($certificateExpiryDate)")
  }

  private def expired(message: String, certificateExpiryDate: LocalDate): PartialFunction[Long, Unit] = { case _ =>
    error(CLASS_NAME, "logCertificate", s"CERTIFICATE_EXPIRED $message $certificateExpiryDate")
  }

  private def logCertificate(day: Long, message: String, certificateExpiryDate: LocalDate): Unit =
    (expiresToday(certificateExpiryDate) orElse
      expiresWithin60Days(message, certificateExpiryDate) orElse
      expiresWithin90Days(message, certificateExpiryDate) orElse
      expiresAfter90Days(message, certificateExpiryDate) orElse
      expired(message, certificateExpiryDate))(day)

  def certificateStatus(now: LocalDate = LocalDate.now()): Boolean =
    getExpiryDate match {
      case Some(certificateExpiryDate) =>
        val (day, message) = difference(certificateExpiryDate, now)
        logCertificate(day, message, certificateExpiryDate)
        day >= 0
      case None                        =>
        false
    }

}
