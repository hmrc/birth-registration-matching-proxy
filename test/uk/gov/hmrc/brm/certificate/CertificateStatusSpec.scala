/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.brm.certificate

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit
import org.mockito.Mockito._
import play.api.Configuration
import play.api.inject.DefaultApplicationLifecycle
import uk.gov.hmrc.brm.TestFixture
import uk.gov.hmrc.brm.config.GroAppConfig
import uk.gov.hmrc.brm.repositories.CertExpiryJobRepoMongo
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.io.FileOutputStream
import java.nio.file.{Files, Paths}
import java.security.KeyStore
import java.security.cert.Certificate
import java.time.LocalDateTime
import scala.util.Try

class CertificateStatusSpec extends TestFixture {

  private val testGroConfigSpy     = spy(testGroConfig)
  private val applicationLifecycle = app.injector.instanceOf[DefaultApplicationLifecycle]

  private val testKit            = ActorTestKit("CertificateStatusSpec")
  private val untypedActorSystem = testKit.system.classicSystem

  implicit val certExpiryJobRepoMongo = mock[CertExpiryJobRepoMongo]

  private val certificateStatus = spy(
    new CertificateStatus(testGroConfigSpy, applicationLifecycle, untypedActorSystem)
  )

  override def afterEach(): Unit = {
    reset(testGroConfigSpy)
    reset(certificateStatus)
  }

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
    super.afterAll()
  }

  "CertificateStatus" should {

    "return Some(date) if cert is present" in {
      val date = Some(LocalDateTime.now().plusDays(30))
      when(certificateStatus.extractExpiryDateFromCertificate()).thenReturn(date)

      val expiryDate = certificateStatus.extractExpiryDateFromCertificate()
      expiryDate should not be empty
      assert(expiryDate == date)
    }

    "return None if keystore path is invalid or exception occurs" in {
      when(testGroConfigSpy.tlsPrivateCertificatePath).thenReturn("/invalid/path.p12")
      when(testGroConfigSpy.tlsPrivateKeystorePassword).thenReturn("pass")

      certificateStatus.extractExpiryDateFromCertificate() shouldBe None
    }

    "return Some(date) if keystore path is valid" in {
      certificateStatus.getExpiryDate should not be empty
    }

    "return None when keystore contains no certificates (empty keystore)" in {
      val password = "password"
      val keyStore = KeyStore.getInstance("PKCS12")
      keyStore.load(null, null)

      val tempFile = Files.createTempFile("emptyKeystore", ".p12").toFile
      val fos      = new FileOutputStream(tempFile)
      keyStore.store(fos, password.toCharArray)
      fos.close()

      when(testGroConfigSpy.tlsPrivateCertificatePath).thenReturn(tempFile.getAbsolutePath)
      when(testGroConfigSpy.tlsPrivateKeystorePassword).thenReturn(password)

      val result = certificateStatus.extractExpiryDateFromCertificate()

      result shouldBe None

      tempFile.delete()
    }

    "return None when keystore contains a certificate that is NOT an X509Certificate" in {
      val fakeCertificate = mock[Certificate]

      when(testGroConfigSpy.tlsPrivateCertificatePath).thenReturn("dummyPath")
      when(testGroConfigSpy.tlsPrivateKeystorePassword).thenReturn("dummyPassword")

      when(certificateStatus.loadCertificate()).thenReturn(Try(fakeCertificate))

      val result = certificateStatus.extractExpiryDateFromCertificate()

      result shouldBe None
    }

    "return false if getExpiryDate is None" in {
      when(certificateStatus.getExpiryDate).thenReturn(None)
      certificateStatus.certificateStatus() shouldBe false
    }

    "return false if certificate is expired" in {
      when(certificateStatus.getExpiryDate).thenReturn(Some(LocalDateTime.now().minusDays(10)))
      certificateStatus.certificateStatus() shouldBe false
    }

    "return true and log EXPIRES_TODAY if cert expires today" in {

      when(certificateStatus.getExpiryDate).thenReturn(Some(LocalDateTime.now().plusHours(2)))

      certificateStatus.certificateStatus() shouldBe true
    }

    "return true and log EXPIRES_WITHIN 60 days" in {
      when(certificateStatus.getExpiryDate).thenReturn(Some(LocalDateTime.now().plusDays(30)))
      certificateStatus.certificateStatus() shouldBe true
    }

    "return true and log EXPIRES_WITHIN 90 days" in {
      when(certificateStatus.getExpiryDate).thenReturn(Some(LocalDateTime.now().plusDays(75)))
      certificateStatus.certificateStatus() shouldBe true
    }

    "return true and log EXPIRES_AFTER 90 days" in {
      when(certificateStatus.getExpiryDate).thenReturn(Some(LocalDateTime.now().plusDays(100)))
      certificateStatus.certificateStatus() shouldBe true
    }

    "return true when current date is earlier than expiry" in {
      when(certificateStatus.getExpiryDate).thenReturn(Some(LocalDateTime.now().plusDays(10)))
      certificateStatus.certificateStatus() shouldBe true
    }

    "return false when current date is later than expiry" in {
      when(certificateStatus.getExpiryDate).thenReturn(Some(LocalDateTime.now().minusDays(5)))
      certificateStatus.certificateStatus() shouldBe false
    }

  }

  "checks with actual certificates" should {

    val groAppConfigLoad =
      new GroAppConfig(new ServicesConfig(Configuration(ConfigFactory.load()))) // read the props from the app.conf

    when(testGroConfigSpy.tlsPrivateKeystorePassword).thenReturn(groAppConfigLoad.tlsPrivateKeystorePassword)

    "extractExpiryDateFromCertificate should return a date for a valid certificate" in {
      when(testGroConfigSpy.tlsPrivateCertificatePath).thenReturn(groAppConfigLoad.tlsPrivateCertificatePath)
      val result = certificateStatus.extractExpiryDateFromCertificate()

      result should not be empty

      assert(result.get.isEqual(LocalDateTime.parse("2055-07-11T12:47:42")))
    }

    "extractExpiryDateFromCertificate should return a past date or be considered invalid for expired certificate" in {
      val certPath = Paths.get("test/resources/certificate-expired.p12").toAbsolutePath.toString

      when(testGroConfigSpy.tlsPrivateCertificatePath).thenReturn(certPath)

      val result = certificateStatus.extractExpiryDateFromCertificate()

      result should not be empty

      assert(result.get.isEqual(LocalDateTime.parse("2025-07-03T11:09:29")))

    }

  }

}
