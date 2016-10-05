package utils

import org.joda.time.{DateTimeUtils, DateTime}
import uk.gov.hmrc.brm.utils.AccessTokenRepository
import uk.gov.hmrc.play.test.UnitSpec

import scala.util.{Failure, Success}

/**
 * Created by adamconder on 05/10/2016.
 */
class AccessTokenRepositorySpec extends UnitSpec {

  "AccessTokenRepository" when {

    "hasToken" should {

      "return true for a token has been set" in {
        val dateTime = DateTime.now()
        AccessTokenRepository.apply(token = "some_valid_token", expiry = dateTime)
        AccessTokenRepository.hasToken shouldBe true
        AccessTokenRepository.token shouldBe Success(_)
      }

    }

    "updating" should {

      "replace the access_token with a new valid token" in {
        val dateTime = DateTime.now()
        AccessTokenRepository.apply(token = "some_valid_token", expiry = dateTime)

        val expiryDate = dateTime.plusMinutes(5)

        AccessTokenRepository.apply(token = "some_new_valid_token", expiry = expiryDate)
        AccessTokenRepository.token shouldBe "some_new_valid_token"
        AccessTokenRepository.token shouldBe Success(_)
      }

    }

    "valid" should {

      "have an access_token" in {
        val dateTime = DateTime.now()
        AccessTokenRepository.apply(token = "some_valid_token", expiry = dateTime)
        AccessTokenRepository.token shouldBe "some_valid_token"
      }

      "return success with token when access token with expiry time" in {
        val dateTime = DateTime.now()
        val expiryDate = dateTime.plusMinutes(5)
        AccessTokenRepository.apply(token = "some_valid_token", expiry = expiryDate)
        DateTimeUtils.setCurrentMillisFixed(dateTime.plusMinutes(4).getMillis)
        AccessTokenRepository.token shouldBe Success(_)
        DateTimeUtils.setCurrentMillisSystem()
      }

    }

    "expired" should {
      "return failure for expiry access token" in {
        val dateTime = DateTime.now()
        val expiryDate = dateTime.plusMinutes(5)
        AccessTokenRepository.apply(token = "some_valid_token", expiry = expiryDate)
        DateTimeUtils.setCurrentMillisFixed(dateTime.plusMinutes(6).getMillis)
        AccessTokenRepository.token shouldBe Failure(_)
        DateTimeUtils.setCurrentMillisSystem()
      }
    }
  }

}
