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

package uk.gov.hmrc.brm.config

import org.scalatest.mock.MockitoSugar
import play.api.LoggerLike
import play.api.test.FakeApplication
import play.api.test.Helpers._
import uk.gov.hmrc.play.audit.http.connector.LoggerProvider
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.test.UnitSpec
import utils.JsonUtils

import scala.concurrent.Future

/**
  * Created by user on 09/12/16.
  */
class BRMResultHandlerSpec extends UnitSpec with MockitoSugar with BRMResultHandler with LoggerProvider {

  override val logger = mock[LoggerLike]
  implicit val hc = HeaderCarrier()
  private val reference = "500035710"
  private val jsonBody = JsonUtils.getJsonFromFile(s"gro/$reference")

  var mock400HttpResponse = HttpResponse.apply(400, Some(jsonBody))


  val withBlockedValuesAndSwitchOn: Map[String, _] = Map(
    "microservice.services.birth-registration-matching.noAuditWordList" -> Seq("subjects", "name"),
    "microservice.services.birth-registration-matching.features.disableAuditingLogging" -> true

  )

  val withBlockedValuesAndSwitchOff: Map[String, _] = Map(
    "microservice.services.birth-registration-matching.noAuditWordList" -> Seq("subjects", "name"),
    "microservice.services.birth-registration-matching.features.disableAuditingLogging" -> false

  )

  val noBlockedWord: Map[String, _] = Map(
    "microservice.services.birth-registration-matching.noAuditWordList" -> Seq("")
  )

  "BRMResultHandler" should {
     "not log body if body contains blocked words, audit logging is disabled and audit response throws exception " in {
       running(FakeApplication(additionalConfiguration = withBlockedValuesAndSwitchOn)) {
         var blockedWords = Seq("subjects", "givenname")
         try {
           await(handleResult(Future.failed(new Exception), jsonBody))

         } catch {
           case e: Exception =>

             for (blockedWord <- blockedWords) {
               e.getMessage should not contain blockedWord
             }
         }
       }
     }

    "log body if audit logging is enabled and audit response throws exception " in {
      running(FakeApplication(additionalConfiguration = withBlockedValuesAndSwitchOff)) {
        try {
          await(handleResult(Future.failed(new Exception), jsonBody))
        } catch {
          case e: Exception =>

            e.getMessage.contains(jsonBody.toString) shouldBe true
        }
      }
    }

     "log the body if body does not contain blocked words " in {

       running(FakeApplication(additionalConfiguration = noBlockedWord)) {
         try {

           await(handleResult(Future.failed(new Exception), jsonBody))
         } catch {
           case e: Exception => {
             e.getMessage.contains(jsonBody.toString) shouldBe true
           }

         }
       }
     }

    "not log body if body contains blocked words when audit reponse has status code more than 300" in {
      running(FakeApplication(additionalConfiguration = withBlockedValuesAndSwitchOn)) {
        var blockedWords = Seq("subjects", "givenname")
        try {
          var httpResponse = await(handleResult(Future.successful(mock400HttpResponse), jsonBody))

        } catch {
          case e: Exception =>

            for (blockedWord <- blockedWords) {
              e.getMessage should not contain blockedWord
              e.getMessage.contains("body removed, contains sensitive data") shouldBe true
            }
        }
      }
    }

    "log body if audit logging is enabled and audit reponse has status code more than 300" in {
      running(FakeApplication(additionalConfiguration = withBlockedValuesAndSwitchOff)) {

        try {

          var httpResponse = await(handleResult(Future.successful(mock400HttpResponse), jsonBody))

        } catch {
          case e: Exception => {
            e.getMessage.contains(jsonBody.toString) shouldBe true
            e.getMessage.contains("body removed, contains sensitive data") shouldBe false
          }
        }
      }
    }

    "log body if body does not contain blocked words when audit reponse has status code more than 300" in {
      running(FakeApplication(additionalConfiguration = noBlockedWord)) {

        try {

          var httpResponse = await(handleResult(Future.successful(mock400HttpResponse), jsonBody))

        } catch {
          case e: Exception => {
            e.getMessage.contains(jsonBody.toString) shouldBe true
            e.getMessage.contains("body removed, contains sensitive data") shouldBe false
          }
        }
      }
    }

  }
}
