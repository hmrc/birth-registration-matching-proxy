/*
 * Copyright 2024 HM Revenue & Customs
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

import org.mockito.Mockito.when
import uk.gov.hmrc.brm.TestFixture

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit.SECONDS

class AccessTokenRepositorySpec extends TestFixture {

  val accessTokenRepository = new AccessTokenRepository(new TimeProvider)

  "AccessTokenRepository" when {

    "hasToken" should {

      "return true for a token has been set" in {
        val dateTime = ZonedDateTime.now().plusSeconds(60)
        accessTokenRepository.saveToken(token = "some_valid_token", expiry = dateTime)
        accessTokenRepository.hasToken        shouldBe true
        accessTokenRepository.token.isSuccess shouldBe true
      }

    }

    "hasExpired" should {

      "return true when no value is set " in {
        val accessTokenRepository = new AccessTokenRepository(new TimeProvider)
        accessTokenRepository.hasExpired shouldBe true
      }

    }

    "updating" should {

      "replace the access_token with a new valid token" in {
        val dateTime = ZonedDateTime.now()
        accessTokenRepository.saveToken(token = "some_valid_token", expiry = dateTime)

        val expiryDate = dateTime.plusMinutes(5)

        accessTokenRepository.saveToken(token = "some_new_valid_token", expiry = expiryDate)
        accessTokenRepository.token.get       shouldBe "some_new_valid_token"
        accessTokenRepository.token.isSuccess shouldBe true
      }

      "return a new expiry time 4 minutes from now when actual expiry is 5 minutes from now." in {
        val mockTimeProvider      = mock[TimeProvider]
        val accessTokenRepository = new AccessTokenRepository(mockTimeProvider)

        val dateTime = ZonedDateTime.now()
        when(mockTimeProvider.now) thenReturn dateTime

        val seconds = 300 // expire in 5 minutes.
        val expiry  = accessTokenRepository.newExpiry(seconds)
        // new expiry should be less than 60 sec.
        SECONDS.between(dateTime, expiry) shouldBe 240
      }

    }

    "valid" should {

      "have an access_token" in {
        accessTokenRepository.saveToken(token = "some_valid_token", expiry = ZonedDateTime.now().plusSeconds(1))
        accessTokenRepository.token.get shouldBe "some_valid_token"
      }

      "return success with token when access token with expiry time" in {
        val mockTimeProvider      = mock[TimeProvider]
        val accessTokenRepository = new AccessTokenRepository(mockTimeProvider)

        val dateTime = ZonedDateTime.now()
        when(mockTimeProvider.now) thenReturn dateTime.plusMinutes(4)

        val expiryDate = dateTime.plusMinutes(5)
        accessTokenRepository.saveToken(token = "some_valid_token", expiry = expiryDate)

        accessTokenRepository.token.get       shouldBe "some_valid_token"
        accessTokenRepository.token.isSuccess shouldBe true
      }

    }

    "expired" should {
      "return failure for expiry access token" in {
        val mockTimeProvider      = mock[TimeProvider]
        val accessTokenRepository = new AccessTokenRepository(mockTimeProvider)
        val dateTime              = ZonedDateTime.now()

        when(mockTimeProvider.now) thenReturn dateTime.plusSeconds(301)

        val expiryDate = dateTime.plusMinutes(5)
        accessTokenRepository.saveToken(token = "some_valid_token", expiry = expiryDate)
        accessTokenRepository.token.isFailure shouldBe true
      }

      "return failure for expiry access token when set using new expiry" in {
        val mockTimeProvider      = mock[TimeProvider]
        val accessTokenRepository = new AccessTokenRepository(mockTimeProvider)
        val dateTime              = ZonedDateTime.now()

        when(mockTimeProvider.now) thenReturn dateTime // return ZonedDateTime.now() for call in newExpiry method
        val expiry = accessTokenRepository.newExpiry(300)

        // return ZonedDateTime.now() + 242 seconds for call in saveToken method
        when(mockTimeProvider.now) thenReturn dateTime.plusSeconds(242)
        accessTokenRepository.saveToken(token = "some_valid_token", expiry)

        accessTokenRepository.token.isFailure shouldBe true
      }

    }
  }

}
