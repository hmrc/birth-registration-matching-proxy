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
import uk.gov.hmrc.brm.certificate.ExpiryThreshold
import uk.gov.hmrc.brm.models.CertExpiryJobDetails
import uk.gov.hmrc.brm.utils.BrmLogger.logger
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.time.{Duration, Instant}
import scala.concurrent.{ExecutionContext, Future}

import org.mongodb.scala.bson.BsonDateTime
import org.mongodb.scala.model.UpdateOptions

trait CertExpiryJobRepo {

  /**
   * Manages distributed coordination for certificate expiry alerting across
   * multiple service instances.
   *
   * Uses a single MongoDB document per job to deduplicate alerts. The document
   * stores the current threshold level and the time the last alert was sent.
   * A new alert fires when:
   *   - the threshold escalates (e.g. EARLY_WARNING -> WARNING), or
   *   - the configured check interval for the current threshold elapses
   *
   * Coordination is achieved via an atomic findOneAndUpdate with upsert,
   * guarded by a unique index on jobId. When multiple instances poll
   * simultaneously, only one can match and update the document — the rest
   * see no match and skip.
   */
  def shouldPerformCertExpiryCheck(
    jobId: String,
    threshold: ExpiryThreshold,
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
          IndexOptions().name("lastAlertedAt_ttl").expireAfter(90, java.util.concurrent.TimeUnit.DAYS)
        )
      ),
      replaceIndexes = true
    )
    with CertExpiryJobRepo {

  override def shouldPerformCertExpiryCheck(
    jobId: String,
    threshold: ExpiryThreshold,
    interval: Duration,
    now: Instant
  ): Future[Boolean] = {

    val bsonNow    = BsonDateTime(now.toEpochMilli)
    val bsonCutoff = BsonDateTime(now.minus(interval).toEpochMilli)

    // Matches when this instance should fire an alert:
    // - threshold has changed: cert moved into a more severe window (e.g. EARLY_WARNING -> WARNING)
    // - interval elapsed: enough time has passed to re-alert at the same severity level
    // If neither is true, the filter matches nothing and the upsert is blocked by the unique index.
    val filter = Filters.and(
      Filters.equal("jobId", jobId),
      Filters.or(
        Filters.ne("threshold", threshold.value),
        Filters.lt("lastAlertedAt", bsonCutoff)
      )
    )

    val update = Updates.combine(
      Updates.set("jobId", jobId),
      Updates.set("threshold", threshold.value),
      Updates.set("lastAlertedAt", bsonNow)
    )

    collection
      .updateOne(filter, update, UpdateOptions().upsert(true))
      .toFuture()
      .map { result =>
        // getUpsertedId is non-null when no document existed and one was inserted (first ever check).
        // getModifiedCount > 0 when an existing document matched the filter and was updated
        // (threshold escalated or interval elapsed).
        // Both being false means the filter didn't match and the unique index blocked the insert —
        // another instance already handled this check.
        result.getModifiedCount > 0 || Option(result.getUpsertedId).isDefined
      }
      .recover {
        case _: com.mongodb.MongoWriteException =>
          logger.info(s"[CertExpiryJobRepoMongo][tryClaimAlert] lost upsert race for $jobId")
          false
        case e                                  =>
          logger.error(s"[CertExpiryJobRepoMongo][tryClaimAlert] failed: ${e.getMessage}")
          false
      }
  }

}
