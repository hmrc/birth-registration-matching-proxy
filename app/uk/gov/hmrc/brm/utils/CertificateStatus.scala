/*
 * Copyright 2025 HM Revenue & Customs
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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.scaladsl.adapter._
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.brm.config.GroAppConfig
import uk.gov.hmrc.brm.utils.BrmLogger._

import java.io.FileInputStream
import java.security.KeyStore
import java.security.cert.{Certificate, X509Certificate}
import java.time.{LocalDateTime, ZoneId}
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.jdk.CollectionConverters.EnumerationHasAsScala
import scala.util.{Failure, Success, Try, Using}

// temp note: singleton as we shouldn't have two actors with the same name
@Singleton
class CertificateStatus @Inject() (
  val groConfig: GroAppConfig,
  lifecycle: ApplicationLifecycle,
  actorSystem: ActorSystem
) extends CertificateProvider {

  lazy val getExpiryDate: Option[LocalDateTime] = extractExpiryDateFromCertificate()

  implicit val logger = BrmLogger

  protected val CLASS_NAME: String = this.getClass.getSimpleName

  // convert play's actor system to typed to use with our typed actor
  val typedActorSystem = actorSystem.toTyped

  // todo: should this be systemActorOf, seems like this is discouraged in the source?
  private val certificateExpiryLoggerActorOpt: Option[ActorRef[CertificateExpiryLogger.LoggerCommand]] =
    getExpiryDate.map { expiryDate =>
      info(CLASS_NAME, "Registering CertificateExpiryLogger actor")

      typedActorSystem.systemActorOf(
        CertificateExpiryLogger(expiryDate, groConfig),
        "certificate-expiry-logger"
      )
    }

  certificateExpiryLoggerActorOpt.foreach(certificateExpiryLoggerActor =>
    lifecycle.addStopHook { () =>
      info(CLASS_NAME, "Stopping CertificateExpiryLogger actor")
      Future.successful {
        certificateExpiryLoggerActor ! CertificateExpiryLogger.Stop
      }
    }
  )

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

  def extractExpiryDateFromCertificate(): Option[LocalDateTime] = {
    info(CLASS_NAME, "extractExpiryDateFromCertificate", "start")

    loadCertificate() match {
      case Success(certificate: X509Certificate) =>
        val expiryDate    = certificate.getNotAfter
        val localDateTime = expiryDate.toInstant.atZone(ZoneId.of("UTC")).toLocalDateTime
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

  def certificateStatus(): Boolean =
    getExpiryDate match {
      case Some(certificateExpiryDateTime) =>
        certificateExpiryDateTime.isAfter(LocalDateTime.now())
      case None                            =>
        false
    }

}
