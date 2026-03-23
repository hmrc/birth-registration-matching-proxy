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

import org.mongodb.scala.model.Filters
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.brm.certificate.ExpiryThreshold.{EarlyWarning, Warning}
import uk.gov.hmrc.brm.models.CertExpiryJobDetails
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.{Duration, Instant}
import java.time.temporal.ChronoUnit
import scala.concurrent.{ExecutionContext, Future}

class CertExpiryJobRepoMongoSpec
    extends AnyWordSpecLike with Matchers with DefaultPlayMongoRepositorySupport[CertExpiryJobDetails] {

  implicit val ec: ExecutionContext = ExecutionContext.global

  override lazy val repository = new CertExpiryJobRepoMongo(mongoComponent)

  val jobId                          = "certificate-expiry-monitor-job-test"
  val earlyWarningInterval: Duration = Duration.ofHours(168)
  val warningInterval: Duration      = Duration.ofHours(24)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    await(repository.collection.deleteMany(Filters.empty()).toFuture())
  }

  private def now(): Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)

  "shouldPerformCertExpiryCheck" should {

    "return true when no record exists (first ever check)" in {
      val result = await(repository.shouldPerformCertExpiryCheck(jobId, EarlyWarning, earlyWarningInterval, now()))
      result shouldBe true
    }

    "return false on the second call with the same threshold (suppression)" in {
      val instant = now()
      await(repository.shouldPerformCertExpiryCheck(jobId, EarlyWarning, earlyWarningInterval, instant))

      val result = await(repository.shouldPerformCertExpiryCheck(jobId, EarlyWarning, earlyWarningInterval, now()))
      result shouldBe false
    }

    "return true when the threshold escalates" in {
      await(repository.shouldPerformCertExpiryCheck(jobId, EarlyWarning, earlyWarningInterval, now()))

      val result = await(repository.shouldPerformCertExpiryCheck(jobId, Warning, warningInterval, now()))
      result shouldBe true
    }

    "return false when the threshold has not changed and the interval has not elapsed" in {
      await(repository.shouldPerformCertExpiryCheck(jobId, Warning, warningInterval, now()))

      val result = await(repository.shouldPerformCertExpiryCheck(jobId, Warning, warningInterval, now()))
      result shouldBe false
    }

    "return true when the interval has elapsed for the same threshold" in {
      // Insert a record with a lastAlertedAt far enough in the past
      val staleTime = now().minus(warningInterval).minusSeconds(1)
      await(repository.shouldPerformCertExpiryCheck(jobId, Warning, warningInterval, staleTime))

      // Now a fresh check should succeed because the interval has elapsed
      val result = await(repository.shouldPerformCertExpiryCheck(jobId, Warning, warningInterval, now()))
      result shouldBe true
    }

    "allow only one winner when called concurrently" in {
      // Simulate two instances calling shouldPerformCertExpiryCheck at the same time
      val instantA = now()
      val instantB = instantA.plusMillis(1) // slightly different timestamps

      val futureA = repository.shouldPerformCertExpiryCheck(jobId, EarlyWarning, earlyWarningInterval, instantA)
      val futureB = repository.shouldPerformCertExpiryCheck(jobId, EarlyWarning, earlyWarningInterval, instantB)

      val results = await(Future.sequence(Seq(futureA, futureB)))

      // Exactly one should have claimed the alert
      results.count(_ == true)  shouldBe 1
      results.count(_ == false) shouldBe 1
    }

    "allow only one winner across many concurrent calls" in {
      // Simulate five instances all racing at once
      val instants = (0 until 5).map(i => now().plusMillis(i))

      val futures = instants.map { instant =>
        repository.shouldPerformCertExpiryCheck(jobId, EarlyWarning, earlyWarningInterval, instant)
      }

      val results = await(Future.sequence(futures))

      // Exactly one winner, four losers
      results.count(_ == true)  shouldBe 1
      results.count(_ == false) shouldBe 4
    }

    "only store one document regardless of how many calls are made" in {
      val instants = (0 until 5).map(i => now().plusMillis(i))
      val futures  = instants.map { instant =>
        repository.shouldPerformCertExpiryCheck(jobId, EarlyWarning, earlyWarningInterval, instant)
      }
      await(Future.sequence(futures))

      val docs = await(repository.collection.find(Filters.equal("jobId", jobId)).toFuture())
      docs.size shouldBe 1
    }
  }

}
