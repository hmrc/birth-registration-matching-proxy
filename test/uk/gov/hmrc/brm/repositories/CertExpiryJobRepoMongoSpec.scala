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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{spy, when}
import org.mongodb.scala.{MongoCollection, SingleObservable}
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.{Filters, UpdateOptions}
import org.mongodb.scala.result.UpdateResult
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.brm.models.CertExpiryJobDetails
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.temporal.ChronoUnit
import java.time.{Duration, Instant}
import scala.concurrent.{ExecutionContext, Future}

class CertExpiryJobRepoMongoSpec
    extends AnyWordSpecLike with Matchers with DefaultPlayMongoRepositorySupport[CertExpiryJobDetails] {

  implicit val ec: ExecutionContext = ExecutionContext.global

  override lazy val repository = new CertExpiryJobRepoMongo(mongoComponent)

  val jobId                               = "certificate-expiry-monitor-job-test"
  val earlyWarningCheckInterval: Duration = Duration.ofHours(168)
  val warningCheckInterval: Duration      = Duration.ofHours(24)
  val criticalCheckInterval: Duration     = Duration.ofHours(1)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    await(repository.collection.deleteMany(Filters.empty()).toFuture())
  }

  private def shouldPerformExpiryCheck(checkInterval: Duration, time: Instant): Boolean =
    await(repository.instanceShouldPerformCertExpiryCheck(jobId, checkInterval, time))

  private def documentCount(): Long =
    await(repository.collection.find(Filters.equal("jobId", jobId)).toFuture()).size

  private def now(): Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)

  "instanceShouldPerformCertExpiryCheck" should {

    "return true when no record exists (first check)" in {
      val result = shouldPerformExpiryCheck(earlyWarningCheckInterval, now())
      result shouldBe true
    }

    "return false on the second call" in {
      val instant = now()
      shouldPerformExpiryCheck(earlyWarningCheckInterval, instant)

      val result = shouldPerformExpiryCheck(earlyWarningCheckInterval, now())
      result shouldBe false
    }

    "return true when the checkInterval has elapsed" in {
      // Insert a record with a lastAlertedAt far enough in the past
      val initialInsertTime = now().minus(warningCheckInterval).minusSeconds(1)
      shouldPerformExpiryCheck(warningCheckInterval, initialInsertTime)

      val result = shouldPerformExpiryCheck(warningCheckInterval, now())
      result shouldBe true
    }

    "allow only one upsert across many concurrent calls" in {
      // simulate five instances all racing at once
      val instants = (0 until 5).map(i => now().plusMillis(i))

      val futures = instants.map { instant =>
        repository.instanceShouldPerformCertExpiryCheck(jobId, earlyWarningCheckInterval, instant)
      }

      val results = await(Future.sequence(futures))

      // one successful upsert, four failures
      results.count(_ == true)  shouldBe 1
      results.count(_ == false) shouldBe 4
    }

    "walk through decreasing checkIntervals as expiry approaches, reusing one document" in {
      val t0 = Instant.parse("2026-01-01T00:00:00Z")

      // early warning checkInterval
      shouldPerformExpiryCheck(earlyWarningCheckInterval, t0)                          shouldBe true
      shouldPerformExpiryCheck(earlyWarningCheckInterval, t0.plus(Duration.ofDays(6))) shouldBe false

      val t1 = t0.plus(Duration.ofHours(25)) // 1 hour after early warning interval
      shouldPerformExpiryCheck(warningCheckInterval, t1) shouldBe true

      val t2 = t1.plus(Duration.ofHours(2)) // 1 hour after critical warning interval
      shouldPerformExpiryCheck(criticalCheckInterval, t2)                                          shouldBe true
      shouldPerformExpiryCheck(criticalCheckInterval, t2.plus(Duration.ofMinutes(30)))             shouldBe false
      shouldPerformExpiryCheck(criticalCheckInterval, t2.plus(Duration.ofHours(1)).plusSeconds(1)) shouldBe true

      documentCount() shouldBe 1
    }

    "return false when an error that is not a MongoWriteException occurs" in {
      val mockCollection = mock[MongoCollection[CertExpiryJobDetails]]
      val repo = spy(new CertExpiryJobRepoMongo(mongoComponent))

      val singleObservableMock = mock[SingleObservable[UpdateResult]]
      when(singleObservableMock.toFuture()).thenReturn(Future.failed(new Exception("!")))

      when(repo.collection).thenReturn(mockCollection)
      when(mockCollection.updateOne(any[Bson], any[Bson], any[UpdateOptions])).thenReturn(singleObservableMock)

      val result = await(repo.instanceShouldPerformCertExpiryCheck(jobId, criticalCheckInterval, now()))
      result shouldBe false
    }

  }

}
