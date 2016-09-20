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


/**
  * Created by chrisianson on 16/08/16.
  */
trait CertificateStatus extends ServicesConfig {

  val privateKeystore_Key = "birth-registration-matching.privateKeystore"

  lazy val privateKeystore = getConfString(privateKeystore_Key,
    throw new RuntimeException("[Configuration][NotFound] privateKeystore"))

  val privateKeystoreKey_Key = "birth-registration-matching.privateKeystoreKey"

  lazy val privateKeystoreKey = getConfString(privateKeystoreKey_Key,
    throw new RuntimeException("[Configuration][NotFound] privateKeystoreKey"))

  val certificateExpiryDate_Key = "birth-registration-matching.certificateExpiryDate"

  lazy val certificateExpiryDate = getConfString(certificateExpiryDate_Key,
    throw new RuntimeException("[Configuration][NotFound] certificateExpiryDate"))

  def getExpiryDate(expiryDate: Option[String] = Some(certificateExpiryDate)): LocalDate = expiryDate match {
    case None => new LocalDate(expiryDate)
    case Some(x) => new LocalDate(x)
  }

  def difference(expiryDate: LocalDate, comparisonDate: LocalDate): (Int, Int, Int) = {
    val days = Days.daysBetween(comparisonDate, expiryDate).getDays
    val months = Months.monthsBetween(comparisonDate, expiryDate).getMonths
    val years = Years.yearsBetween(comparisonDate, expiryDate).getYears
    (days, months, years)
  }

  def statusMessage(dayDifference: Int, monthDifference: Int, yearDifference: Int): String = (dayDifference, monthDifference, yearDifference) match {
    case (0, 0, 0) => "expires today"
    case (d, 0, 0) if d <= 31 => "expires this month"
    case (d, m, 0) if (m <= 12 && m > 0) => s"expires in $m months, $d days"
    case (d, m, y) if (y > 0) => s"expires in $y years, $m months, $d days"
    case _ => "couldn't determine status message from given dates"
  }

  def isValidDate(): Boolean = {
    val dateDifference = difference(getExpiryDate(), new LocalDate())
    dateDifference._1 > 0
  }
}

object CertificateStatus extends CertificateStatus