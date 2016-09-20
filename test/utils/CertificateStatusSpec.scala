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

package utils

import org.joda.time.LocalDate
import uk.gov.hmrc.brm.utils.CertificateStatus
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

/**
  * Created by chrisianson on 15/08/16.
  */
class CertificateStatusSpec extends UnitSpec with WithFakeApplication {

  val mockCertificateStatusInvalidExpiryDate = new CertificateStatus {
    override lazy val certificateExpiryDate: String = "2012-02-19"
  }

  val mockCertificateStatusValidExpiryDate = new CertificateStatus {
    override lazy val certificateExpiryDate: String = "2040-02-19"
  }

  val mockCertificateStatus20160219 = new CertificateStatus {
    override lazy val certificateExpiryDate: String = "2016-02-19"
  }

//  val mockCertificateStatusExpiresThisMonth = new CertificateStatus {
//    override lazy val certificateExpiryDate: String = LocalDate.now().toString()
//  }

  val mockCertificateStatusInvalidConfKey = new CertificateStatus {

    override val privateKeystore_Key = "birth-registration-matching.privateKeystoreINVALID"

    override val privateKeystoreKey_Key = "birth-registration-matching.privateKeystoreKeyINVALID"

    override val certificateExpiryDate_Key = "birth-registration-matching.certificateExpiryDateINVALID"
  }

  " CertificateStatus" should {

    "contain privateKeystore" in {
      val privateKeystore = CertificateStatus.privateKeystore
      privateKeystore should not be null
    }

    "contain privateKeystoreKey" in {
      val privateKeystoreKey = CertificateStatus.privateKeystoreKey
      privateKeystoreKey should not be null
    }

    "throw RuntimeException if privateKeystore config doesn't exist" in {
      intercept[RuntimeException] {
        val response = mockCertificateStatusInvalidConfKey.privateKeystore
        response shouldBe a[RuntimeException]
      }
    }

    "throw RuntimeException if privateKeystoreKey config doesn't exist" in {
      intercept[RuntimeException] {
        val response = mockCertificateStatusInvalidConfKey.privateKeystoreKey
        response shouldBe a[RuntimeException]
      }
    }

    "throw RuntimeException if certificateExpiryDate config doesn't exist" in {
      intercept[RuntimeException] {
        val response = mockCertificateStatusInvalidConfKey.certificateExpiryDate
        response shouldBe a[RuntimeException]
      }
    }
  }

  "certificateExpiryDate" should {

    "contain a value" in {
      val certificateExpiryDate = CertificateStatus.certificateExpiryDate
      certificateExpiryDate should not be null
    }
  }

  "getExpiryDate" should {

    "return an instance of LocalDate" in {
      val date = CertificateStatus.getExpiryDate()
      date shouldBe a[LocalDate]
    }

    "allow date to be passed in directly" in {
      val date = CertificateStatus.getExpiryDate(Option("2012-12-13"))
      date.toString shouldBe "2012-12-13"
    }

    "return positive value when expiry date is in the future from comparison date" in {
      val expiryDate = CertificateStatus.getExpiryDate(Some("2016-12-18"))
      val difference = CertificateStatus.difference(expiryDate, new LocalDate("2016-12-13"))
      difference._1 should be > 0
    }

    "return negative value when expiry date is in the past from comparison date" in {
      val expiryDate = CertificateStatus.getExpiryDate(Some("2012-12-13"))
      val difference = CertificateStatus.difference(expiryDate, new LocalDate("2016-12-18"))
      difference._1 should be < 0
    }

    "return correct difference between two dates (VALID expiry date)" in {
      val expiryDate = CertificateStatus.getExpiryDate(Some("2016-06-18"))
      val difference = CertificateStatus.difference(expiryDate, new LocalDate("2016-03-18"))
      difference._2 should be > 0
      difference._1 shouldBe 92
      difference._2 shouldBe 3
      difference._3 shouldBe 0
    }

    "return correct difference between two dates (INVALID expiry date)" in {
      val expiryDate = CertificateStatus.getExpiryDate(Some("2016-01-12"))
      val difference = CertificateStatus.difference(expiryDate, new LocalDate("2016-12-12"))
      difference._2 should be < 0
      difference._1 shouldBe -335
      difference._2 shouldBe -11
      difference._3 shouldBe 0
    }

    "return zero when both dates are the same" in {
      val expiryDate = CertificateStatus.getExpiryDate(Some("2016-06-18"))
      val difference = CertificateStatus.difference(expiryDate, new LocalDate("2016-06-18"))
      difference._1 shouldBe 0
      difference._2 shouldBe 0
      difference._3 shouldBe 0
    }
  }

  "isValidDate" should {

    "return false when a date is invalid" in {
      val dateStatus = mockCertificateStatusInvalidExpiryDate.isValidDate()
      dateStatus shouldBe false
    }

    "return true when a date is valid" in {
      val dateStatus = mockCertificateStatusValidExpiryDate.isValidDate()
      dateStatus shouldBe true
    }

    "return true when current date is same as certificate expiry date" in {
      val dateStatus = mockCertificateStatus20160219.isValidDate(new LocalDate("2016-02-19"))
      dateStatus shouldBe true
    }

    "return true when current date within same month as certificate expiry date" in {
      val dateStatus = mockCertificateStatus20160219.isValidDate(new LocalDate("2016-02-02"))
      dateStatus shouldBe true
    }

    "return true when current date one month behind certificate expiry date" in {
      val dateStatus = mockCertificateStatus20160219.isValidDate(new LocalDate("2016-02-19").minusMonths(1: Int))
      dateStatus shouldBe true
    }

    "return true when current date six months behind certificate expiry date" in {
      val dateStatus = mockCertificateStatus20160219.isValidDate(new LocalDate("2016-02-19").minusMonths(6: Int))
      dateStatus shouldBe true
    }

    "return true when current date 10 months behind certificate expiry date" in {
      val dateStatus = mockCertificateStatus20160219.isValidDate(new LocalDate("2016-02-19").minusMonths(10: Int))
      dateStatus shouldBe true
    }

    "return false when current date 10 months in front of certificate expiry date" in {
      val dateStatus = mockCertificateStatus20160219.isValidDate(new LocalDate("432016-02-19").plusMonths(10: Int))
      dateStatus shouldBe false
    }

  }

}
