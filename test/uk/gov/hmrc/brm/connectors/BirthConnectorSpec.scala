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

package uk.gov.hmrc.brm.connectors

import org.mockito.Matchers
import org.scalatest.{Suite, BeforeAndAfter}
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import play.api.libs.json.{JsObject, JsArray, JsNull}
import play.api.test.FakeApplication
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.test.{WithFakeApplication, UnitSpec}
import utils.JsonUtils
import play.api.test.Helpers._

import scala.concurrent.Future

/**
 * Created by adamconder on 01/08/2016.
 */
class BirthConnectorSpec extends UnitSpec with WithFakeApplication with MockitoSugar with BeforeAndAfter {

  implicit val hc = HeaderCarrier()

  val mockHttpGet = mock[HttpGet]
  val mockHttpPost = mock[HttpPost]

  object MockBirthConnector extends BirthConnector {
    override val httpGet = mockHttpGet
    override val httpPost = mockHttpPost
  }

  def groResponse(reference: String) = JsonUtils.getJsonFromFile(s"gro/$reference")
  val authRecord = JsonUtils.getJsonFromFile("gro/auth")

  val config : Map[String, _] = Map(

  )

  object BRMFakeApplication {

    def fakeApplication(config : Map[String, _]) = {
      FakeApplication(additionalConfiguration = config)
    }

  }

  before {
    reset(mockHttpGet)
    reset(mockHttpPost)
  }

  "BirthConnector" when {

    "invalid configuration" should {

//      "throw RuntimeException" in {
//        running(BRMFakeApplication.fakeApplication(config)) {
//          intercept[RuntimeException] {
//            when(mockHttpPost.POSTForm[HttpResponse](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
//              .thenReturn(Future.successful(HttpResponse(200, Some(authRecord))))
//            when(mockHttpGet.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any()))
//              .thenReturn(Future.successful(HttpResponse(200, Some(groResponse("500035710")))))
//            val result = await(MockBirthConnector.getReference("500035710"))
//            result should not be JsNull
//          }
//        }
//      }

    }

    "getReference" should {

      "200 with json response with match" in {
        when(mockHttpPost.POSTForm[HttpResponse](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(200, Some(authRecord))))
        when(mockHttpGet.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(200, Some(groResponse("500035710")))))
        val result = await(MockBirthConnector.getReference("500035710"))
        result should not be JsNull
      }

      "200 with json response with no match" in {
        when(mockHttpPost.POSTForm[HttpResponse](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(200, Some(authRecord))))
        when(mockHttpGet.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(200, Some(groResponse("NoMatch")))))
        val result = await(MockBirthConnector.getReference("500037654675710"))
        result should not be JsNull
        result shouldBe a[JsArray]
      }

      "400 with BadRequest for authentication" in {
        when(mockHttpPost.POSTForm[HttpResponse](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(400, None)))
        intercept[Upstream4xxResponse] {
          val result = await(MockBirthConnector.getReference("500035710"))
          result should not be JsNull
          result shouldBe a[JsArray]
        }
      }

      "500 with InternalServerError for authentication" in {
        when(mockHttpPost.POSTForm[HttpResponse](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(500, None)))
        intercept[Upstream5xxResponse] {
          val result = await(MockBirthConnector.getReference("500035710"))
          result should not be JsNull
          result shouldBe a[JsArray]
        }
      }

      "400 with BadRequest for reference" in {
        when(mockHttpPost.POSTForm[HttpResponse](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(200, Some(authRecord))))
        when(mockHttpGet.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(400, None)))
        intercept[Upstream4xxResponse] {
          val result = await(MockBirthConnector.getReference("500035710"))
          result should not be JsNull
          result shouldBe a[JsArray]
        }
      }

      "500 with InternalServerError for reference" in {
        when(mockHttpPost.POSTForm[HttpResponse](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(200, Some(authRecord))))
        when(mockHttpGet.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(500, None)))
        intercept[Upstream5xxResponse] {
          val result = await(MockBirthConnector.getReference("500035710"))
          result should not be JsNull
          result shouldBe a[JsArray]
        }
      }

    }

    "getChildDetails" should {

      "200 with json response with match" in {
        val payload = Map(
          "firstName" -> "Adam",
          "lastName" -> "Wilson",
          "dateOfBirth" -> "2006-11-12"
        )
        when(mockHttpPost.POSTForm[HttpResponse](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(200, Some(authRecord))))
        when(mockHttpGet.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(200, Some(groResponse("wilson")))))
        val result = await(MockBirthConnector.getChildDetails(payload))
        result shouldBe a[JsArray]
        result shouldBe groResponse("wilson")
      }

      "200 with json response with no match" in {
        val payload = Map(
          "firstName" -> "Adam1",
          "lastName" -> "Wilson1",
          "dateOfBirth" -> "2006-11-12"
        )
        when(mockHttpPost.POSTForm[HttpResponse](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(200, Some(authRecord))))
        when(mockHttpGet.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(200, Some(groResponse("NoMatch")))))
        val result = await(MockBirthConnector.getChildDetails(payload))
        result shouldBe a[JsArray]
        result.as[List[JsObject]].length shouldBe 0
        result shouldBe groResponse("NoMatch")
      }

      "400 with BadRequest for authentication" in {
        val payload = Map(
          "firstName" -> "Adam",
          "lastName" -> "Wilson",
          "dateOfBirth" -> "2006-11-12"
        )
        when(mockHttpPost.POSTForm[HttpResponse](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(400, None)))
        intercept[Upstream4xxResponse] {
          val result = await(MockBirthConnector.getChildDetails(payload))
          result should not be JsNull
          result shouldBe a[JsArray]
        }
      }

      "500 with InternalServerError for authentication" in {
        val payload = Map(
          "firstName" -> "Adam",
          "lastName" -> "Wilson",
          "dateOfBirth" -> "2006-11-12"
        )
        when(mockHttpPost.POSTForm[HttpResponse](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(500, None)))
        intercept[Upstream5xxResponse] {
          val result = await(MockBirthConnector.getChildDetails(payload))
          result should not be JsNull
          result shouldBe a[JsArray]
        }
      }

      "400 with BadRequest for child details" in {
        val payload = Map(
          "firstName" -> "Adam",
          "lastName" -> "Wilson",
          "dateOfBirth" -> "2006-11-12"
        )
        when(mockHttpPost.POSTForm[HttpResponse](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(200, Some(authRecord))))
        when(mockHttpGet.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(400, None)))
        intercept[Upstream4xxResponse] {
          val result = await(MockBirthConnector.getChildDetails(payload))
          result should not be JsNull
          result shouldBe a[JsArray]
        }
      }

      "500 with InternalServerError for child details" in {
        val payload = Map(
          "firstName" -> "Adam",
          "lastName" -> "Wilson",
          "dateOfBirth" -> "2006-11-12"
        )
        when(mockHttpPost.POSTForm[HttpResponse](Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(200, Some(authRecord))))
        when(mockHttpGet.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(500, None)))
        intercept[Upstream5xxResponse] {
          val result = await(MockBirthConnector.getChildDetails(payload))
          result should not be JsNull
          result shouldBe a[JsArray]
        }
      }
    }
  }

}
