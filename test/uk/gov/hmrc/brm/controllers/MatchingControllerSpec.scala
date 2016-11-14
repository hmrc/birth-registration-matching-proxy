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

import java.net.URLEncoder

import org.mockito.Matchers
import org.mockito.Matchers.{eq => mockEq}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfter, OneInstancePerTest}
import play.api.Logger
import play.api.libs.json._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.brm.connectors._
import uk.gov.hmrc.play.http.{HeaderCarrier, JsValidationException}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import utils.JsonUtils
import utils.ResponseHelper._

import scala.concurrent.Future

class MatchingControllerSpec extends UnitSpec
  with WithFakeApplication
  with MockitoSugar
  with BeforeAndAfter
  with OneInstancePerTest {

  val reference = "500035710"
  val invalidReference = "812739812739183"

  def groResponse(reference: String) = JsonUtils.getJsonFromFile(s"gro/$reference")
  val groJsonNoRecord = JsonUtils.getJsonFromFile("gro/NoMatch")

  def referenceRequest(ref : String) = FakeRequest("GET", s"/birth-registration-matching-proxy/match/$ref")
    .withHeaders(("Content-type", "application/json"))

  def detailsRequest(forenames : String, lastname : String, dateofbirth : String) = {
    val params = Map(
      "forenames" -> forenames,
      "lastname" -> lastname,
      "dateofbirth" -> dateofbirth
    )
    val query = params.map(pair => pair._1 + "=" + URLEncoder.encode(pair._2, "UTF-8")).mkString("&")
    Logger.debug(s"query: /birth-registration-matching-proxy/match?$query")
    FakeRequest("GET", s"/birth-registration-matching-proxy/match?$query")
  }

  val validDetailsRequest = detailsRequest("Adam", "Wilson", "2010-08-27")
  val noMatchDetailsRequest = detailsRequest("Adam", "Conder", "2010-08-27")

  private val mockBirthConnector = mock[BirthConnector]
  implicit val hc = HeaderCarrier()

  def successResponse(json:JsValue) ={
    Future.successful(BirthSuccessResponse(json))
  }

  val jsValidationExceptionResponse: BirthResponse = BirthErrorResponse(
    new JsValidationException("", "", getClass, Seq())
  )

  object MockController extends MatchingController {
    override val groConnector = mockBirthConnector
  }

  before {
    reset(mockBirthConnector)
  }

  /**
   * MatchingController when:
   *
   *  initialising should wire up dependencies correctly
   *  GET should return 200 OK for a reference than exists in GRO
   *  GET should return 404 NOT_FOUND for a reference that does not exist in GRO
   *  GET should return INTERNAL_SERVER_ERROR when GRO returns Upstream5xxResponse InternalServerError
   *  GET should return INTERNAL_SERVER_ERROR when GRO returns Upstream5xxResponse BadGateway
   *  GET should return BAD_GATEWAY when invalid reference number is provided
   *  GET should return INTERNAL_SERVER_ERROR when invalid json is returned
   *  GET should return INTERNAL_SERVER_ERROR when GRO times out
   *  GET should return INTERNAL_SERVER_ERROR when GRO returns Forbidden
   */

  "MatchingController" when {

      "initialising" should {

        "wire up dependencies correctly" in {
          MatchingController.groConnector shouldBe a[BirthConnector]
        }

      }

      // TODO ADD UNIT TEST CASES FOR REFERENCE NUMBER WITH SPECIAL CHARACTERS / UTF-8

      "GET /birth-registration-matching-proxy/match/:ref" should {

        "return 200 for a reference than exists in GRO" in {
          val json = groResponse(reference)

          when(MockController.groConnector.get(mockEq(reference))(Matchers.any())).thenReturn(successResponse(json))

          val request = referenceRequest(reference)
          val result = await(MockController.reference(reference).apply(request))

          status(result) shouldBe OK
          contentType(result).get shouldBe "application/json"
          jsonBodyOf(result).as[JsObject] shouldBe json.as[JsObject]
        }

        "return 404 for a reference that does not exist in GRO" in {
          when(MockController.groConnector.get(mockEq(invalidReference))(Matchers.any())).
            thenReturn(notFoundResponse)

          val request = referenceRequest(invalidReference)
          val result = await(MockController.reference(invalidReference).apply(request))

          status(result) shouldBe NOT_FOUND
          contentType(result).get shouldBe "application/json"
          bodyOf(result) shouldBe ErrorResponses.NOT_FOUND
        }

        "return InternalServerError when GRO returns Upstream5xxResponse InternalServerError" in {
          when(MockController.groConnector.get(mockEq(reference))(Matchers.any())).thenReturn(internalServerErrorResponse)

          val request = referenceRequest(reference)
          val result = await(MockController.reference(reference).apply(request))

          status(result) shouldBe INTERNAL_SERVER_ERROR
          contentType(result).get shouldBe "application/json"
          bodyOf(result) shouldBe ErrorResponses.CONNECTION_DOWN
        }

        "return InternalServerError when GRO returns Upstream5xxResponse BadGateway" in {
          when(MockController.groConnector.get(mockEq(reference))(Matchers.any())).thenReturn(badGatewayResponse)

          val request = referenceRequest(reference)
          val result = await(MockController.reference(reference).apply(request))

          status(result) shouldBe BAD_GATEWAY
          contentType(result).get shouldBe "application/json"
          bodyOf(result) shouldBe ErrorResponses.BAD_REQUEST
        }

        "return BadGateway when invalid reference number is provided" in {
          when(MockController.groConnector.get(mockEq("ass1212sqw"))(Matchers.any())).thenReturn(badRequestResponse)
          val request = referenceRequest("ass1212sqw")
          val result = await(MockController.reference("ass1212sqw").apply(request))
          status(result) shouldBe BAD_GATEWAY
          contentType(result).get shouldBe "application/json"
          bodyOf(result) shouldBe ErrorResponses.BAD_REQUEST
        }

        "return InternalServerError when invalid json is returned" in {
          when(MockController.groConnector.get(mockEq("ass1212sqw"))(Matchers.any())).thenReturn(jsValidationExceptionResponse)
          val request = referenceRequest("ass1212sqw")
          val result = await(MockController.reference("ass1212sqw").apply(request))
          status(result) shouldBe INTERNAL_SERVER_ERROR
          contentType(result).get shouldBe "application/json"
        }

        "return InternalServerError when GRO times out" in {
          when(MockController.groConnector.get(mockEq("ass1212sqw"))(Matchers.any())).thenReturn(gatewayTimeoutResponse)
          val request = referenceRequest("ass1212sqw")
          val result = await(MockController.reference("ass1212sqw").apply(request))
          status(result) shouldBe GATEWAY_TIMEOUT
          contentType(result).get shouldBe "application/json"
          bodyOf(result) shouldBe ErrorResponses.GATEWAY_TIMEOUT
        }

        "return InternalServerError when GRO returns Forbidden" in {
          when(MockController.groConnector.get(mockEq("ass1212sqw"))(Matchers.any())).thenReturn(forbiddenResponse)
          val request = referenceRequest("ass1212sqw")
          val result = await(MockController.reference("ass1212sqw").apply(request))
          status(result) shouldBe INTERNAL_SERVER_ERROR
          contentType(result).get shouldBe "application/json"
       }

      }


      "GET /birth-registration-matching-proxy/match" should {

        "return 200 with records for details that match" in {
          val forenames = "adam"
          val lastname = "smith"
          val dateofbirth = "2006-11-12"

          val json = groResponse("2006-11-12_smith_adam")
          when(MockController.groConnector.get(mockEq(forenames), mockEq(lastname), mockEq(dateofbirth))(Matchers.any())).thenReturn(successResponse(json))

          val request = detailsRequest(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth)
          val result = await(MockController.details(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth).apply(request))
          status(result) shouldBe OK
          contentType(result).get shouldBe "application/json"
          jsonBodyOf(result) shouldBe json
        }

        "return 200 with records for details that match UTF-8" in {
          val forenames = "Adàm TËST"
          val lastname = "SMÏTH"
          val dateofbirth = "2006-11-12"

          val json = groResponse("2006-11-12_smith_adam-utf-8")
          when(MockController.groConnector.get(mockEq(forenames), mockEq(lastname), mockEq(dateofbirth))(Matchers.any())).thenReturn(successResponse(json))

          val request = detailsRequest(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth)
          val result = await(MockController.details(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth).apply(request))
          status(result) shouldBe OK
          contentType(result).get shouldBe "application/json"
          jsonBodyOf(result) shouldBe json
        }

        "return 200 [] for details that does not match" in {
          val forenames = "adam"
          val lastname = "conder"
          val dateofbirth = "2016-10-10"

          val json = groResponse("NoMatch")

          when(MockController.groConnector.get(mockEq(forenames), mockEq(lastname), mockEq(dateofbirth))(Matchers.any())).
            thenReturn(successResponse(json))

          val request = detailsRequest(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth)
          val result = await(MockController.details(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth).apply(request))
          status(result) shouldBe OK
          contentType(result).get shouldBe "application/json"
          jsonBodyOf(result) shouldBe groResponse("NoMatch")
        }

        "return InternalServerError when GRO returns Upstream5xxResponse InternalServerError" in {
          val forenames = "adam"
          val lastname = "conder"
          val dateofbirth = "2016-10-10"

          when(MockController.groConnector.get(mockEq(forenames), mockEq(lastname), mockEq(dateofbirth))(Matchers.any())).thenReturn(internalServerErrorResponse)
          val request = detailsRequest(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth)
          val result = await(MockController.details(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth).apply(request))
          status(result) shouldBe INTERNAL_SERVER_ERROR
          contentType(result).get shouldBe "application/json"
          bodyOf(result) shouldBe ErrorResponses.CONNECTION_DOWN
        }

        "return BadGateway when GRO returns Upstream5xxResponse BadGateway" in {
          val forenames = "adam"
          val lastname = "conder"
          val dateofbirth = "2016-10-10"

          when(MockController.groConnector.get(mockEq(forenames), mockEq(lastname), mockEq(dateofbirth))(Matchers.any())).thenReturn(badGatewayResponse)
          val request = detailsRequest(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth)
          val result = await(MockController.details(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth).apply(request))
          status(result) shouldBe BAD_GATEWAY
          contentType(result).get shouldBe "application/json"
          bodyOf(result) shouldBe ErrorResponses.BAD_REQUEST
        }

        "return BadGateway when forenames is not provided" in {
          val forenames = ""
          val lastname = "conder"
          val dateofbirth = "2016-10-10"

          when(MockController.groConnector.get(mockEq(forenames), mockEq(lastname), mockEq(dateofbirth))(Matchers.any())).thenReturn(badRequestResponse)
          val request = detailsRequest(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth)
          val result = await(MockController.details(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth).apply(request))
          status(result) shouldBe BAD_GATEWAY
          contentType(result).get shouldBe "application/json"
          bodyOf(result) shouldBe ErrorResponses.BAD_REQUEST
        }

        "return BadGateway when lastname is not provided" in {
          val forenames = "adam"
          val lastname = ""
          val dateofbirth = "2016-10-10"

          when(MockController.groConnector.get(mockEq(forenames), mockEq(lastname), mockEq(dateofbirth))(Matchers.any())).thenReturn(badRequestResponse)
          val request = detailsRequest(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth)
          val result = await(MockController.details(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth).apply(request))
          status(result) shouldBe BAD_GATEWAY
          contentType(result).get shouldBe "application/json"
          bodyOf(result) shouldBe ErrorResponses.BAD_REQUEST
        }

        "return BadGateway when dateofbirth is not provided" in {
          val forenames = "adam"
          val lastname = "conder"
          val dateofbirth = ""

          when(MockController.groConnector.get(mockEq(forenames), mockEq(lastname), mockEq(dateofbirth))(Matchers.any())).thenReturn(badRequestResponse)
          val request = detailsRequest(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth)
          val result = await(MockController.details(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth).apply(request))
          status(result) shouldBe BAD_GATEWAY
          contentType(result).get shouldBe "application/json"
          bodyOf(result) shouldBe ErrorResponses.BAD_REQUEST
        }

        /**
         * NOTE: This should be a 502 BadGateway returned to signify the data is incorrect
         * GRO comment:
         * Currently the response status is 500 Internal Server Error; this is not the intention and will be fixed in a future release to show the proper 400 Bad Request instead.
         */
        "return InternalServerError when dateofbirth is invalid format" in {
          val forenames = "adam"
          val lastname = "conder"
          val dateofbirth = "10-10-2016"

          when(MockController.groConnector.get(mockEq(forenames), mockEq(lastname), mockEq(dateofbirth))(Matchers.any())).thenReturn(internalServerErrorResponse)
          val request = detailsRequest(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth)
          val result = await(MockController.details(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth).apply(request))
          status(result) shouldBe INTERNAL_SERVER_ERROR
          contentType(result).get shouldBe "application/json"
          bodyOf(result) shouldBe ErrorResponses.CONNECTION_DOWN
        }

        "return InternalServerError when invalid json is returned" in {
          val forenames = "adam"
          val lastname = "conder"
          val dateofbirth = "2016-10-10"

          when(MockController.groConnector.get(mockEq(forenames), mockEq(lastname), mockEq(dateofbirth))(Matchers.any())).thenReturn(jsValidationExceptionResponse)
          val request = detailsRequest(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth)
          val result = await(MockController.details(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth).apply(request))
          status(result) shouldBe INTERNAL_SERVER_ERROR
          contentType(result).get shouldBe "application/json"
          bodyOf(result) shouldBe empty
        }

        "return InternalServerError when GRO times out" in {
          val forenames = "adam"
          val lastname = "conder"
          val dateofbirth = "2016-10-10"

          when(MockController.groConnector.get(mockEq(forenames), mockEq(lastname), mockEq(dateofbirth))(Matchers.any())).thenReturn(gatewayTimeoutResponse)
          val request = detailsRequest(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth)
          val result = await(MockController.details(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth).apply(request))
          status(result) shouldBe GATEWAY_TIMEOUT
          contentType(result).get shouldBe "application/json"
          bodyOf(result) shouldBe ErrorResponses.GATEWAY_TIMEOUT
        }

        "return InternalServerError when GRO returns Forbidden" in {
          val forenames = "adam"
          val lastname = "conder"
          val dateofbirth = "2016-10-10"

          when(MockController.groConnector.get(mockEq(forenames), mockEq(lastname), mockEq(dateofbirth))(Matchers.any())).thenReturn(forbiddenResponse)
          val request = detailsRequest(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth)
          val result = await(MockController.details(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth).apply(request))
          status(result) shouldBe INTERNAL_SERVER_ERROR
          contentType(result).get shouldBe "application/json"
          bodyOf(result) shouldBe empty
        }

      }

    }

}
