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

package utils

import uk.gov.hmrc.brm.connectors.Encoder
import uk.gov.hmrc.play.test.UnitSpec

/**
  * Created by adamconder on 14/11/2016.
  */
class EncoderSpec extends UnitSpec {

  "Encoder" should {

    "parse a Map[String, String] into a UTF-8 URI" in {
      val details =  Map(
        "forenames" -> "Adàm TËST",
        "lastname" -> "SMÏTH",
        "dateofbirth" -> "2006-11-12"
      )
      Encoder.encode(details) shouldBe "forenames=Ad%C3%A0m+T%C3%8BST&lastname=SM%C3%8FTH&dateofbirth=2006-11-12"
    }

  }

}
