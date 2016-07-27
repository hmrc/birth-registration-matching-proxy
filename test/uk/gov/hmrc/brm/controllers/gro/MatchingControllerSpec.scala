package uk.gov.hmrc.brm.controllers.gro

import org.mockito.Matchers
import org.mockito.Matchers.{eq => mockEq, _}
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.{JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.play.test.{WithFakeApplication, UnitSpec}
import utils.JsonUtils

import scala.concurrent.Future

/**
 * Created by adamconder on 27/07/2016.
 */
class MatchingControllerSpec extends UnitSpec with WithFakeApplication with MockitoSugar {

  val reference = "500035710"
  val invalidReference = "812739812739183"
  val groJsonResponseObject = JsonUtils.getJsonFromFile(s"gro/$reference")
  val groJsonNoRecord = JsonUtils.getJsonFromFile("gro/no-record")

  val groMatchWithReference = Json.parse(
    s"""
       |{
       | "forename" : "Chris",
       | "surname" : "Jones",
       | "dateOfBirth" : "1990-02-16",
       | "reference" : "$reference"
       |}
    """.stripMargin)

  val groMatchWithInvalidReference = Json.parse(
    s"""
       |{
       | "forename" : "Chris",
       | "surname" : "Jones",
       | "dateOfBirth" : "1990-02-16",
       | "reference" : "$invalidReference"
                                    |}
    """.stripMargin)

  val groMatchWithName = Json.parse(
    s"""
       |{
       | "forename" : "Adam",
       | "surname" : "Smith",
       | "dateOfBirth" : "2006-11-12"
       |}
    """.stripMargin)

  def postRequest(v: JsValue) : FakeRequest[JsValue] = FakeRequest("POST", "/birth-registration-matching-proxy/match")
    .withHeaders(("Content-type", "application/json"))
    .withBody(v)

  val mockConnector = mock[BirthConnector]
  object MockController extends GROController {
    override val GROConnector = mockConnector
  }

    "MatchingController" when {

      "initialising" should {

        "wire up dependencies correctly" in {
          GROController.GROConnector shouldBe a[BirthConnector]
        }

      }

      "POST /birth-registration-matching-proxy with reference" should {

        "return 200 for a reference than exists in GRO" in {
          when(MockController.GROConnector.getReference(mockEq(reference))(Matchers.any())).thenReturn(Future.successful(groJsonResponseObject))
          val request = postRequest(groMatchWithReference)
          val result = MockController.post().apply(request)
          status(result) shouldBe 200
          contentType(result).get shouldBe "application/json"
        }

        "return an empty response when no record exists for reference" in {
          when(MockController.GROConnector.getReference(mockEq(invalidReference))(Matchers.any())).thenReturn(Future.successful(groJsonNoRecord))
          val request = postRequest(groMatchWithInvalidReference)
          val result = MockController.post().apply(request)
          status(result) shouldBe 200
          contentType(result).get shouldBe "application/json"
        }

      }

      "POST /birth-registration-matching-proxy without reference" should {

      }

    }

}
