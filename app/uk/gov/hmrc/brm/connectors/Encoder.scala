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

package uk.gov.hmrc.brm.connectors

import java.net.URLEncoder

import uk.gov.hmrc.brm.utils.BrmLogger

trait Encoder {

  def encode(params: Map[String, String]): String

}

object Encoder extends Encoder {

  def encode(params: Map[String, String]): String = {
    val query = params.map(pair => pair._1 + "=" + URLEncoder.encode(pair._2, "UTF-8")).mkString("&")
    BrmLogger.debug(this.getClass.getSimpleName, "encode", s"params: $query")
    query
  }

}
