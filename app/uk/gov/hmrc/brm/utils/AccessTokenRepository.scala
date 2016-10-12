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

package uk.gov.hmrc.brm.utils

import org.joda.time.{Seconds, DateTime}

import scala.util.{Failure, Success, Try}
import uk.gov.hmrc.brm.utils.BrmLogger._

/**
 * Created by adamconder on 05/10/2016.
 */
class AccessTokenRepository {

  private var _token : Option[String] = None
  private var _expiry : Option[DateTime] = None

  private val CLASS_NAME = this.getClass.getCanonicalName

  private val expiredTokenException  = new RuntimeException(s"access_token expired")

  def saveToken(token: String, expiry : DateTime) = {
    debug(CLASS_NAME, "saveToken", s"access_token: $token, expiry: $expiry")
    info(CLASS_NAME, "saveToken", s"saving new access_token, expiry: $expiry")
    _token = Some(token)
    _expiry = Some(expiry)
  }

  def newExpiry(seconds : Int) = {
    info(CLASS_NAME, "newExpiry", s"access_token new expiry time in seconds: $seconds")
    DateTime.now.plusSeconds(seconds)
  }

  def hasExpired : Boolean = {
    def currentTime = DateTime.now().minusSeconds(30)
    val expired = _expiry.fold(true)(t => t.isBefore(currentTime))
    info(CLASS_NAME, "access_token hasExpired", s"access_token has expired $expired")
    expired
  }

  def hasToken = _token.fold(false)(x => x.trim.nonEmpty)

  def token : Try[String] = {
    if (hasToken && !hasExpired) {
      def seconds = Seconds.secondsBetween(DateTime.now, _expiry.get).getSeconds
      info(CLASS_NAME, "token", s"access_token expires in: $seconds seconds")
      Success(_token.get)
    } else {
      error(CLASS_NAME, "token", "access_token has expired")
      Failure(expiredTokenException)
    }
  }

}
