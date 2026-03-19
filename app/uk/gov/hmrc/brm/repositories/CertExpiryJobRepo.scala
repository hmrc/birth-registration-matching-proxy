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

package uk.gov.hmrc.brm.repositories

import com.google.inject.{Inject, Singleton}
import org.mongodb.scala.model._
import uk.gov.hmrc.brm.models.CertExpiryJobDetails
import uk.gov.hmrc.brm.utils.BrmLogger.logger
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}

trait CertExpiryJobRepo {

  def getAlertDetails(
                       jobId: String,
                       expiryDate: Instant,
                       threshold: String
                     ): Future[Boolean]

  def insertAlertDetails(
                          jobId: String,
                          expiryDate: Instant,
                          threshold: String
                        ): Future[Boolean]
}

@Singleton
class CertExpiryJobRepoMongo @Inject()(val mongoComponent: MongoComponent
                                      )(implicit ec: ExecutionContext)
  extends PlayMongoRepository[CertExpiryJobDetails](
    collectionName = "cert-expiry-job-details",
    mongoComponent = mongoComponent,
    domainFormat = CertExpiryJobDetails.format,
    indexes = Seq(
      IndexModel(Indexes.ascending("jobId")),
      IndexModel(
        Indexes.ascending("expiryDate"),
        IndexOptions()
          .name("expiryDate_ttl")
          .expireAfter(0, TimeUnit.SECONDS)
      )
    ),
    replaceIndexes = true
  )
    with CertExpiryJobRepo {

  override def getAlertDetails(
                                jobId: String,
                                expiryDate: Instant,
                                threshold: String
                              ): Future[Boolean] = {

    val filter = Filters.and(
      Filters.equal("jobId", jobId)
    )

    val res = collection
      .find(filter)
      .headOption()
      .map(_.isDefined)
      .recover { case e =>
        logger.error(s"[CertExpiryJobRepoMongo][getAlertDetails] failed: ${e.getMessage}")
        false
      }
    res
  }

  override def insertAlertDetails(
                                   jobId: String,
                                   expiryDate: Instant,
                                   threshold: String
                                 ): Future[Boolean] = {
    val doc = CertExpiryJobDetails(
      jobId = jobId,
      expiryDate = expiryDate,
      threshold = threshold
    )

    collection
      .insertOne(doc)
      .toFuture()
      .map { _ =>
        logger.info(s"[CertExpiryJobRepoMongo][insertAlertDetails] inserted")
        true
      }
      .recover { case e =>
        logger.error(s"[CertExpiryJobRepoMongo][insertAlertDetails] failed: ${e.getMessage}")
        false
      }
  }

}
