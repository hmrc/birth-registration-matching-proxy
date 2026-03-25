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
import uk.gov.hmrc.brm.certificate.ExpiryThreshold
import uk.gov.hmrc.brm.certificate.ExpiryThreshold._
import uk.gov.hmrc.brm.models.CertExpiryJobDetails
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.{Duration, Instant}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CertExpiryJobRepoLifecycleSpec
    extends AnyWordSpecLike with Matchers with DefaultPlayMongoRepositorySupport[CertExpiryJobDetails] {

  override lazy val repository = new CertExpiryJobRepoMongo(mongoComponent)

  val jobId                          = "certificate-expiry-monitor-job"
  val earlyWarningInterval: Duration = Duration.ofHours(168)
  val warningInterval: Duration      = Duration.ofHours(24)
  val criticalInterval: Duration     = Duration.ofHours(1)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    await(repository.collection.deleteMany(Filters.empty()).toFuture())
  }

  private def claim(threshold: ExpiryThreshold, interval: Duration, time: Instant): Boolean =
    await(repository.instanceShouldPerformCertExpiryCheck(jobId, threshold, interval, time))

  private def claimAsync(threshold: ExpiryThreshold, interval: Duration, time: Instant): Future[Boolean] =
    repository.instanceShouldPerformCertExpiryCheck(jobId, threshold, interval, time)

  private def documentCount(): Long =
    await(repository.collection.find(Filters.equal("jobId", jobId)).toFuture()).size

  "certificate expiry check lifecycle" should {

    "walk through all thresholds from early warning to expired" in {
      val t0 = Instant.parse("2026-01-01T00:00:00Z")

      // Early warning: first check claims, subsequent checks within interval are suppressed
      claim(EarlyWarning, earlyWarningInterval, t0)                              shouldBe true
      claim(EarlyWarning, earlyWarningInterval, t0.plus(Duration.ofMinutes(15))) shouldBe false
      claim(EarlyWarning, earlyWarningInterval, t0.plus(Duration.ofDays(6)))     shouldBe false


      // Early warning: interval elapses, re-alert fires, then suppressed again
      claim(EarlyWarning, earlyWarningInterval, t0.plus(earlyWarningInterval).plusSeconds(1)) shouldBe true

      claim(
        EarlyWarning,
        earlyWarningInterval,
        t0.plus(earlyWarningInterval).plus(Duration.ofMinutes(15))
      ) shouldBe false


      // Escalation to WARNING: immediately claims despite early warning interval not elapsed
      val warningStart = t0.plus(earlyWarningInterval).plus(Duration.ofHours(1))
      claim(Warning, warningInterval, warningStart)                            shouldBe true
      claim(Warning, warningInterval, warningStart.plus(Duration.ofHours(12))) shouldBe false

      // Warning: interval elapses, re-alert fires
      claim(Warning, warningInterval, warningStart.plus(warningInterval).plusSeconds(1)) shouldBe true


      // Escalation to CRITICAL_WARNING: immediately claims
      val criticalStart = warningStart.plus(warningInterval).plus(Duration.ofHours(1))
      claim(CriticalWarning, criticalInterval, criticalStart)                              shouldBe true
      claim(CriticalWarning, criticalInterval, criticalStart.plus(Duration.ofMinutes(30))) shouldBe false

      // Critical: interval elapses, re-alert fires
      claim(CriticalWarning, criticalInterval, criticalStart.plus(criticalInterval).plusSeconds(1)) shouldBe true


      // escalation to EXPIRED: immediately claims, keeps re-alerting each interval
      val expiredStart = criticalStart.plus(criticalInterval).plus(Duration.ofHours(1))
      claim(Expired, criticalInterval, expiredStart)                                       shouldBe true
      claim(Expired, criticalInterval, expiredStart.plus(Duration.ofMinutes(30)))          shouldBe false
      claim(Expired, criticalInterval, expiredStart.plus(criticalInterval).plusSeconds(1)) shouldBe true

      // One document through the entire lifecycle
      documentCount() shouldBe 1
    }

    "allow only one winner when two instances race at each threshold transition" in {
      val t0 = Instant.parse("2026-01-01T00:00:00Z")

      Seq(
        (EarlyWarning, earlyWarningInterval, t0),
        (Warning, warningInterval, t0.plus(Duration.ofHours(1))),
        (CriticalWarning, criticalInterval, t0.plus(Duration.ofHours(2))),
        (Expired, criticalInterval, t0.plus(Duration.ofHours(3)))
      ).foreach { case (threshold, interval, time) =>
        val results = await(
          Future.sequence(
            Seq(
              claimAsync(threshold, interval, time),
              claimAsync(threshold, interval, time.plusMillis(1))
            )
          )
        )

        results.count(_ == true) shouldBe 1
      }

      documentCount() shouldBe 1
    }
  }

}
