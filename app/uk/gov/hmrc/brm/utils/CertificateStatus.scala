/*
 * Copyright 2016 HM Revenue & Customs
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

import org.joda.time.{Days, LocalDate, Months, Years}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.brm.utils.BrmLogger._

/**
  * Created by chrisianson on 16/08/16.
  */
trait CertificateStatus extends ServicesConfig {

  protected val CLASS_NAME : String = this.getClass.getCanonicalName

  val privateKeystore_Key = "birth-registration-matching.privateKeystore"

  lazy val privateKeystore = getConfString(privateKeystore_Key,
    throw new RuntimeException("[Configuration][NotFound] privateKeystore"))

  val privateKeystoreKey_Key = "birth-registration-matching.privateKeystoreKey"

  lazy val privateKeystoreKey = getConfString(privateKeystoreKey_Key,
    throw new RuntimeException("[Configuration][NotFound] privateKeystoreKey"))

  val certificateExpiryDate_Key = "birth-registration-matching.certificateExpiryDate"

  lazy val certificateExpiryDate = getConfString(certificateExpiryDate_Key,
    throw new RuntimeException("[Configuration][NotFound] certificateExpiryDate"))

  private def getExpiryDate = new LocalDate(certificateExpiryDate)

  private def difference(expiryDate: LocalDate, comparisonDate: LocalDate): (Int, Int, Int) = {
    val days = Days.daysBetween(comparisonDate, expiryDate).getDays
    val months = Months.monthsBetween(comparisonDate, expiryDate).getMonths
    val years = Years.yearsBetween(comparisonDate, expiryDate).getYears
    (days, months, years)
  }

  private def logCertificate(dayDifference: Int, monthDifference: Int, yearDifference: Int): Unit = (dayDifference, monthDifference, yearDifference) match {
    case (0, 0, 0) => error(CLASS_NAME, "logCertificate", "EXPIRES_TODAY")
    case (d, 0, 0) => error(CLASS_NAME, "logCertificate", "EXPIRES_THIS_MONTH")
    case (d, m, 0) if m <= 12 && m > 0 =>
      if (m <= 6) {
        warn(CLASS_NAME, "logCertificate", s"[EXPIRES_IN][Days: $d][Months: $m]")
      } else {
        info(CLASS_NAME, "logCertificate", s"[EXPIRES_IN][Days: $d][Months: $m]")
      }
    case (d, m, y) =>
      if (d < 0) {
        error(CLASS_NAME, "logCertificate", s"[CERTIFICATE_EXPIRED][Days: $d]")
      } else {
        info(CLASS_NAME, "logCertificate", s"[EXPIRES_IN][Days: $d][Months: $m] [Years: $y]")
      }
  }

  def logCertificateStatus(date: LocalDate = new LocalDate): Boolean = {
    val (day, month, year) = difference(getExpiryDate, date)
    logCertificate(day, month, year)
    day >= 0
  }

}

object CertificateStatus extends CertificateStatus