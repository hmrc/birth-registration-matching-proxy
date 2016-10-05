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

import org.joda.time.DateTime

import scala.util.{Failure, Success, Try}

/**
 * Created by adamconder on 05/10/2016.
 */
object AccessTokenRepository {

  private val expiredTokenException  = new RuntimeException("access_token expired")

  private var _token : Option[String] = None
  private var _expiry : Option[DateTime] = None

  def apply(token: String, expiry : DateTime) = {
    _token = Some(token)
    _expiry = Some(expiry)
  }

  def newExpiry(seconds : Int) = {
    DateTime.now.plusSeconds(seconds)
  }

  private def hasExpired : Boolean = {
    val currentTime = DateTime.now().minusSeconds(30)
    _expiry.fold(true)(t => t.isBefore(currentTime))
  }

  def hasToken = _token.fold(false)(x => x.trim.nonEmpty)

  def token : Try[String] = {
    if (hasToken && !hasExpired) Success(_token.get)
    else Failure(expiredTokenException)
  }

}
