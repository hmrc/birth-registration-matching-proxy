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
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import scala.concurrent.{ExecutionContext, Future}


trait CertExpiryJobRepo {
  def markAlertSent(jobId: String, expiryDate: String, threshold: String, nowEpochMs: Long): Future[Boolean]
}

@Singleton
class CertExpiryJobRepoMongo @Inject()(
                                        val mongoComponent: MongoComponent
                                      )(implicit ec: ExecutionContext)
  extends PlayMongoRepository[CertExpiryJobDetails](
    collectionName = "cert-expiry-job-details",
    mongoComponent = mongoComponent,
    domainFormat = CertExpiryJobDetails.format,
    indexes = Seq(
      IndexModel(
        Indexes.ascending("jobId", "expiryDate", "threshold"),
        IndexOptions().name("jobId_expiry_threshold_unique").unique(true)
      )),
    replaceIndexes = false
  ) with CertExpiryJobRepo {

  override def markAlertSent(jobId: String, expiryDate: String, threshold: String, nowEpochMs: Long): Future[Boolean] = {

    val filter = Filters.and(
      Filters.equal("jobId", jobId),
      Filters.equal("expiryDate", expiryDate),
      Filters.equal("threshold", threshold)
    )

    val update = Updates.combine(
      Updates.setOnInsert("jobId", jobId),
      Updates.setOnInsert("expiryDate", expiryDate),
      Updates.setOnInsert("threshold", threshold),
      Updates.setOnInsert("createdAt", nowEpochMs)
    )

    collection
      .updateOne(filter, update, new UpdateOptions().upsert(true))
      .toFuture()
      .map(result => result.getUpsertedId != null)
      .recover { case _ => false }

  }
}
