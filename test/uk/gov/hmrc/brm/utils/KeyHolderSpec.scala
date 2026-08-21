/*
 * Copyright 2026 HM Revenue & Customs
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

import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class KeyHolderSpec extends AnyWordSpecLike with Matchers with BeforeAndAfterEach {

  override def beforeEach(): Unit = {
    super.beforeEach()
    KeyHolder.setKey("")
  }

  override def afterEach(): Unit = {
    KeyHolder.setKey("")
    super.afterEach()
  }

  "KeyHolder" should {

    "return an empty key when none has been set" in {
      KeyHolder.getKey() shouldBe ""
    }

    "return the key that was set" in {
      KeyHolder.setKey("brm-key")

      KeyHolder.getKey() shouldBe "brm-key"
    }

    "replace a previously set key" in {
      KeyHolder.setKey("first-key")
      KeyHolder.setKey("second-key")

      KeyHolder.getKey() shouldBe "second-key"
    }

    "publish a key set on one thread to threads that read it later" in {
      KeyHolder.setKey("brm-key")

      val observed = new AtomicReference("")
      val reader   = new Thread(() => observed.set(KeyHolder.getKey()), "key-holder-spec-reader")

      reader.start()
      reader.join(TimeUnit.SECONDS.toMillis(30))

      observed.get() shouldBe "brm-key"
    }

  }

}
