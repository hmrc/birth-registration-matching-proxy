/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.brm.utils

import org.joda.time.{DateTime, DateTimeUtils, Seconds}
import uk.gov.hmrc.brm.TestFixture

class AccessTokenRepositorySpec extends TestFixture {

  val accessTokenRepository = new AccessTokenRepository

  "AccessTokenRepository" when {

    "hasToken" should {

      "return true for a token has been set" in {
        val dateTime = DateTime.now().plusSeconds(60)
        accessTokenRepository.saveToken(token = "some_valid_token", expiry = dateTime)
        accessTokenRepository.hasToken        shouldBe true
        accessTokenRepository.token.isSuccess shouldBe true
      }

    }

    "hasExpired" should {
      "return true when no value is set " in {
        val accessTokenRepository = new AccessTokenRepository
        accessTokenRepository.hasExpired shouldBe true
      }
    }

    "updating" should {

      "replace the access_token with a new valid token" in {
        val dateTime = DateTime.now()
        accessTokenRepository.saveToken(token = "some_valid_token", expiry = dateTime)

        val expiryDate = dateTime.plusMinutes(5)

        accessTokenRepository.saveToken(token = "some_new_valid_token", expiry = expiryDate)
        accessTokenRepository.token.get       shouldBe "some_new_valid_token"
        accessTokenRepository.token.isSuccess shouldBe true
      }

      "return a new expiry time 4 minutes from now when actual expiry is 5 minutes from now." in {
        val dateTime = new DateTime()
        DateTimeUtils.setCurrentMillisFixed(dateTime.getMillis)
        val seconds  = 300 //expire seconds 5 mins.
        val expiry   = accessTokenRepository.newExpiry(seconds)
        //new expriy should be less than 60 sec.
        Seconds.secondsBetween(dateTime, expiry).getSeconds shouldBe 240
      }

    }

    "valid" should {

      "have an access_token" in {
        val dateTime = DateTime.now()
        accessTokenRepository.saveToken(token = "some_valid_token", expiry = dateTime)
        accessTokenRepository.token.get shouldBe "some_valid_token"
      }

      "return success with token when access token with expiry time" in {
        val dateTime   = DateTime.now()
        val expiryDate = dateTime.plusMinutes(5)
        accessTokenRepository.saveToken(token = "some_valid_token", expiry = expiryDate)
        DateTimeUtils.setCurrentMillisFixed(dateTime.plusMinutes(4).getMillis)
        accessTokenRepository.token.get       shouldBe "some_valid_token"
        accessTokenRepository.token.isSuccess shouldBe true
        DateTimeUtils.setCurrentMillisSystem()
      }

    }

    "expired" should {
      "return failure for expiry access token" in {
        val dateTime   = DateTime.now()
        val expiryDate = dateTime.plusMinutes(5)
        accessTokenRepository.saveToken(token = "some_valid_token", expiry = expiryDate)
        DateTimeUtils.setCurrentMillisFixed(dateTime.plusSeconds(301).getMillis)
        accessTokenRepository.token.isFailure shouldBe true
        DateTimeUtils.setCurrentMillisSystem()
      }

      "return failure for expiry access token when set using new expiry" in {
        val dateTime = DateTime.now()
        accessTokenRepository.saveToken(token = "some_valid_token", accessTokenRepository.newExpiry(300))
        DateTimeUtils.setCurrentMillisFixed(dateTime.plusSeconds(242).getMillis)
        accessTokenRepository.token.isFailure shouldBe true
        DateTimeUtils.setCurrentMillisSystem()
      }

    }
  }

}
