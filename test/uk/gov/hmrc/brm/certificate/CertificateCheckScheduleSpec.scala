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

import uk.gov.hmrc.brm.TestFixture

import java.time.{Duration, LocalDateTime}

class CertificateCheckScheduleSpec extends TestFixture {

  "CertificateCheckSchedule" should {

    "getTimeUntilCertExpiry" in {
      val currentTime       = LocalDateTime.now()
      val certificateExpiry = currentTime.plusHours(100)

      val schedule = new CertificateCheckSchedule(testGroConfig, certificateExpiry)

      schedule.getTimeUntilCertExpiry(currentTime) shouldBe Duration.ofHours(100)
    }

  }

}
