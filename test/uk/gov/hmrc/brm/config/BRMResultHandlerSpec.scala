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
import uk.gov.hmrc.brm.BRMFakeApplication
import uk.gov.hmrc.play.audit.http.connector.LoggerProvider
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import utils.JsonUtils
import org.mockito.Mockito._
import org.specs2.mock.mockito.ArgumentCapture

import scala.concurrent.Future

/**
  * Created by user on 09/12/16.
  */
class BRMResultHandlerSpec extends UnitSpec with MockitoSugar with BRMResultHandler with LoggerProvider {

  override val logger = mock[LoggerLike]
  implicit val hc = HeaderCarrier()
  private val reference = "500035710"
  private val jsonBody = JsonUtils.getJsonFromFile(s"gro/$reference")

  val withBlockedValues: Map[String, _] = Map(
    "microservice.services.birth-registration-matching.no-audit-word-list" -> Seq("subjects", "name")
  )

  val noBlockedWord: Map[String, _] = Map(
    "microservice.services.birth-registration-matching.no-audit-word-list" -> Seq()
  )

  "BRMResultHandler" should {
    import scala.concurrent.ExecutionContext.Implicits.global
    "not log body if body contains blocked words " in {
      running(FakeApplication(additionalConfiguration = withBlockedValues)) {
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
  }
}
