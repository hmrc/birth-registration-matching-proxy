/*
 * Copyright 2016 HM Revenue & Customs
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

package utils

import org.joda.time.{DateTime, DateTimeUtils, Minutes}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import uk.gov.hmrc.brm.utils.AccessTokenRepository
import uk.gov.hmrc.play.test.UnitSpec

import scala.util.{Failure, Success}

/**
 * Created by adamconder on 05/10/2016.
 */
class AccessTokenRepositorySpec extends UnitSpec with BeforeAndAfter {

  var accessTokenRepository = new AccessTokenRepository

  before {
     accessTokenRepository = new AccessTokenRepository
  }

  "AccessTokenRepository" when {

    "hasToken" should {

      "return true for a token has been set" in {
        val dateTime = DateTime.now()
        accessTokenRepository.saveToken(token = "some_valid_token", expiry = dateTime)
        accessTokenRepository.hasToken shouldBe true
        accessTokenRepository.token.isSuccess shouldBe true
      }

    }


    "hasExpired" should {
      "return true when no value is set " in {
         accessTokenRepository.hasExpired shouldBe true
      }
    }

    "updating" should {

      "replace the access_token with a new valid token" in {
        val dateTime = DateTime.now()
        accessTokenRepository.saveToken(token = "some_valid_token", expiry = dateTime)

        val expiryDate = dateTime.plusMinutes(5)

        accessTokenRepository.saveToken(token = "some_new_valid_token", expiry = expiryDate)
        accessTokenRepository.token.get shouldBe "some_new_valid_token"
        accessTokenRepository.token.isSuccess shouldBe true
      }

      "return a new expiry time 5 minutes from now" in {
        val seconds = 300
        val expiry = accessTokenRepository.newExpiry(seconds)
        Minutes.minutesBetween(DateTime.now, expiry).getMinutes shouldBe 5
      }

    }

    "valid" should {

      "have an access_token" in {
        val dateTime = DateTime.now()
        accessTokenRepository.saveToken(token = "some_valid_token", expiry = dateTime)
        accessTokenRepository.token.get shouldBe "some_valid_token"
      }

      "return success with token when access token with expiry time" in {
        val dateTime = DateTime.now()
        val expiryDate = dateTime.plusMinutes(5)
        accessTokenRepository.saveToken(token = "some_valid_token", expiry = expiryDate)
        DateTimeUtils.setCurrentMillisFixed(dateTime.plusMinutes(4).getMillis)
        accessTokenRepository.token.get shouldBe "some_valid_token"
        accessTokenRepository.token.isSuccess shouldBe true
        DateTimeUtils.setCurrentMillisSystem()
      }

    }

    "expired" should {
      "return failure for expiry access token" in {
        val dateTime = DateTime.now()
        val expiryDate = dateTime.plusMinutes(5)
        accessTokenRepository.saveToken(token = "some_valid_token", expiry = expiryDate)
        DateTimeUtils.setCurrentMillisFixed(dateTime.plusMinutes(6).getMillis)
        accessTokenRepository.token.isFailure shouldBe true
        DateTimeUtils.setCurrentMillisSystem()
      }

    }
  }

}
