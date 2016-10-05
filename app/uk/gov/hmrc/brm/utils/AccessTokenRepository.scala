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

  private def hasExpired : Boolean = {
    val currentTime = DateTime.now()
    currentTime.isBeforeNow
  }

  def hasToken = _token.fold(false)(x => x.trim.nonEmpty)

  def token : Try[String] = {
    if (hasToken && !hasExpired) Success(_token.get)
    else Failure(expiredTokenException)
  }

}
