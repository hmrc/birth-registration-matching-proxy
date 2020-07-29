/*
 * Copyright 2020 HM Revenue & Customs
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

import org.joda.time.{DateTime, Seconds}
import uk.gov.hmrc.brm.connectors.ConnectorTypes.AccessToken
import uk.gov.hmrc.brm.utils.BrmLogger._

import scala.util.{Failure, Success, Try}

class AccessTokenRepository {

  private var _token : Option[AccessToken] = None
  private var _expiry : Option[DateTime] = None
  private val _expireSecondsDiff = 60 //1 min
  private val CLASS_NAME = this.getClass.getSimpleName

  private val expiredTokenException  = new RuntimeException(s"access_token expired")

  def saveToken(token: AccessToken, expiry : DateTime) = {
    debug(CLASS_NAME, "saveToken", s"access_token: $token, expiry: $expiry")
    info(CLASS_NAME, "saveToken", s"saving new access_token, expiry: $expiry")
    _token = Some(token)
    _expiry = Some(expiry)
  }

  def newExpiry(seconds : Int) = {
    info(CLASS_NAME, "newExpiry", s"access_token new expiry time in seconds: $seconds")
    //making expiry time less by one minute.
    DateTime.now.plusSeconds(seconds).minusSeconds(_expireSecondsDiff)
  }

  def hasExpired : Boolean = {
    def currentTime = DateTime.now()
    val expired = _expiry.fold(true)(t => t.isBefore(currentTime))
    info(CLASS_NAME, "access_token hasExpired", s"access_token has expired $expired")
    expired
  }

  def hasToken = _token.fold(false)(x => x.trim.nonEmpty)

  def token : Try[AccessToken] = {
    if (hasToken && !hasExpired) {
      def seconds = Seconds.secondsBetween(DateTime.now, _expiry.get).getSeconds
      info(CLASS_NAME, "token", s"access_token expires in: $seconds seconds")
      Success(_token.get)
    } else {
      info(CLASS_NAME, "token", "access_token has expired")
      Failure(expiredTokenException)
    }
  }

}
