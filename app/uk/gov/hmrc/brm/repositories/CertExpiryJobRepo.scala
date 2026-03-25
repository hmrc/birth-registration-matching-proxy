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
import org.mongodb.scala.bson.BsonDateTime
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model._
import uk.gov.hmrc.brm.models.CertExpiryJobDetails
import uk.gov.hmrc.brm.utils.BrmLogger.logger
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.time.{Duration, Instant}
import java.util.concurrent.TimeUnit.DAYS
import scala.concurrent.{ExecutionContext, Future}

trait CertExpiryJobRepo {

  def instanceShouldPerformCertExpiryCheck(
    jobId: String,
    interval: Duration,
    now: Instant
  ): Future[Boolean]

}

@Singleton
class CertExpiryJobRepoMongo @Inject() (val mongoComponent: MongoComponent)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[CertExpiryJobDetails](
      collectionName = "cert-expiry-job-details",
      mongoComponent = mongoComponent,
      domainFormat = CertExpiryJobDetails.format,
      indexes = Seq(
        IndexModel(
          Indexes.ascending("jobId"),
          IndexOptions().name("jobId_idx").unique(true) // unique index
        ),
        IndexModel(
          Indexes.ascending("lastAlertedAt"),
          IndexOptions()
            .name("lastAlertedAt_ttl") // ttl not used for alerting logic, to clean up orphaned records
            .expireAfter(90, DAYS)
        )
      ),
      replaceIndexes = true
    )
    with CertExpiryJobRepo {

  // By using a single jobId and making the field a unique index, an upsert with filter ensures the following operations happen:
  // A - if it's the first time we interact with the DB, we insert a record and return true
  // B - if the existing record matched the filter and was modified, return true, else false
  override def instanceShouldPerformCertExpiryCheck(
    jobId: String,
    checkInterval: Duration,
    now: Instant
  ): Future[Boolean] = {

    val bsonIntervalExpiry = BsonDateTime(now.minus(checkInterval).toEpochMilli)

    val filter = Filters.and(
      Filters.equal("jobId", jobId),
      Filters.lt("lastAlertedAt", bsonIntervalExpiry) // enough time has passed to perform a cert expiry check
    )

    val update: Bson = Updates.combine(
      Updates.set("jobId", jobId),
      Updates.set("lastAlertedAt", BsonDateTime(now.toEpochMilli))
    )

    collection
      .updateOne(filter, update, UpdateOptions().upsert(true))
      .toFuture()
      .map { record =>
        val isFirstInsert       = record.getUpsertedId != null
        val existingDocModified = record.getModifiedCount > 0

        isFirstInsert || existingDocModified
      }
      .recover {
        case _: com.mongodb.MongoWriteException =>
          logger.info(s"[CertExpiryJobRepoMongo][shouldPerformCertExpiryCheck] lost upsert race for $jobId")
          false
        case e                                  =>
          logger.error(s"[CertExpiryJobRepoMongo][shouldPerformCertExpiryCheck] failed: ${e.getMessage}")
          false
      }
  }

}
