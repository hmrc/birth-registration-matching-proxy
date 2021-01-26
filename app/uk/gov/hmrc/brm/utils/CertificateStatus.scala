/*
 * Copyright 2021 HM Revenue & Customs
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

import org.joda.time._
import org.joda.time.format.{PeriodFormatter, PeriodFormatterBuilder}
import uk.gov.hmrc.brm.config.GroAppConfig
import uk.gov.hmrc.brm.utils.BrmLogger._

import javax.inject.Inject

class CertificateStatus @Inject()(val groConfig: GroAppConfig) {

  protected val CLASS_NAME: String = this.getClass.getSimpleName

  lazy val privateKeystore: String = groConfig.tlsPrivateKeystore
  lazy val privateKeystoreKey: String = groConfig.tlsPrivateKeystoreKey
  lazy val certificateExpiryDate: String = groConfig.certificateExpiryDate

  lazy val formatDate: PeriodFormatter =
    new PeriodFormatterBuilder()
      .appendYears()
      .appendSuffix(" year", " years")
      .appendSeparator(", ")
      .appendMonths()
      .appendSuffix(" month", " months")
      .appendSeparator(", ")
      .appendWeeks()
      .appendSuffix(" week", " weeks")
      .appendSeparator(", ")
      .appendDays()
      .appendSuffix(" day", " days")
      .toFormatter

  private def getExpiryDate = new LocalDate(certificateExpiryDate)

  private def difference(expiryDate: LocalDate, comparisonDate: LocalDate): (Int, String) = {
    val days = Days.daysBetween(comparisonDate, expiryDate).getDays
    (days, formatDate.print(new Period(comparisonDate, expiryDate)))
  }

  private val expiresToday : PartialFunction[Int, Unit] = {
    case 0 =>
      error(CLASS_NAME, "logCertificate", s"EXPIRES_TODAY ($certificateExpiryDate)")
  }

  private def expiresWithin60Days(message: String) : PartialFunction[Int, Unit] = {
    case d if d > 0 && d <= 60 =>
      error(CLASS_NAME, "logCertificate", s"!!!EXPIRES_SOON!!! EXPIRES_WITHIN $message ($certificateExpiryDate)")
  }

  private def expiresWithin90Days(message: String) : PartialFunction[Int, Unit] = {
    case d if d > 60 && d <= 90 =>
      warn(CLASS_NAME, "logCertificate", s"EXPIRES_WITHIN $message ($certificateExpiryDate)")
  }

  private def expiresAfter90Days(message: String) : PartialFunction[Int, Unit] = {
    case d if d > 90 =>
      info(CLASS_NAME, "logCertificate", s"EXPIRES_IN $message ($certificateExpiryDate)")
  }

  private def expired(message: String) : PartialFunction[Int, Unit] = {
    case _ =>
      error(CLASS_NAME, "logCertificate", s"CERTIFICATE_EXPIRED $message $certificateExpiryDate")
  }

  private def logCertificate(d: Int, message: String): Unit = {
    (expiresToday orElse
      expiresWithin60Days(message) orElse
      expiresWithin90Days(message) orElse
      expiresAfter90Days(message) orElse
      expired(message)
      )(d)
  }

  def certificateStatus(date: LocalDate = new LocalDate): Boolean = {
    val (day, message) = difference(getExpiryDate, date)
    logCertificate(day, message)
    day >= 0
  }

}
