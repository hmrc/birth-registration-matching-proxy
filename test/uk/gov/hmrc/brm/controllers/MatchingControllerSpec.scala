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

package uk.gov.hmrc.brm.controllers

import org.mockito.Matchers
import org.mockito.Matchers.{eq => mockEq}
import org.mockito.Mockito._
import org.scalatest.{OneInstancePerTest, BeforeAndAfter}
import org.scalatest.mock.MockitoSugar
import play.api.Logger
import play.api.libs.json._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.brm.connectors.BirthConnector
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import utils.JsonUtils

import scala.concurrent.Future

/**
 * Created by adamconder on 27/07/2016.
 */
class MatchingControllerSpec extends UnitSpec
  with WithFakeApplication
  with MockitoSugar
  with BeforeAndAfter
  with OneInstancePerTest {

  val reference = "500035710"
  val invalidReference = "812739812739183"

  def groResponseForReference(reference: String) = JsonUtils.getJsonFromFile(s"gro/$reference")
  def groResponseForName(surname: String) = JsonUtils.getJsonFromFile(s"gro/$surname")
  val groJsonNoRecord = JsonUtils.getJsonFromFile("gro/NoMatch")

  def referenceRequest(ref : String) = FakeRequest("GET", s"/birth-registration-matching-proxy/match/$ref")
  def params(firstName : String, lastName : String, dateOfBirth: String) = Map(s"firstName" -> firstName, "lastName" -> lastName, "dateOfBirth" -> dateOfBirth)
  def detailsRequest(firstName : String, lastName : String, dateOfBirth : String) = FakeRequest("GET", s"/birth-registration-matching-proxy/match?firstName=$firstName&lastName=$lastName&dateOfBirth=$dateOfBirth")

  val validDetailsRequest = detailsRequest("Adam", "Wilson", "2010-08-27")
  val noMatchDetailsRequest = detailsRequest("Adam", "Conder", "2010-08-27")
  val invalidDetailsRequest = detailsRequest("", "", "")

  private val mockConnector = mock[BirthConnector]

  object MockController extends MatchingController {
    override val groConnector = mockConnector
  }

  before {
    reset(mockConnector)
  }

  "MatchingController" when {

      "initialising" should {

        "wire up dependencies correctly" in {
          MatchingController.groConnector shouldBe a[BirthConnector]
        }

      }

      "GET /birth-registration-matching-proxy/match/:ref" should {

        "not return NOT_FOUND endpoint" in {
          val result = route(referenceRequest(reference))
          result.isDefined shouldBe true
          status(result.get) should not be NOT_FOUND
        }

        "return 200 for a reference than exists in GRO" in {
          val json = groResponseForReference(reference)
          when(MockController.groConnector.getReference(mockEq(reference))(Matchers.any())).thenReturn(Future.successful(json))
          val request = referenceRequest(reference)
          val result = await(MockController.reference(reference).apply(request))
          status(result) shouldBe OK
          contentType(result).get shouldBe "application/json"
          jsonBodyOf(result).as[JsObject] shouldBe json.as[JsObject]
        }

        "return 200 for a reference that does not exist in GRO" in {
          when(MockController.groConnector.getReference(mockEq(invalidReference))(Matchers.any())).thenReturn(Future.successful(groJsonNoRecord))
          val request = referenceRequest(invalidReference)
          val result = await(MockController.reference(invalidReference).apply(request))
          status(result) shouldBe OK
          contentType(result).get shouldBe "application/json"
          jsonBodyOf(result).as[JsArray] shouldBe groJsonNoRecord.as[JsArray]
        }

        "return InternalServerError when GRO is down" in {
          when(MockController.groConnector.getReference(mockEq(reference))(Matchers.any())).thenReturn(Future.failed(new Upstream5xxResponse("", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)))
          val request = referenceRequest(reference)
          val result = await(MockController.reference(reference).apply(request))
          status(result) shouldBe INTERNAL_SERVER_ERROR
          contentType(result).get shouldBe "application/json"
        }

        "return BadRequest when invalid reference number is provided" in {
          when(MockController.groConnector.getReference(mockEq("ass1212sqw"))(Matchers.any())).thenReturn(Future.failed(new Upstream4xxResponse("", BAD_REQUEST, BAD_REQUEST)))
          val request = referenceRequest("ass1212sqw")
          val result = await(MockController.reference("ass1212sqw").apply(request))
          status(result) shouldBe BAD_REQUEST
          contentType(result).get shouldBe "application/json"
        }

      }

      "GET /birth-registration-matching-proxy/match" should {

        "not return NOT_FOUND endpoint" in {
          val result = route(validDetailsRequest)
          result.isDefined shouldBe true
          status(result.get) should not be NOT_FOUND
        }

        "return 200 for details than exists in GRO" in {
            val json = groResponseForName("wilson")
            when(MockController.groConnector.getChildDetails(mockEq(params("Adam", "Wilson", "2010-08-27")))(Matchers.any())).thenReturn(Future.successful(json))
            val request = validDetailsRequest
            val result = await(MockController.details.apply(request))
            status(result) shouldBe OK
            contentType(result).get shouldBe "application/json"
            jsonBodyOf(result).as[JsArray] shouldBe json.as[JsArray]
        }

        "return 200 for details that do not exists in GRO" in {
          when(MockController.groConnector.getChildDetails(mockEq(params("Adam", "Conder", "2010-08-27")))(Matchers.any())).thenReturn(Future.successful(groJsonNoRecord))
          val request = noMatchDetailsRequest
          val result = await(MockController.details.apply(request))
          status(result) shouldBe OK
          contentType(result).get shouldBe "application/json"
          jsonBodyOf(result) shouldBe groJsonNoRecord
        }

        "return InternalServerError when GRO is down" in {
          when(MockController.groConnector.getChildDetails(mockEq(params("Adam", "Wilson", "2010-08-27")))(Matchers.any())).thenReturn(Future.failed(new Upstream5xxResponse("", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)))
          val request = validDetailsRequest
          val result = await(MockController.details.apply(request))
          status(result) shouldBe INTERNAL_SERVER_ERROR
          contentType(result).get shouldBe "application/json"
        }

        "return BadRequest when invalid details are provided" in {
          when(MockController.groConnector.getChildDetails(mockEq(params("", "", "")))(Matchers.any())).thenReturn(Future.failed(new Upstream4xxResponse("", BAD_REQUEST, BAD_REQUEST)))
          val request = invalidDetailsRequest
          val result = await(MockController.details.apply(request))
          status(result) shouldBe BAD_REQUEST
          contentType(result).get shouldBe "application/json"
        }

      }

    }

}
