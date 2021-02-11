/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.brm.utils

import play.api.test.Helpers._
import uk.gov.hmrc.brm.connectors.{Birth404ErrorResponse, BirthErrorResponse, BirthResponse}
import uk.gov.hmrc.http.UpstreamErrorResponse

object ResponseHelper {

  val notFoundResponse: BirthResponse = Birth404ErrorResponse(
    UpstreamErrorResponse("", NOT_FOUND, NOT_FOUND)
  )
  val badRequestResponse: BirthResponse = BirthErrorResponse(
    UpstreamErrorResponse("", BAD_REQUEST, BAD_REQUEST)
  )

  val teapotException : BirthResponse = BirthErrorResponse(
    UpstreamErrorResponse("", IM_A_TEAPOT, IM_A_TEAPOT)
  )

  val internalServerErrorResponse: BirthResponse = BirthErrorResponse(
    UpstreamErrorResponse("", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)
  )

  val serviceUnavailableResponse : BirthResponse = BirthErrorResponse(
    UpstreamErrorResponse("", SERVICE_UNAVAILABLE, SERVICE_UNAVAILABLE)
  )

  val badGatewayResponse: BirthResponse = BirthErrorResponse(
    UpstreamErrorResponse("", BAD_GATEWAY, BAD_GATEWAY)
  )

  val gatewayTimeoutResponse: BirthResponse = BirthErrorResponse(
    UpstreamErrorResponse("", GATEWAY_TIMEOUT, GATEWAY_TIMEOUT)
  )

  val forbiddenResponse: BirthResponse = BirthErrorResponse(
    UpstreamErrorResponse("", FORBIDDEN, FORBIDDEN)
  )

}
