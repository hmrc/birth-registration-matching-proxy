/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.brm.connectors

import org.joda.time.{DateTime, DateTimeUtils, Seconds}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.util.Success

/**
  * Created by adamconder on 14/11/2016.
  */
class AuthenticatorSpec extends UnitSpec with WithFakeApplication {

  "Authenticator" when {

    "creating an instance of cache" should {

      "return false for having a token" in {
        val authenticator = Authenticator.apply()
        authenticator.tokenCache.hasToken shouldBe false
        authenticator.tokenCache.hasExpired shouldBe true
      }

    }

    "saving a new token" should {

      "insert a token" in {
        val authenticator = Authenticator.apply()
        authenticator.tokenCache.saveToken("new token", DateTime.now.plusDays(2))
        authenticator.tokenCache.hasToken shouldBe true
        authenticator.tokenCache.hasExpired shouldBe false
        authenticator.tokenCache.token shouldBe Success("new token")
      }

      "generate new expiry" in {
        val authenticator = Authenticator.apply()
        val dateTime = new DateTime()
        DateTimeUtils.setCurrentMillisFixed(dateTime.getMillis)
        val expiryTime = authenticator.tokenCache.newExpiry(100)
        //expiry time shd be less by 60 sec.
        Seconds.secondsBetween(dateTime, expiryTime).getSeconds shouldBe 40
        DateTimeUtils.setCurrentMillisSystem()
      }

    }

  }

}
