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

import java.net.URL

import com.sun.javafx.font.Metrics
import org.joda.time.{DateTime, DateTimeUtils, LocalDate}
import org.mockito.Matchers
import org.mockito.Matchers.{eq => mockEq}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.{JsArray, JsNull, JsObject}
import play.api.test.Helpers._
import uk.co.bigbeeconsultants.http.{Config, HttpClient}
import uk.co.bigbeeconsultants.http.header.{Headers, MediaType}
import uk.co.bigbeeconsultants.http.request.Request
import uk.co.bigbeeconsultants.http.response.{Response, Status}
import uk.gov.hmrc.brm.metrics.GroMetrics
import uk.gov.hmrc.brm.utils.{AccessTokenRepository, CertificateStatus}
import uk.gov.hmrc.play.http.{Upstream4xxResponse, _}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import utils.JsonUtils
import utils.ResponseHelper._

/**
  * Created by adamconder on 01/08/2016.
  */
class BirthConnectorSpec extends UnitSpec with WithFakeApplication with MockitoSugar with BeforeAndAfter {

  implicit val hc = HeaderCarrier()

  val mockHttpClient = mock[HttpClient]

  val mockCertificateStatus = mock[CertificateStatus]

  object MockBirthConnector extends BirthConnector {
    override val httpClient = mockHttpClient
    override val metrics = GroMetrics
    override val authRepository = new AccessTokenRepository
  }

  def groResponse(reference: String) = JsonUtils.getJsonFromFile(s"gro/$reference")

  val authRecord = JsonUtils.getJsonFromFile("gro/auth")

  before {
    MockBirthConnector.authRepository.saveToken("",DateTime.now())
  }


  val headers = Map(
    "Authorization" -> s"Bearer something",
    "X-Auth-Downstream-Username" -> "hmrc"
  )

  "BirthConnector" when {

    "initialising" should {
      "wire up dependencies" in {
        MockBirthConnector.httpClient shouldBe a[HttpClient]
      }
    }

    "parsing json" should {

      "throw Upstream5xxResponse for invalid json" in {
        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099/oauth/login"), None), Status.S200_OK, MediaType.APPLICATION_JSON, authRecord.toString())
        val eventResponse = Response.apply(Request.get(new URL("http://localhost:8099/v0/events/birth"), headers = Headers.apply(headers)), Status.S200_OK, MediaType.APPLICATION_JSON, "[something]")

        when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
        when(mockHttpClient.get(Matchers.any(), Matchers.any())).thenReturn(eventResponse)


        val result = await(MockBirthConnector.getReference("500035710"))
        val birthErrorResponse = result.asInstanceOf[BirthErrorResponse]
        birthErrorResponse.cause.isInstanceOf[Upstream5xxResponse] shouldBe true

      }
    }



    "getReference" should {

      "200 with json response with match" in {
        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099/oauth/login"), None), Status.S200_OK, MediaType.APPLICATION_JSON, authRecord.toString())
        val eventResponse = Response.apply(Request.get(new URL("http://localhost:8099/v0/events/birth"), headers = Headers.apply(headers)), Status.S200_OK, MediaType.APPLICATION_JSON, groResponse("500035710").toString())

        when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
        when(mockHttpClient.get(Matchers.any(), Matchers.any())).thenReturn(eventResponse)

        val result = await(MockBirthConnector.getReference("500035710"))
        result should not be JsNull
      }

      "404 with NotFound response for no match" in {
        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None), Status.S200_OK, MediaType.APPLICATION_JSON, authRecord.toString())
        val eventResponse = Response.apply(Request.get(new URL("http://localhost:8099"), headers = Headers.apply(headers)), Status.S404_NotFound, MediaType.APPLICATION_JSON, groResponse("valid_empty").toString())

        when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(authResponse)
        when(mockHttpClient.get(Matchers.any(), Matchers.any()))
          .thenReturn(eventResponse)

        val result = await(MockBirthConnector.getReference("500037654675710"))
        result.isInstanceOf[BirthErrorResponse] shouldBe true
        val birthErrorResponse = result.asInstanceOf[BirthErrorResponse]
        birthErrorResponse.cause.isInstanceOf[Upstream4xxResponse] shouldBe true
        val resonseException = birthErrorResponse.cause.asInstanceOf[Upstream4xxResponse]
        resonseException.upstreamResponseCode shouldBe NOT_FOUND
      }

      "400 with BadRequest for authentication" in {

        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None), Status.S400_BadRequest, MediaType.APPLICATION_JSON, "")
        when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)

        val result = await(MockBirthConnector.getReference("500035710"))
        result.isInstanceOf[BirthErrorResponse] shouldBe true
        val birthErrorResponse = result.asInstanceOf[BirthErrorResponse]
        birthErrorResponse.cause.isInstanceOf[Upstream4xxResponse] shouldBe true
        val resonseException = birthErrorResponse.cause.asInstanceOf[Upstream4xxResponse]
        resonseException.upstreamResponseCode shouldBe BAD_REQUEST
      }

      "500 with InternalServerError for authentication" in {
        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None), Status.S500_InternalServerError, MediaType.APPLICATION_JSON, "")
        when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)

        val result = await(MockBirthConnector.getReference("500035710"))
        result.isInstanceOf[BirthErrorResponse] shouldBe true
        val birthErrorResponse = result.asInstanceOf[BirthErrorResponse]
        birthErrorResponse.cause.isInstanceOf[Upstream5xxResponse] shouldBe true
        val resonseException = birthErrorResponse.cause.asInstanceOf[Upstream5xxResponse]
        resonseException.upstreamResponseCode shouldBe INTERNAL_SERVER_ERROR

      }

      "400 with BadRequest for reference" in {
        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None), Status.S200_OK, MediaType.APPLICATION_JSON, authRecord.toString())
        val eventResponse = Response.apply(Request.get(new URL("http://localhost:8099"), headers = Headers.apply(headers)), Status.S400_BadRequest, MediaType.APPLICATION_JSON, "")

        when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
        when(mockHttpClient.get(Matchers.any(), Matchers.any())).thenReturn(eventResponse)


        val result = await(MockBirthConnector.getReference("500035710"))
        val birthErrorResponse = result.asInstanceOf[BirthErrorResponse]
        birthErrorResponse.cause.isInstanceOf[Upstream4xxResponse] shouldBe true
        val resonseException = birthErrorResponse.cause.asInstanceOf[Upstream4xxResponse]
        resonseException.upstreamResponseCode shouldBe BAD_REQUEST

      }

      "500 with InternalServerError for reference" in {
        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None), Status.S200_OK, MediaType.APPLICATION_JSON, authRecord.toString())
        val eventResponse = Response.apply(Request.get(new URL("http://localhost:8099"), headers = Headers.apply(headers)), Status.S500_InternalServerError, MediaType.APPLICATION_JSON, "")

        when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
        when(mockHttpClient.get(Matchers.any(), Matchers.any())).thenReturn(eventResponse)

        val result = await(MockBirthConnector.getReference("500035710"))
        result.isInstanceOf[BirthErrorResponse] shouldBe true
        val birthErrorResponse = result.asInstanceOf[BirthErrorResponse]
        birthErrorResponse.cause.isInstanceOf[Upstream5xxResponse] shouldBe true
        val resonseException = birthErrorResponse.cause.asInstanceOf[Upstream5xxResponse]
        resonseException.upstreamResponseCode shouldBe INTERNAL_SERVER_ERROR
      }

      "500 with InternalSeverError when certificate has expired" in {
        // Force LocalDate to something other than now
        val date = new DateTime(2050: Int, 9: Int, 15: Int, 5: Int, 10: Int, 10: Int)
        DateTimeUtils.setCurrentMillisFixed(date.getMillis)

        val result = await(MockBirthConnector.getReference("500035710"))
        result.isInstanceOf[BirthErrorResponse] shouldBe true
        val birthErrorResponse = result.asInstanceOf[BirthErrorResponse]
        birthErrorResponse.cause.isInstanceOf[Upstream5xxResponse] shouldBe true
        val resonseException = birthErrorResponse.cause.asInstanceOf[Upstream5xxResponse]
        resonseException.upstreamResponseCode shouldBe INTERNAL_SERVER_ERROR

        DateTimeUtils.setCurrentMillisSystem()
      }


      "500 with InternalServerError when authentication returns empty or no access tokem" in {
        val authResponse = Response.apply(Request.post(new URL("http://localhost:8099"), None), Status.S200_OK, MediaType.APPLICATION_JSON, "")
       // val eventResponse = Response.apply(Request.get(new URL("http://localhost:8099"), headers = Headers.apply(headers)), Status.S500_InternalServerError, MediaType.APPLICATION_JSON, "")

        when(mockHttpClient.post(Matchers.any(), Matchers.any(), Matchers.any())).thenReturn(authResponse)
       // when(mockHttpClient.get(Matchers.any(), Matchers.any())).thenReturn(eventResponse)

        val result = await(MockBirthConnector.getReference("500035710"))
        result.isInstanceOf[BirthErrorResponse] shouldBe true
        /*val birthErrorResponse = result.asInstanceOf[BirthErrorResponse]
        birthErrorResponse.cause.isInstanceOf[Upstream5xxResponse] shouldBe true
        val resonseException = birthErrorResponse.cause.asInstanceOf[Upstream5xxResponse]
        resonseException.upstreamResponseCode shouldBe INTERNAL_SERVER_ERROR*/
      }



    }

  }

}
