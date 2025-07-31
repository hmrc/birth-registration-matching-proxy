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

import com.typesafe.config.ConfigFactory
import org.mockito.Mockito._
import play.api.Configuration
import uk.gov.hmrc.brm.TestFixture
import uk.gov.hmrc.brm.config.GroAppConfig
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.io.FileOutputStream
import java.nio.file.{Files, Paths}
import java.security.KeyStore
import java.security.cert.Certificate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.util.Try

class CertificateStatusSpec extends TestFixture {

  val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  val mockCertificateStatusValidExpiryDate = new CertificateStatus(testGroConfig) {
    override lazy val getExpiryDate = Some(LocalDateTime.now().plusDays(100))
  }
  val mockCertificateStatusToday = new CertificateStatus(testGroConfig) {
    override lazy val getExpiryDate = Some(LocalDateTime.now())
  }
  val mockCertificateStatusWithin60 = new CertificateStatus(testGroConfig) {
    override lazy val getExpiryDate = Some(LocalDateTime.now().plusDays(30))
  }
  val mockCertificateStatusWithin90 = new CertificateStatus(testGroConfig) {
    override lazy val getExpiryDate = Some(LocalDateTime.now().plusDays(75))
  }
  val mockCertificateStatusExpired = new CertificateStatus(testGroConfig) {
    override lazy val getExpiryDate = Some(LocalDateTime.now().minusDays(10))
  }
  val mockCertificateStatusNone = new CertificateStatus(testGroConfig) {
    override lazy val getExpiryDate = None
  }

  "CertificateStatus" should {

    "return Some(date) if cert is present" in {
      val date = Some(LocalDateTime.now().plusDays(30))
      val status = new CertificateStatus(testGroConfig) {
        override def extractExpiryDateFromCertificate(): Option[LocalDateTime] = date
      }
      val expiryDate = status.extractExpiryDateFromCertificate()
      expiryDate should not be empty
      assert(expiryDate == date)
    }

    "return None if keystore path is invalid or exception occurs" in {
      val config = mock[GroAppConfig]
      when(config.tlsPrivateCertificatePath).thenReturn("/invalid/path.p12")
      when(config.tlsPrivateKeystorePassword).thenReturn("pass")

      val status = new CertificateStatus(config)
      status.extractExpiryDateFromCertificate() shouldBe None
    }

    "return Some(date) if keystore path is valid" in {
      val config = real[GroAppConfig]
      val status = new CertificateStatus(config)
      status.getExpiryDate should not be empty
    }

    "return None when keystore contains no certificates (empty keystore)" in {
      val password = "password"
      val keyStore = KeyStore.getInstance("PKCS12")
      keyStore.load(null, null)

      val tempFile = Files.createTempFile("emptyKeystore", ".p12").toFile
      val fos = new FileOutputStream(tempFile)
      keyStore.store(fos, password.toCharArray)
      fos.close()

      val mockConfig = mock[GroAppConfig]
      when(mockConfig.tlsPrivateCertificatePath).thenReturn(tempFile.getAbsolutePath)
      when(mockConfig.tlsPrivateKeystorePassword).thenReturn(password)

      val certStatus = new CertificateStatus(mockConfig)
      val result = certStatus.extractExpiryDateFromCertificate()

      result shouldBe None

      tempFile.delete()
    }

    "return None when keystore contains a certificate that is NOT an X509Certificate" in {
      val fakeCertificate = mock[Certificate]

      val dummyConfig = mock[GroAppConfig]
      when(dummyConfig.tlsPrivateCertificatePath).thenReturn("dummyPath")
      when(dummyConfig.tlsPrivateKeystorePassword).thenReturn("dummyPassword")

      val certStatus = new CertificateStatus(dummyConfig) {
        override def loadCertificate(): Try[Certificate] = Try(fakeCertificate)
      }

      val result = certStatus.extractExpiryDateFromCertificate()

      result shouldBe None
    }

    "return false if getExpiryDate is None" in {
      mockCertificateStatusNone.certificateStatus() shouldBe false
    }

    "return false if certificate is expired" in {
      mockCertificateStatusExpired.certificateStatus() shouldBe false
    }

    "return true and log EXPIRES_TODAY if cert expires today" in {
      val customStatus = new CertificateStatus(testGroConfig) {
        override lazy val getExpiryDate = Some(LocalDateTime.now().plusHours(2))
      }
      customStatus.certificateStatus() shouldBe true
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
        override lazy val getExpiryDate = Some(LocalDateTime.now().plusDays(10))
      }
      customStatus.certificateStatus() shouldBe true
    }

    "return false when current date is later than expiry" in {
      val customStatus = new CertificateStatus(testGroConfig) {
        override lazy val getExpiryDate = Some(LocalDateTime.now().minusDays(5))
      }
      customStatus.certificateStatus() shouldBe false
    }

  }

  "checks with actual certificates" should {

    val config = mock[GroAppConfig]

    val groAppConfigLoad =
      new GroAppConfig(new ServicesConfig(Configuration(ConfigFactory.load()))) //read the props from the app.conf

    when(config.tlsPrivateKeystorePassword).thenReturn(groAppConfigLoad.tlsPrivateKeystorePassword)

    "extractExpiryDateFromCertificate should return a date for a valid certificate" in {

      when(config.tlsPrivateCertificatePath).thenReturn(groAppConfigLoad.tlsPrivateCertificatePath)

      val certStatus = new CertificateStatus(config)

      val result = certStatus.extractExpiryDateFromCertificate()

      result should not be empty

      result.get.isAfter(LocalDateTime.now()) shouldBe true
    }

    "extractExpiryDateFromCertificate should return a past date or be considered invalid for expired certificate" in {
      val certPath = Paths.get("test/resources/certificate-expired.p12").toAbsolutePath.toString

      when(config.tlsPrivateCertificatePath).thenReturn(certPath)

      val certStatus = new CertificateStatus(config)

      val result = certStatus.extractExpiryDateFromCertificate()

      result should not be empty
      result.get.isBefore(LocalDateTime.now()) shouldBe true
    }

  }
}
