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

package uk.gov.hmrc.brm

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfter, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.brm.config.GroAppConfig
import uk.gov.hmrc.brm.utils.BaseUnitSpec

import scala.reflect.ClassTag

trait TestFixture
    extends AnyWordSpecLike
    with Matchers
    with OptionValues
    with MockitoSugar
    with BeforeAndAfter
    with GuiceOneAppPerSuite
    with BaseUnitSpec {

  def real[T: ClassTag]: T = app.injector.instanceOf[T]

  val testGroConfig: GroAppConfig = real[GroAppConfig]

}
