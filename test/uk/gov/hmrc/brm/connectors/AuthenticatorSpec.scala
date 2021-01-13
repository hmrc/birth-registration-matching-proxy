/*
 * Copyright 2021 HM Revenue & Customs
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
import uk.gov.hmrc.brm.TestFixture
import uk.gov.hmrc.brm.tls.HttpClientFactory
import uk.gov.hmrc.brm.utils.CertificateStatus

import scala.util.Success


class AuthenticatorSpec extends TestFixture {

  val testAuthenticator =
    new Authenticator(testProxyConfig, testGroConfig, mock[HttpClientFactory], mock[ProxyAuthenticator], mock[CertificateStatus])

  "Authenticator" when {

    "creating an instance of cache" should {

      "return false for having a token" in {
        testAuthenticator.tokenCache.hasToken shouldBe false
        testAuthenticator.tokenCache.hasExpired shouldBe true
      }

    }

    "saving a new token" should {

      "insert a token" in {
        testAuthenticator.tokenCache.saveToken("new token", DateTime.now.plusDays(2))
        testAuthenticator.tokenCache.hasToken shouldBe true
        testAuthenticator.tokenCache.hasExpired shouldBe false
        testAuthenticator.tokenCache.token shouldBe Success("new token")
      }

      "generate new expiry" in {
        val dateTime = new DateTime()
        DateTimeUtils.setCurrentMillisFixed(dateTime.getMillis)
        val expiryTime = testAuthenticator.tokenCache.newExpiry(100)
        //expiry time shd be less by 60 sec.
        Seconds.secondsBetween(dateTime, expiryTime).getSeconds shouldBe 40
        DateTimeUtils.setCurrentMillisSystem()
      }

    }

  }

}
