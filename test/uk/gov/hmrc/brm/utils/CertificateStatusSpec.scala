/*
 * Copyright 2022 HM Revenue & Customs
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

import org.joda.time.LocalDate
import uk.gov.hmrc.brm.TestFixture

class CertificateStatusSpec extends TestFixture {

  val mockCertificateStatus = new CertificateStatus(testGroConfig)

  val mockCertificateStatusInvalidExpiryDate = new CertificateStatus(testGroConfig) {
    override lazy val certificateExpiryDate: String = "2012-02-19"
  }

  val mockCertificateStatusValidExpiryDate = new CertificateStatus(testGroConfig) {
    override lazy val certificateExpiryDate: String = "2040-02-19"
  }

  val mockCertificateStatus20160219 = new CertificateStatus(testGroConfig) {
    override lazy val certificateExpiryDate: String = "2016-02-19"
  }

  val mockCertificateStatusInvalidConfKey = new CertificateStatus(testGroConfig) {

    override lazy val certificateExpiryDate = "birth-registration-matching.certificateExpiryDateINVALID"
  }

  " CertificateStatus" should {

    "throw RuntimeException if certificateExpiryDate config doesn't exist" in {
      intercept[RuntimeException] {
        val response = mockCertificateStatusInvalidConfKey.certificateExpiryDate
        response shouldBe a[RuntimeException]
      }
    }
  }

  "certificateExpiryDate" should {

    "contain a value" in {
      val certificateExpiryDate = mockCertificateStatus.certificateExpiryDate
      certificateExpiryDate should not be empty
    }
  }

  "isValidDate" should {

    "return false when certificate date is invalid" in {
      val dateStatus = mockCertificateStatusInvalidExpiryDate.certificateStatus()
      dateStatus shouldBe false
    }

    "return true when certificate date is valid" in {
      val dateStatus = mockCertificateStatusValidExpiryDate.certificateStatus()
      dateStatus shouldBe true
    }

    "return true when current date within 7 days but greater than certificate expiry date" in {
      val dateStatus = mockCertificateStatus20160219.certificateStatus(new LocalDate("2016-02-19").minusDays(4: Int))
      dateStatus shouldBe true
    }

    "return true when current date is same as certificate expiry date" in {
      val dateStatus = mockCertificateStatus20160219.certificateStatus(new LocalDate("2016-02-19"))
      dateStatus shouldBe true
    }

    "return true when current date within same month but greater than certificate expiry date" in {
      val dateStatus = mockCertificateStatus20160219.certificateStatus(new LocalDate("2016-02-02"))
      dateStatus shouldBe true
    }

    "return true when current date 70 days behind certificate expiry date" in {
      val dateStatus = mockCertificateStatus20160219.certificateStatus(new LocalDate("2016-02-19").minusDays(70: Int))
      dateStatus shouldBe true
    }

    "return true when current date one month behind certificate expiry date" in {
      val dateStatus = mockCertificateStatus20160219.certificateStatus(new LocalDate("2016-02-19").minusMonths(1: Int))
      dateStatus shouldBe true
    }

    "return true when current date 6 months behind certificate expiry date" in {
      val dateStatus = mockCertificateStatus20160219.certificateStatus(new LocalDate("2016-02-19").minusMonths(6: Int))
      dateStatus shouldBe true
    }

    "return true when current date 10 months behind certificate expiry date" in {
      val dateStatus = mockCertificateStatus20160219.certificateStatus(new LocalDate("2016-02-19").minusMonths(10: Int))
      dateStatus shouldBe true
    }

    "return false when current date 10 months in front of certificate expiry date" in {
      val dateStatus = mockCertificateStatus20160219.certificateStatus(new LocalDate("2016-02-19").plusMonths(10: Int))
      dateStatus shouldBe false
    }

  }

}
