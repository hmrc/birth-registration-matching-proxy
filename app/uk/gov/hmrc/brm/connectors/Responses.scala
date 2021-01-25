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

package uk.gov.hmrc.brm.connectors

import play.api.libs.json.JsValue
import uk.gov.hmrc.brm.connectors.ConnectorTypes._


trait BirthResponse

case class BirthAccessTokenResponse(token: AccessToken) extends BirthResponse
case class BirthSuccessResponse[T <: JsValue](json: T) extends BirthResponse
case class BirthErrorResponse(cause: Exception) extends BirthResponse
case class Birth404ErrorResponse(cause: Exception) extends BirthResponse
