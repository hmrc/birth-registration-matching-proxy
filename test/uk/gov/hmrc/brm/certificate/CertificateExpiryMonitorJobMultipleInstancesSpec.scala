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

import org.mongodb.scala.model.Filters
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.brm.certificate.ExpiryThreshold.CriticalWarning
import uk.gov.hmrc.brm.repositories.CertExpiryJobRepoMongo

class CertificateExpiryMonitorJobMultipleInstancesSpec extends CertificateExpiryMonitorJobSpec {

  val stubConfig             = createMockConfig()
  val certExpiryJobRepoMongo = real[CertExpiryJobRepoMongo]
  val jobID                  = "certificate-expiry-monitor-job-test"

  override def afterEach(): Unit = {
    super.afterEach()
    await(
      certExpiryJobRepoMongo.collection
        .deleteMany(Filters.equal("jobId", jobID))
        .toFuture()
    )
  }

  "CertificateExpiryMonitorJob with real mongo" should {

    "only one alert exists in mongo when multiple instances run" in {

      val certificateExpiry = now.plusHours(10)

      val actorLoop = (1 to 10).map { ele =>
        testKit.spawn(
          CertificateExpiryMonitorJob(
            certificateExpiry = certificateExpiry,
            timeProvider = timeProvider,
            config = stubConfig,
            certExpiryJobRepo = certExpiryJobRepoMongo,
            jobId = jobID
          )
        )
      }

      actorLoop.foreach(eachActor => eachActor ! CheckExpiry)

      val count = await(
        certExpiryJobRepoMongo.collection
          .countDocuments(
            Filters.and(
              Filters.equal("jobId", jobID),
              Filters.equal("threshold", CriticalWarning.value)
            )
          )
          .toFuture()
      )

      count shouldBe 1L
    }
  }

}
