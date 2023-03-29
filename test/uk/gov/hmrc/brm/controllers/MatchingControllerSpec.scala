/*
 * Copyright 2023 HM Revenue & Customs
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

import akka.stream.Materializer
import org.mockito.ArgumentMatchers.{any, eq => mockEq}
import org.mockito.Mockito._
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import play.api.mvc.ControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.brm.TestFixture
import uk.gov.hmrc.brm.connectors.{GROEnglandAndWalesConnector, _}
import uk.gov.hmrc.brm.metrics.BRMMetrics
import uk.gov.hmrc.brm.utils.JsonUtils
import uk.gov.hmrc.brm.utils.ResponseHelper._
import uk.gov.hmrc.http.{HeaderCarrier, JsValidationException}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class MatchingControllerSpec extends TestFixture {

  implicit lazy val materializer: Materializer = fakeApplication.materializer

  override lazy val fakeApplication: Application = GuiceApplicationBuilder(
    disabled = Seq(classOf[com.kenshoo.play.metrics.PlayModule])
  ).build()

  implicit val hc: HeaderCarrier   = HeaderCarrier()
  implicit val metrics: BRMMetrics = new BRMMetrics

  val reference                               = "500035710"
  val invalidReference                        = "812739812739183"
  val EXAMPLE_UPSTREAM_CORRELATION_ID: String = "b98a62e6-0a3e-11ec-9a03-0242a30003"

  def groResponse(reference: String): JsValue = JsonUtils.getJsonFromFile(s"gro/$reference")
  val groJsonNoRecord: JsValue                = JsonUtils.getJsonFromFile("gro/NoMatch")

  def referenceRequest(ref: String): FakeRequest[JsValue] =
    FakeRequest("POST", s"/birth-registration-matching-proxy/match/reference")
      .withHeaders(("Content-type", "application/json"))
      .withBody(Json.parse(s"""
                    |{
                    |"reference": "$ref"
                    |}
                    """.stripMargin))

  def referenceRequestWithCorrelationID(ref: String): FakeRequest[JsValue] =
    FakeRequest("POST", s"/birth-registration-matching-proxy/match/reference")
      .withHeaders(("Content-type", "application/json"), ("X-Correlation-ID", EXAMPLE_UPSTREAM_CORRELATION_ID))
      .withBody(Json.parse(s"""
                      |{
                      |"reference": "$ref"
                      |}
                    """.stripMargin))

  def badReferenceRequest(ref: String): FakeRequest[JsValue] =
    FakeRequest("POST", s"/birth-registration-matching-proxy/match/reference")
      .withHeaders(("Content-type", "application/json"))
      .withBody(Json.parse(s"""
                      |{
                      |"ref": "$ref"
                      |}
                    """.stripMargin))

  def detailsRequest(forenames: String, lastname: String, dateofbirth: String): FakeRequest[JsValue] =
    FakeRequest("POST", "/birth-registration-matching/match/details")
      .withHeaders((ACCEPT, "application/json"))
      .withBody(
        Json.parse(s"""
         |{
         |"forenames": "$forenames",
         |"lastname": "$lastname",
         |"dateofbirth": "$dateofbirth"
         |}
       """.stripMargin)
      )

  def badDetailsRequest(forenames: String, lastname: String, dateofbirth: String): FakeRequest[JsValue] =
    FakeRequest("POST", "/birth-registration-matching/match/details")
      .withHeaders((ACCEPT, "application/json"))
      .withBody(
        Json.parse(s"""
         |{
         |"firstname": "$forenames",
         |"lastname": "$lastname",
         |"dateofbirth": "$dateofbirth"
         |}
       """.stripMargin)
      )

  def badDetailsRequestForLastName(forenames: String, lastname: String, dateofbirth: String): FakeRequest[JsValue] =
    FakeRequest("POST", "/birth-registration-matching/match/details")
      .withHeaders((ACCEPT, "application/json"))
      .withBody(
        Json.parse(s"""
                      |{
                      |"forenames": "$forenames",
                      |"surname": "$lastname",
                      |"dateofbirth": "$dateofbirth"
                      |}
       """.stripMargin)
      )

  def badDetailsRequestForDateOfBirth(forenames: String, lastname: String, dateofbirth: String): FakeRequest[JsValue] =
    FakeRequest("POST", "/birth-registration-matching/match/details")
      .withHeaders((ACCEPT, "application/json"))
      .withBody(
        Json.parse(s"""
                      |{
                      |"forenames": "$forenames",
                      |"lastname": "$lastname",
                      |"dob": "$dateofbirth"
                      |}
       """.stripMargin)
      )

  val validDetailsRequest: FakeRequest[JsValue]   = detailsRequest("Adam", "Wilson", "2010-08-27")
  val noMatchDetailsRequest: FakeRequest[JsValue] = detailsRequest("Adam", "Conder", "2010-08-27")

  def successResponse(json: JsValue): Future[BirthSuccessResponse[JsValue]] =
    Future.successful(BirthSuccessResponse(json))

  val jsValidationExceptionResponse: BirthResponse = BirthErrorResponse(
    new JsValidationException("", "", getClass, "")
  )

  val stubCC: ControllerComponents = stubControllerComponents()

  val MockController: MatchingController =
    new MatchingController(mock[GROEnglandAndWalesConnector], stubCC, mock[BRMMetrics]) {}

  "MatchingController" when {

    "initialising" should {

      "wire up dependencies correctly" in {
        MockController.groConnector shouldBe a[GROEnglandAndWalesConnector]
      }

    }

    "getOrCreateCorrelationID" should {

      "return a random UUID when no x-correlation-id is present within the request headers" in {
        val request = referenceRequest(reference)
        val result  = MockController.getOrCreateCorrelationID(request)

        result.length shouldBe 36
      }

      "return the x-correlation-id value found in the upstream request headers" in {
        val request = referenceRequestWithCorrelationID(reference)
        val result  = MockController.getOrCreateCorrelationID(request)

        result shouldBe EXAMPLE_UPSTREAM_CORRELATION_ID
      }

    }

    "POST /birth-registration-matching-proxy/match/reference" should {

      "return 200 for a reference than exists in GRO" in {
        val json = groResponse(reference)

        when(
          MockController.groConnector
            .getReference(mockEq[String](reference))(any[HeaderCarrier], any[BRMMetrics], any[ExecutionContext])
        )
          .thenReturn(successResponse(json))

        val request = referenceRequest(reference)
        val result  = MockController.reference.apply(request)

        status(result)                     shouldBe OK
        contentType(result).get            shouldBe "application/json"
        contentAsJson(result).as[JsObject] shouldBe json.as[JsObject]
      }

      "return 400 for when reference is not passed" in {
        val request = badReferenceRequest(reference)
        val result  = MockController.reference.apply(request)
        status(result) shouldBe BAD_REQUEST
      }

      "return 404 for a reference that does not exist in GRO" in {
        when(
          MockController.groConnector
            .getReference(mockEq(invalidReference))(any[HeaderCarrier], any[BRMMetrics], any[ExecutionContext])
        ).thenReturn(Future(notFoundResponse))

        val request = referenceRequest(invalidReference)
        val result  = MockController.reference.apply(request)

        status(result)          shouldBe NOT_FOUND
        contentType(result).get shouldBe "application/json"
        contentAsString(result) shouldBe ErrorResponses.NOT_FOUND.toString
      }

      "return SERVICE_UNAVAILABLE  when GRO returns Upstream5xxResponse Service Unavailable" in {
        when(
          MockController.groConnector
            .getReference(mockEq(reference))(any[HeaderCarrier], any[BRMMetrics], any[ExecutionContext])
        )
          .thenReturn(Future(serviceUnavailableResponse))

        val request = referenceRequest(reference)
        val result  = MockController.reference.apply(request)

        status(result)          shouldBe SERVICE_UNAVAILABLE
        contentType(result).get shouldBe "application/json"
        contentAsString(result) shouldBe ErrorResponses.CONNECTION_DOWN.toString
      }

      "return INTERNAL_SERVER_ERROR when GRO is down" in {
        when(
          MockController.groConnector
            .getReference(mockEq(reference))(any[HeaderCarrier], any[BRMMetrics], any[ExecutionContext])
        )
          .thenReturn(Future(internalServerErrorResponse))

        val request = referenceRequest(reference)
        val result  = MockController.reference.apply(request)

        status(result)          shouldBe INTERNAL_SERVER_ERROR
        contentType(result).get shouldBe "application/json"
        contentAsString(result) shouldBe ErrorResponses.CONNECTION_DOWN.toString
      }

      "return Bad_Gateway when GRO returns Upstream5xxResponse BadGateway" in {
        when(
          MockController.groConnector
            .getReference(mockEq(reference))(any[HeaderCarrier], any[BRMMetrics], any[ExecutionContext])
        )
          .thenReturn(Future(badGatewayResponse))

        val request = referenceRequest(reference)
        val result  = MockController.reference.apply(request)

        status(result)          shouldBe BAD_GATEWAY
        contentType(result).get shouldBe "application/json"
        contentAsString(result) shouldBe ErrorResponses.BAD_GATEWAY.toString
      }

      "return BadRequest when invalid reference number is provided" in {
        when(
          MockController.groConnector
            .getReference(mockEq("ass1212sqw"))(any[HeaderCarrier], any[BRMMetrics], any[ExecutionContext])
        )
          .thenReturn(Future(badRequestResponse))
        val request = referenceRequest("ass1212sqw")
        val result  = MockController.reference.apply(request)
        status(result)          shouldBe BAD_REQUEST
        contentType(result).get shouldBe "application/json"
        contentAsString(result) shouldBe ErrorResponses.BAD_REQUEST.toString
      }

      "return InternalServerError when invalid json is returned" in {
        when(
          MockController.groConnector
            .getReference(mockEq("ass1212sqw"))(any[HeaderCarrier], any[BRMMetrics], any[ExecutionContext])
        )
          .thenReturn(Future(jsValidationExceptionResponse))
        val request = referenceRequest("ass1212sqw")
        val result  = MockController.reference.apply(request)
        status(result)          shouldBe INTERNAL_SERVER_ERROR
        contentType(result).get shouldBe "application/json"
      }

      "return 403 Forbidden when GRO responds with 418 teapot" in {
        when(
          MockController.groConnector
            .getReference(mockEq("SELECT ALL --"))(any[HeaderCarrier], any[BRMMetrics], any[ExecutionContext])
        )
          .thenReturn(Future(teapotException))
        val request = referenceRequest("SELECT ALL --")
        val result  = MockController.reference.apply(request)
        status(result)          shouldBe FORBIDDEN
        contentType(result).get shouldBe "application/json"
        contentAsString(result) shouldBe ErrorResponses.TEAPOT.toString
      }

      "return gateway_timeout when GRO times out" in {
        when(
          MockController.groConnector
            .getReference(mockEq("ass1212sqw"))(any[HeaderCarrier], any[BRMMetrics], any[ExecutionContext])
        )
          .thenReturn(Future(gatewayTimeoutResponse))
        val request = referenceRequest("ass1212sqw")
        val result  = MockController.reference.apply(request)
        status(result)          shouldBe GATEWAY_TIMEOUT
        contentType(result).get shouldBe "application/json"
        contentAsString(result) shouldBe ErrorResponses.GATEWAY_TIMEOUT.toString
      }

      "return 403 Forbidden when GRO returns Forbidden" in {
        when(
          MockController.groConnector
            .getReference(mockEq("ass1212sqw"))(any[HeaderCarrier], any[BRMMetrics], any[ExecutionContext])
        )
          .thenReturn(Future(forbiddenResponse))
        val request = referenceRequest("ass1212sqw")
        val result  = MockController.reference.apply(request)
        status(result)          shouldBe FORBIDDEN
        contentType(result).get shouldBe "application/json"
        contentAsString(result) shouldBe ErrorResponses.CERTIFICATE_INVALID.toString
      }

    }

    "POST /birth-registration-matching-proxy/match/details" should {

      "return 200 with records for details that match" in {
        val forenames   = "adam"
        val lastname    = "smith"
        val dateofbirth = "2006-11-12"

        val json = groResponse("2006-11-12_smith_adam")
        when(
          MockController.groConnector.getDetails(mockEq(forenames), mockEq(lastname), mockEq(dateofbirth))(
            any[HeaderCarrier],
            any[BRMMetrics],
            any[ExecutionContext]
          )
        )
          .thenReturn(successResponse(json))

        val request = detailsRequest(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth)
        val result  = MockController.details().apply(request)
        status(result)          shouldBe OK
        contentType(result).get shouldBe "application/json"
        contentAsJson(result)   shouldBe json
      }

      "return 200 with records for details that match UTF-8" in {
        val forenames   = "Adàm TËST"
        val lastname    = "SMÏTH"
        val dateofbirth = "2006-11-12"

        val json = groResponse("2006-11-12_smith_adam-utf-8")
        when(
          MockController.groConnector.getDetails(mockEq(forenames), mockEq(lastname), mockEq(dateofbirth))(
            any[HeaderCarrier],
            any[BRMMetrics],
            any[ExecutionContext]
          )
        )
          .thenReturn(successResponse(json))

        val request = detailsRequest(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth)
        val result  = MockController.details().apply(request)
        status(result)          shouldBe OK
        contentType(result).get shouldBe "application/json"
        contentAsJson(result)   shouldBe json
      }

      "return 200 [] for details that does not match" in {
        val forenames   = "adam"
        val lastname    = "conder"
        val dateofbirth = "2016-10-10"

        val json = groResponse("NoMatch")

        when(
          MockController.groConnector.getDetails(mockEq(forenames), mockEq(lastname), mockEq(dateofbirth))(
            any[HeaderCarrier],
            any[BRMMetrics],
            any[ExecutionContext]
          )
        ).thenReturn(successResponse(json))

        val request = detailsRequest(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth)
        val result  = MockController.details().apply(request)
        status(result)          shouldBe OK
        contentType(result).get shouldBe "application/json"
        contentAsJson(result)   shouldBe groResponse("NoMatch")
      }

      "return 400 BadRequest when firstname key is invalid" in {
        val forenames   = "adam"
        val lastname    = "conder"
        val dateofbirth = "2016-10-10"
        val request     = badDetailsRequest(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth)
        val result      = MockController.details().apply(request)
        status(result) shouldBe BAD_REQUEST
      }

      "return 400 BadRequest when lastname key is invalid" in {
        val forenames   = "adam"
        val lastname    = "conder"
        val dateofbirth = "2016-10-10"
        val request     =
          badDetailsRequestForLastName(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth)
        val result      = MockController.details().apply(request)
        status(result)          shouldBe BAD_REQUEST
        contentType(result).get shouldBe "application/json"
        contentAsString(result) shouldBe ErrorResponses.BAD_REQUEST.toString
      }

      "return 400 BadRequest when dateofbirth key is invalid" in {
        val forenames   = "adam"
        val lastname    = "conder"
        val dateofbirth = "2016-10-10"
        val request     =
          badDetailsRequestForDateOfBirth(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth)
        val result      = MockController.details().apply(request)
        status(result)          shouldBe BAD_REQUEST
        contentType(result).get shouldBe "application/json"
        contentAsString(result) shouldBe ErrorResponses.BAD_REQUEST.toString
      }

      "return INTERNAL_SERVER_ERROR when GRO returns Upstream5xxResponse InternalServerError ie GRO is down." in {
        val forenames   = "adam"
        val lastname    = "conder"
        val dateofbirth = "2016-10-10"

        when(
          MockController.groConnector.getDetails(mockEq(forenames), mockEq(lastname), mockEq(dateofbirth))(
            any[HeaderCarrier],
            any[BRMMetrics],
            any[ExecutionContext]
          )
        )
          .thenReturn(Future(internalServerErrorResponse))
        val request = detailsRequest(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth)
        val result  = MockController.details().apply(request)
        status(result)          shouldBe INTERNAL_SERVER_ERROR
        contentType(result).get shouldBe "application/json"
        contentAsString(result) shouldBe ErrorResponses.CONNECTION_DOWN.toString
      }

      "return SERVICE_UNAVAILABLE when GRO returns Upstream5xxResponse Service Unavailable" in {
        val forenames   = "adam"
        val lastname    = "conder"
        val dateofbirth = "2016-10-10"

        when(
          MockController.groConnector.getDetails(mockEq(forenames), mockEq(lastname), mockEq(dateofbirth))(
            any[HeaderCarrier],
            any[BRMMetrics],
            any[ExecutionContext]
          )
        ).thenReturn(Future(serviceUnavailableResponse))
        val request = detailsRequest(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth)
        val result  = MockController.details().apply(request)
        status(result)          shouldBe SERVICE_UNAVAILABLE
        contentType(result).get shouldBe "application/json"
        contentAsString(result) shouldBe ErrorResponses.CONNECTION_DOWN.toString
      }

      "return BadGateway when GRO returns Upstream5xxResponse BadGateway" in {
        val forenames   = "adam"
        val lastname    = "conder"
        val dateofbirth = "2016-10-10"

        when(
          MockController.groConnector.getDetails(mockEq(forenames), mockEq(lastname), mockEq(dateofbirth))(
            any[HeaderCarrier],
            any[BRMMetrics],
            any[ExecutionContext]
          )
        )
          .thenReturn(Future(badGatewayResponse))
        val request = detailsRequest(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth)
        val result  = MockController.details().apply(request)
        status(result)          shouldBe BAD_GATEWAY
        contentType(result).get shouldBe "application/json"
        contentAsString(result) shouldBe ErrorResponses.BAD_GATEWAY.toString
      }

      "return BadRequest when forenames is not provided" in {
        val forenames   = ""
        val lastname    = "conder"
        val dateofbirth = "2016-10-10"

        when(
          MockController.groConnector.getDetails(mockEq(forenames), mockEq(lastname), mockEq(dateofbirth))(
            any[HeaderCarrier],
            any[BRMMetrics],
            any[ExecutionContext]
          )
        )
          .thenReturn(Future(badRequestResponse))
        val request = detailsRequest(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth)
        val result  = MockController.details().apply(request)
        status(result)          shouldBe BAD_REQUEST
        contentType(result).get shouldBe "application/json"
        contentAsString(result) shouldBe ErrorResponses.BAD_REQUEST.toString
      }

      "return BadRequest when lastname is not provided" in {
        val forenames   = "adam"
        val lastname    = ""
        val dateofbirth = "2016-10-10"

        when(
          MockController.groConnector.getDetails(mockEq(forenames), mockEq(lastname), mockEq(dateofbirth))(
            any[HeaderCarrier],
            any[BRMMetrics],
            any[ExecutionContext]
          )
        )
          .thenReturn(Future(badRequestResponse))
        val request = detailsRequest(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth)
        val result  = MockController.details().apply(request)
        status(result)          shouldBe BAD_REQUEST
        contentType(result).get shouldBe "application/json"
        contentAsString(result) shouldBe ErrorResponses.BAD_REQUEST.toString
      }

      "return BadRequest when dateofbirth is not provided" in {
        val forenames   = "adam"
        val lastname    = "conder"
        val dateofbirth = ""

        when(
          MockController.groConnector.getDetails(mockEq(forenames), mockEq(lastname), mockEq(dateofbirth))(
            any[HeaderCarrier],
            any[BRMMetrics],
            any[ExecutionContext]
          )
        )
          .thenReturn(Future(badRequestResponse))
        val request = detailsRequest(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth)
        val result  = MockController.details().apply(request)
        status(result)          shouldBe BAD_REQUEST
        contentType(result).get shouldBe "application/json"
        contentAsString(result) shouldBe ErrorResponses.BAD_REQUEST.toString
      }

      "return BadRequest when dateofbirth is invalid format" in {
        val forenames   = "adam"
        val lastname    = "conder"
        val dateofbirth = "10-10-2016"

        when(
          MockController.groConnector.getDetails(mockEq(forenames), mockEq(lastname), mockEq(dateofbirth))(
            any[HeaderCarrier],
            any[BRMMetrics],
            any[ExecutionContext]
          )
        )
          .thenReturn(Future(badRequestResponse))
        val request = detailsRequest(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth)
        val result  = MockController.details().apply(request)
        status(result)          shouldBe BAD_REQUEST
        contentType(result).get shouldBe "application/json"
        contentAsString(result) shouldBe ErrorResponses.BAD_REQUEST.toString
      }

      "return InternalServerError when invalid json is returned" in {
        val forenames   = "adam"
        val lastname    = "conder"
        val dateofbirth = "2016-10-10"

        when(
          MockController.groConnector
            .getDetails(mockEq(forenames), mockEq(lastname), mockEq(dateofbirth))(
              any[HeaderCarrier],
              any[BRMMetrics],
              any[ExecutionContext]
            )
        )
          .thenReturn(Future(jsValidationExceptionResponse))
        val request = detailsRequest(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth)
        val result  = MockController.details().apply(request)
        status(result)          shouldBe INTERNAL_SERVER_ERROR
        contentType(result).get shouldBe "application/json"
        contentAsString(result) shouldBe ErrorResponses.UNKNOWN_ERROR.toString
      }

      "return GatewayTimeout when GRO times out" in {
        val forenames   = "adam"
        val lastname    = "conder"
        val dateofbirth = "2016-10-10"

        when(
          MockController.groConnector.getDetails(mockEq(forenames), mockEq(lastname), mockEq(dateofbirth))(
            any[HeaderCarrier],
            any[BRMMetrics],
            any[ExecutionContext]
          )
        )
          .thenReturn(Future(gatewayTimeoutResponse))
        val request = detailsRequest(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth)
        val result  = MockController.details().apply(request)
        status(result)          shouldBe GATEWAY_TIMEOUT
        contentType(result).get shouldBe "application/json"
        contentAsString(result) shouldBe ErrorResponses.GATEWAY_TIMEOUT.toString
      }

      "return 403 Forbidden when GRO responds with 418 teapot" in {
        val forenames   = "SELECT ALL --"
        val lastname    = "conder"
        val dateofbirth = "2016-10-10"

        when(
          MockController.groConnector.getDetails(mockEq(forenames), mockEq(lastname), mockEq(dateofbirth))(
            any[HeaderCarrier],
            any[BRMMetrics],
            any[ExecutionContext]
          )
        )
          .thenReturn(Future(teapotException))
        val request = detailsRequest(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth)
        val result  = MockController.details().apply(request)
        status(result)          shouldBe FORBIDDEN
        contentType(result).get shouldBe "application/json"
        contentAsString(result) shouldBe ErrorResponses.TEAPOT.toString
      }

      "return 403 Forbidden when GRO returns Forbidden" in {
        val forenames   = "adam"
        val lastname    = "conder"
        val dateofbirth = "2016-10-10"

        when(
          MockController.groConnector.getDetails(mockEq(forenames), mockEq(lastname), mockEq(dateofbirth))(
            any[HeaderCarrier],
            any[BRMMetrics],
            any[ExecutionContext]
          )
        )
          .thenReturn(Future(forbiddenResponse))
        val request = detailsRequest(forenames = forenames, lastname = lastname, dateofbirth = dateofbirth)
        val result  = MockController.details().apply(request)
        status(result)          shouldBe FORBIDDEN
        contentType(result).get shouldBe "application/json"
        contentAsString(result) shouldBe ErrorResponses.CERTIFICATE_INVALID.toString
      }

    }

  }

}
