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

import org.mockito.Mockito.when
import org.mongodb.scala.model.Filters
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.brm.TestFixture
import uk.gov.hmrc.brm.config.GroAppConfig
import uk.gov.hmrc.brm.models.CertExpiryJobDetails
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.Instant

class CertExpiryJobRepoMongoSpec extends TestFixture with DefaultPlayMongoRepositorySupport[CertExpiryJobDetails] {

  override lazy val repository = new CertExpiryJobRepoMongo(mongoComponent, testGroConfig)

  private val testGroAppConfig: GroAppConfig = mock[GroAppConfig]
  when(testGroAppConfig.cachettl).thenReturn(24)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    await(repository.collection.deleteMany(Filters.empty()).toFuture())
  }

  "markAlertSent" should {

    "return true when alert is inserted for the first time" in {
      val countBefore = await(repository.collection.countDocuments().toFuture())
      println("###################### " + countBefore)
      countBefore mustBe 0L

      val result =
        repository
          .markAlertSent(
            jobId = "certificate-expiry-monitor-job",
            expiryDate = "2026-12-31",
            threshold = "EARLY_WARNING",
            nowEpochMs = Instant.now().toEpochMilli
          )
          .futureValue

      result mustBe true
    }

    "return false when the same jobId, expiryDate and threshold already exists" in {
      repository
        .markAlertSent(
          jobId = "certificate-expiry-monitor-job",
          expiryDate = "2026-12-31",
          threshold = "EARLY_WARNING",
          nowEpochMs = Instant.now().toEpochMilli
        )
        .futureValue mustBe true

      val result =
        repository
          .markAlertSent(
            jobId = "certificate-expiry-monitor-job",
            expiryDate = "2026-12-31",
            threshold = "EARLY_WARNING",
            nowEpochMs = Instant.now().toEpochMilli
          )
          .futureValue

      result mustBe false
    }

    "return true when threshold is different for the same jobId and expiryDate" in {
      repository
        .markAlertSent(
          jobId = "certificate-expiry-monitor-job",
          expiryDate = "2026-12-31",
          threshold = "EARLY_WARNING",
          nowEpochMs = Instant.now().toEpochMilli
        )
        .futureValue mustBe true

      val result =
        repository
          .markAlertSent(
            jobId = "certificate-expiry-monitor-job",
            expiryDate = "2026-12-31",
            threshold = "WARNING",
            nowEpochMs = Instant.now().toEpochMilli
          )
          .futureValue

      result mustBe true
    }

    "return true when expiryDate is different for the same jobId and threshold" in {
      repository
        .markAlertSent(
          jobId = "certificate-expiry-monitor-job",
          expiryDate = "2026-12-31",
          threshold = "EARLY_WARNING",
          nowEpochMs = Instant.now().toEpochMilli
        )
        .futureValue mustBe true

      val result =
        repository
          .markAlertSent(
            jobId = "certificate-expiry-monitor-job",
            expiryDate = "2027-01-31",
            threshold = "EARLY_WARNING",
            nowEpochMs = Instant.now().toEpochMilli
          )
          .futureValue

      result mustBe true
    }

  }

}
