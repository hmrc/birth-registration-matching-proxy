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

import uk.gov.hmrc.brm.certificate.CertificateExpiryMonitorJobCommand.*

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.scaladsl.adapter.*
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.brm.config.GroAppConfig
import uk.gov.hmrc.brm.repositories.CertExpiryJobRepoMongo
import uk.gov.hmrc.brm.time.TimeProvider
import uk.gov.hmrc.brm.utils.BrmLogger
import uk.gov.hmrc.brm.utils.BrmLogger.*

import java.io.FileInputStream
import java.security.KeyStore
import java.security.cert.{Certificate, X509Certificate}
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.EnumerationHasAsScala
import scala.util.{Failure, Success, Try, Using}

@Singleton
class CertificateStatus @Inject() (
  val groConfig: GroAppConfig,
  lifecycle: ApplicationLifecycle,
  actorSystem: ActorSystem
)(using executionContext: ExecutionContext, certExpiryJobRepo: CertExpiryJobRepoMongo)
    extends CertificateProvider {

  given instanceId: UUID = UUID.randomUUID()

  lazy val getExpiryDate: Option[LocalDateTime] = extractExpiryDateFromCertificate()

  given logger: BrmLogger.type     = BrmLogger
  val typedActorSystem             = actorSystem.toTyped
  val timeProvider                 = new TimeProvider
  protected val CLASS_NAME: String = this.getClass.getSimpleName

  private val certificateExpiryLoggerActorOpt: Option[ActorRef[CertificateExpiryMonitorJobCommand]] =
    getExpiryDate.map { expiryDate =>
      info(CLASS_NAME, "Registering CertificateExpiryMonitorJob actor")

      typedActorSystem.systemActorOf(
        CertificateExpiryMonitorJob(
          certificateExpiry = expiryDate,
          timeProvider = timeProvider,
          config = groConfig
        ),
        "certificate-expiry-monitor-job"
      )
    }

  certificateExpiryLoggerActorOpt.foreach(certificateExpiryLoggerActor =>
    lifecycle.addStopHook { () =>
      info(
        instanceId,
        CLASS_NAME,
        "Running application lifecycle shutdown hook - Sending Terminate message to CertificateExpiryMonitorJob actor"
      )
      Future.successful {
        certificateExpiryLoggerActor ! Terminate
      }
    }
  )

  def extractExpiryDateFromCertificate(): Option[LocalDateTime] = {
    info(CLASS_NAME, "extractExpiryDateFromCertificate", "start")

    loadCertificate() match {
      case Success(certificate: X509Certificate) =>
        val expiryDate    = certificate.getNotAfter
        val localDateTime = expiryDate.toInstant.atZone(timeProvider.zoneId).toLocalDateTime
        info(CLASS_NAME, "extractExpiryDateFromCertificate", s"CERTIFICATE_EXPIRES $localDateTime")
        Some(localDateTime)
      case Success(cert)                         =>
        error(CLASS_NAME, "extractExpiryDateFromCertificate", s"Error loading cert, cert was of type: ${cert.getType}")
        None
      case Failure(exception)                    =>
        error(CLASS_NAME, "extractExpiryDateFromCertificate", s"Error loading cert $exception ")
        None
    }
  }

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

  def certificateStatus(): Boolean =
    getExpiryDate match {
      case Some(certificateExpiryDateTime) =>
        certificateExpiryDateTime.isAfter(LocalDateTime.now())
      case None                            =>
        false
    }

}
