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
import java.time.{LocalDate, Period, ZoneId}
import javax.inject.Inject
import scala.collection.mutable.ListBuffer

class CertificateStatus @Inject() (val groConfig: GroAppConfig) extends CertificateProvider {

  lazy val getExpiryDate: Option[LocalDate] = extractExpiryDateFromCertificate()

  lazy val certificateExpiryDate: String =
    getExpiryDate.getOrElse(LocalDate.MIN).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

  protected val CLASS_NAME: String = this.getClass.getSimpleName

  override def loadCertificates(): List[Certificate] = {
    val keyStore = KeyStore.getInstance("PKCS12")
    val fis      = new FileInputStream(groConfig.tlsPrivateCertificatePath)
    try {
      keyStore.load(fis, groConfig.tlsPrivateKeystorePassword.toCharArray)
      val certs   = ListBuffer[Certificate]()
      val aliases = keyStore.aliases()
      while (aliases.hasMoreElements) {
        val alias = aliases.nextElement()
        certs += keyStore.getCertificate(alias)
      }
      certs.toList
    } finally fis.close()
  }

  def extractExpiryDateFromCertificate(): Option[LocalDate] = {
    info(CLASS_NAME, "extractExpiryDateFromCertificate", "start")

    try {
      val certs = loadCertificates()
      certs.foreach {
        case x509: X509Certificate =>
          val expiryDate = x509.getNotAfter
          val localDate  = expiryDate.toInstant.atZone(ZoneId.systemDefault()).toLocalDate
          info(CLASS_NAME, "extractExpiryDateFromCertificate", s"CERTIFICATE_EXPIRES $localDate")
          return Some(localDate)
        case _                     =>
          error(CLASS_NAME, "extractExpiryDateFromCertificate", "Non-X509 certificate found")
          throw new Exception("failed to get certificate")
      }
    } catch {
      case e: Exception =>
        error(CLASS_NAME, "extractExpiryDateFromCertificate", s"Exception occurred: ${e.getMessage}")
    }

    None
  }

  private def difference(expiryDate: LocalDate, comparisonDate: LocalDate): (Long, String) = {
    val days = DAYS.between(comparisonDate, expiryDate)
    (days, DateOutput.formatDurations(Period.between(comparisonDate, expiryDate)))
  }

  private val expiresToday: PartialFunction[Long, Unit] = { case 0 =>
    error(CLASS_NAME, "logCertificate", s"EXPIRES_TODAY ($certificateExpiryDate)")
  }

  private def expiresWithin60Days(message: String): PartialFunction[Long, Unit] = {
    case d if d > 0 && d <= 60 =>
      error(CLASS_NAME, "logCertificate", s"!!!EXPIRES_SOON!!! EXPIRES_WITHIN $message ($certificateExpiryDate)")
  }

  private def expiresWithin90Days(message: String): PartialFunction[Long, Unit] = {
    case d if d > 60 && d <= 90 =>
      warn(CLASS_NAME, "logCertificate", s"EXPIRES_WITHIN $message ($certificateExpiryDate)")
  }

  private def expiresAfter90Days(message: String): PartialFunction[Long, Unit] = {
    case d if d > 90 =>
      info(CLASS_NAME, "logCertificate", s"EXPIRES_IN $message ($certificateExpiryDate)")
  }

  private def expired(message: String): PartialFunction[Long, Unit] = { case _ =>
    error(CLASS_NAME, "logCertificate", s"CERTIFICATE_EXPIRED $message $certificateExpiryDate")
  }

  private def logCertificate(day: Long, message: String): Unit =
    (expiresToday orElse
      expiresWithin60Days(message) orElse
      expiresWithin90Days(message) orElse
      expiresAfter90Days(message) orElse
      expired(message))(day)

  def certificateStatus(date: LocalDate = LocalDate.now()): Boolean =
    if (getExpiryDate.isDefined) {
      val (day, message) = difference(getExpiryDate.get, date)
      logCertificate(day, message)
      day >= 0
    } else {
      false
    }
}
