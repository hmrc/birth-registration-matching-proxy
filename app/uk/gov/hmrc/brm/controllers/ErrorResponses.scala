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

/**
 * Created by adamconder on 10/11/2016.
 */
object ErrorResponses {

  val CONNECTION_DOWN = "GRO_CONNECTION_DOWN"
  val BAD_REQUEST = "BAD_REQUEST"
  val NOT_FOUND = s"NOT_FOUND"
  val GATEWAY_TIMEOUT = s"GATEWAY_TIMEOUT"

}
