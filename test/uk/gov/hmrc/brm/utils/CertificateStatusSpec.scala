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

import org.mockito.Mockito.when
import uk.gov.hmrc.brm.TestFixture
import uk.gov.hmrc.brm.config.GroAppConfig

import java.io.FileOutputStream
import java.nio.file.Files
import java.security.KeyStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class CertificateStatusSpec extends TestFixture {

  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  val mockCertificateStatusValidExpiryDate = new CertificateStatus(testGroConfig) {
    override lazy val getExpiryDate = Some(LocalDate.now().plusDays(100))
  }

  val mockCertificateStatusToday = new CertificateStatus(testGroConfig) {
    override lazy val getExpiryDate = Some(LocalDate.now())
  }

  val mockCertificateStatusWithin60 = new CertificateStatus(testGroConfig) {
    override lazy val getExpiryDate = Some(LocalDate.now().plusDays(30))
  }

  val mockCertificateStatusWithin90 = new CertificateStatus(testGroConfig) {
    override lazy val getExpiryDate = Some(LocalDate.now().plusDays(75))
  }

  val mockCertificateStatusExpired = new CertificateStatus(testGroConfig) {
    override lazy val getExpiryDate = Some(LocalDate.now().minusDays(10))
  }

  val mockCertificateStatusNone = new CertificateStatus(testGroConfig) {
    override lazy val getExpiryDate = None
  }

  "CertificateStatus" should {

    "format certificateExpiryDate from getExpiryDate" in {
      mockCertificateStatusValidExpiryDate.certificateExpiryDate shouldBe
        mockCertificateStatusValidExpiryDate.getExpiryDate.get.format(formatter)
    }

    "fallback to LocalDate.MIN if getExpiryDate is None" in {
      mockCertificateStatusNone.certificateExpiryDate shouldBe LocalDate.MIN.format(formatter)
    }

    "return Some(date) if cert is present" in {
      val status = new CertificateStatus(testGroConfig) {
        override def extractExpiryDateFromCertificate(): Option[LocalDate] = Some(LocalDate.now().plusDays(30))
      }
      status.extractExpiryDateFromCertificate() should not be empty
    }

    "return None if keystore path is invalid or exception occurs" in {
      val config = mock[GroAppConfig]
      when(config.tlsPrivateCertificatePath).thenReturn("/invalid/path.p12")
      when(config.tlsPrivateKeystorePassword).thenReturn("pass")

      val status = new CertificateStatus(config)
      status.extractExpiryDateFromCertificate() shouldBe None
    }

    "return some(date) if keystore path is valid" in {
      val config = real[GroAppConfig]
      val status = new CertificateStatus(config)
      status.extractExpiryDateFromCertificate() should not be empty
    }

    "return None when keystore contains a certificate that is not an X509Certificate" in {
      val password = "password"
      val keyStore = KeyStore.getInstance("PKCS12")
      keyStore.load(null, null)

      val tempFile = Files.createTempFile("emptyKeystore", ".p12").toFile
      val fos      = new FileOutputStream(tempFile)
      keyStore.store(fos, password.toCharArray)
      fos.close()

      val mockConfig = mock[GroAppConfig]
      when(mockConfig.tlsPrivateCertificatePath).thenReturn(tempFile.getAbsolutePath)
      when(mockConfig.tlsPrivateKeystorePassword).thenReturn(password)

      val certStatus = new CertificateStatus(mockConfig)
      val result     = certStatus.extractExpiryDateFromCertificate()

      result shouldBe None

      tempFile.delete()
    }

    "return false if getExpiryDate is None" in {
      mockCertificateStatusNone.certificateStatus() shouldBe false
    }

    "return false if certificate is expired" in {
      mockCertificateStatusExpired.certificateStatus() shouldBe false
    }

    "return true and log EXPIRES_TODAY if cert expires today" in {
      mockCertificateStatusToday.certificateStatus() shouldBe true
    }

    "return true and log EXPIRES_WITHIN 60 days" in {
      mockCertificateStatusWithin60.certificateStatus() shouldBe true
    }

    "return true and log EXPIRES_WITHIN 90 days" in {
      mockCertificateStatusWithin90.certificateStatus() shouldBe true
    }

    "return true and log EXPIRES_AFTER 90 days" in {
      mockCertificateStatusValidExpiryDate.certificateStatus() shouldBe true
    }

    "return true when current date is earlier than expiry" in {
      val customStatus = new CertificateStatus(testGroConfig) {
        override lazy val getExpiryDate = Some(LocalDate.now().plusDays(10))
      }
      customStatus.certificateStatus(LocalDate.now()) shouldBe true
    }

    "return false when current date is later than expiry" in {
      val customStatus = new CertificateStatus(testGroConfig) {
        override lazy val getExpiryDate = Some(LocalDate.now().minusDays(5))
      }
      customStatus.certificateStatus(LocalDate.now()) shouldBe false
    }

    "return true when current date is exactly expiry date" in {
      val customStatus = new CertificateStatus(testGroConfig) {
        override lazy val getExpiryDate = Some(LocalDate.now())
      }
      customStatus.certificateStatus(LocalDate.now()) shouldBe true
    }
  }

}
