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

import org.apache.pekko.actor.typed.scaladsl.TimerScheduler
import org.mockito.Mockito.spy
import org.mongodb.scala.model.Filters
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.brm.TestFixture
import uk.gov.hmrc.brm.models.CertExpiryJobDetails
import uk.gov.hmrc.brm.repositories.CertExpiryJobRepoMongo
import uk.gov.hmrc.brm.utils.TestHelperUtil
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

class CertificateExpiryMonitorJobMultipleInstancesSpec
    extends TestHelperUtil with TestFixture with DefaultPlayMongoRepositorySupport[CertExpiryJobDetails] {

  implicit override lazy val repository: CertExpiryJobRepoMongo = new CertExpiryJobRepoMongo(mongoComponent)

  val jobID = "certificate-expiry-monitor-job"

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    await(repository.collection.deleteMany(Filters.empty()).toFuture())
  }

  var timerSpy: PekkoTimer[CertificateExpiryMonitorJobCommand] = null

  val timer = (scheduler: TimerScheduler[CertificateExpiryMonitorJobCommand]) => {
    val realTimer = new PekkoTimer(scheduler)
    timerSpy = spy(realTimer)
    timerSpy
  }

  "CertificateExpiryMonitorJob with real mongo" should {

    "only one alert exists in mongo when multiple instances run" in {

      val certificateExpiry = now.plusHours(10)

      val actorLoop = (1 to 10).map { ele =>
        testKit.spawn(
          CertificateExpiryMonitorJob(
            certificateExpiry = certificateExpiry,
            timeProvider = timeProvider,
            config = createMockConfig(),
            timer
          )
        )
      }

      actorLoop.foreach(eachActor => eachActor ! CheckExpiry)

      val count = await(
        repository.collection
          .countDocuments(
            Filters.equal("jobId", jobID)
          )
          .toFuture()
      )

      count shouldBe 1L
    }
  }

}
