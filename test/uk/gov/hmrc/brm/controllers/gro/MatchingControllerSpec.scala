package uk.gov.hmrc.brm.controllers.gro

import org.mockito.Matchers
import org.mockito.Matchers.{eq => mockEq, _}
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import play.api.http.Status
import play.api.libs.json.{Json, JsValue}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.brm.connectors.BirthConnector
import uk.gov.hmrc.brm.controllers.MatchingController
import uk.gov.hmrc.play.http.{Upstream4xxResponse, Upstream5xxResponse}
import uk.gov.hmrc.play.test.{WithFakeApplication, UnitSpec}
import utils.JsonUtils

import scala.concurrent.Future

/**
 * Created by adamconder on 27/07/2016.
 */
class MatchingControllerSpec extends UnitSpec with WithFakeApplication with MockitoSugar {

  val reference = "500035710"
  val invalidReference = "812739812739183"

  def groResponseForReference(reference: String) = JsonUtils.getJsonFromFile(s"gro/$reference")
  def groResponseForName(surname: String) = JsonUtils.getJsonFromFile(s"gro/$surname")
  val groJsonNoRecord = JsonUtils.getJsonFromFile("gro/no-record")

  def referenceRequest(ref : String) = FakeRequest("GET", s"/birth-registration-matching-proxy/match/$ref")
  def params(firstName : String, lastName : String, dateOfBirth: String) = Map(s"firstName" -> firstName, "lastName" -> lastName, "dateOfBirth" -> dateOfBirth)
  def detailsRequest(firstName : String, lastName : String, dateOfBirth : String) = FakeRequest("GET", s"/birth-registration-matching-proxy/match/?firstName=$firstName&lastName=$lastName&dateOfBirth=$dateOfBirth")

  val validDetailsRequest = detailsRequest("Adam", "Wilson", "2005-10-05")

  val mockConnector = mock[BirthConnector]
  object MockController extends MatchingController {
    override val connector = mockConnector
  }

    "MatchingController" when {

      "initialising" should {

        "wire up dependencies correctly" in {
          MatchingController.connector shouldBe a[BirthConnector]
        }

      }

      "GET /birth-registration-matching-proxy/match/:ref" should {

        "not return NOT_FOUND endpoint" in {
          val result = route(referenceRequest(reference))
          result.isDefined shouldBe true
          status(result.get) should not be NOT_FOUND
        }

        "return 200 for a reference than exists in GRO" in {
          when(MockController.connector.getReference(mockEq(reference))(Matchers.any())).thenReturn(Future.successful(groResponseForReference(reference)))
          val request = referenceRequest(reference)
          val result = MockController.reference(reference).apply(request)
          status(result) shouldBe OK
          contentType(result).get shouldBe "application/json"
          jsonBodyOf(result) shouldBe groResponseForReference(reference)
        }

        "return 200 for a reference that does not exist in GRO" in {
          when(MockController.connector.getReference(mockEq(invalidReference))(Matchers.any())).thenReturn(Future.successful(groJsonNoRecord))
          val request = referenceRequest(reference)
          val result = MockController.reference(reference).apply(request)
          status(result) shouldBe OK
          contentType(result).get shouldBe "application/json"
          jsonBodyOf(result) shouldBe groJsonNoRecord
        }

        "return InternalServerError when GRO is down" in {
          when(MockController.connector.getReference(mockEq(reference))(Matchers.any())).thenReturn(Future.failed(new Upstream5xxResponse("", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)))
          val request = referenceRequest(reference)
          val result = MockController.reference(reference).apply(request)
          status(result) shouldBe INTERNAL_SERVER_ERROR
          contentType(result).get shouldBe "application/json"
        }

        "return BadRequest when invalid reference number is provided" in {
          when(MockController.connector.getReference(mockEq("ass1212sqw"))(Matchers.any())).thenReturn(Future.failed(new Upstream4xxResponse("", BAD_REQUEST, BAD_REQUEST)))
          val request = referenceRequest("ass1212sqw")
          val result = MockController.reference("ass1212sqw").apply(request)
          status(result) shouldBe INTERNAL_SERVER_ERROR
          contentType(result).get shouldBe "application/json"
        }

      }

      "GET /birth-registration-matching-proxy/match/" should {

        "not return NOT_FOUND endpoint" in {
          val result = route(validDetailsRequest)
          result.isDefined shouldBe true
          status(result.get) should not be NOT_FOUND
        }

        "return 200 for a reference than exists in GRO" in {
          when(MockController.connector.getDetails(mockEq(params("Adam", "Wilson", "2010-08-27")))(Matchers.any())).thenReturn(Future.successful(groResponseForReference(reference)))
          val request = validDetailsRequest
          val result = MockController.details().apply(request)
          status(result) shouldBe OK
          contentType(result).get shouldBe "application/json"
          jsonBodyOf(result) shouldBe groResponseForReference(reference)
        }

        "return 200 for a reference that does not exist in GRO" in {
          when(MockController.connector.getDetails(mockEq(params("Adam", "Conder", "2010-08-27")))(Matchers.any())).thenReturn(Future.successful(groJsonNoRecord))
          val request = validDetailsRequest
          val result = MockController.details().apply(request)
          status(result) shouldBe OK
          contentType(result).get shouldBe "application/json"
          jsonBodyOf(result) shouldBe groJsonNoRecord
        }

        "return InternalServerError when GRO is down" in {
          when(MockController.connector.getDetails(mockEq(params("Adam", "Wilson", "2010-08-27")))(Matchers.any())).thenReturn(Future.failed(new Upstream5xxResponse("", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)))
          val request = validDetailsRequest
          val result = MockController.details().apply(request)
          status(result) shouldBe INTERNAL_SERVER_ERROR
          contentType(result).get shouldBe "application/json"
        }


        "return BadRequest when invalid reference number is provided" in {
          when(MockController.connector.getDetails(mockEq(params("", "", "")))(Matchers.any())).thenReturn(Future.failed(new Upstream4xxResponse("", BAD_REQUEST, BAD_REQUEST)))
          val request = referenceRequest("ass1212sqw")
          val result = MockController.reference("ass1212sqw").apply(request)
          status(result) shouldBe INTERNAL_SERVER_ERROR
          contentType(result).get shouldBe "application/json"
        }

      }

    }

}
