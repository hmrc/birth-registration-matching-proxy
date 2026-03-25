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

import org.mongodb.scala._
import org.mongodb.scala.model.Filters
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.brm.TestFixture
import uk.gov.hmrc.brm.certificate.ExpiryThreshold._
import uk.gov.hmrc.brm.models.CertExpiryJobDetails
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.Instant
import java.time.temporal.ChronoUnit

class CertExpiryJobRepoMongoSpec extends TestFixture with DefaultPlayMongoRepositorySupport[CertExpiryJobDetails] {

  override lazy val repository = new CertExpiryJobRepoMongo(mongoComponent)
  val jobId                    = "certificate-expiry-monitor-job-test"

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    await(repository.collection.deleteMany(Filters.empty()).toFuture())
  }

  private def now(): Instant =
    Instant.now().truncatedTo(ChronoUnit.MILLIS)

  "getAlertDetails" should {

    "return false when document does not exist" in {
      val result = await(
        repository.getAlertDetails(jobId, now(), Warning.value)
      )
      result shouldBe false
    }

    "return true when document exists" in {
      await(repository.insertAlertDetails(jobId, now(), Warning.value))
      val result = await(
        repository.getAlertDetails(jobId, now(), Warning.value)
      )
      result shouldBe true
    }
  }

  "insertAlertDetails" should {

    "insert document successfully" in {
      val result = await(
        repository.insertAlertDetails(jobId, now(), Warning.value)
      )
      result shouldBe true
    }

    "allow retrieval after insert" in {
      await(repository.insertAlertDetails(jobId, now(), CriticalWarning.value))
      val exists = await(
        repository.getAlertDetails(jobId, now(), CriticalWarning.value)
      )
      exists shouldBe true
    }
  }

}
