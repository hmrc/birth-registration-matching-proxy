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

package uk.gov.hmrc.brm.utils

import org.mockito.ArgumentCaptor
import org.mockito.Mockito._
import org.slf4j.Logger
import uk.gov.hmrc.brm.TestFixture

class BrmLoggerSpec extends TestFixture {

  val mockLogger: Logger = mock[org.slf4j.Logger]

  object MockBRMLogger extends BrmLogger(mockLogger)

  before {
    reset(mockLogger)

  }

  "BrmLogger" should {
    "info call Logger info" in {
      KeyHolder.setKey("somekey")
      MockBRMLogger.info(this, "methodName", "message")
      val argumentCapture: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      verify(mockLogger, times(1)).info(argumentCapture.capture)
      argumentCapture.getValue.contains("methodName") shouldBe true
      argumentCapture.getValue.contains("message")    shouldBe true
      argumentCapture.getValue.contains("somekey")    shouldBe true
    }

    "warn call Logger warn" in {
      MockBRMLogger.warn(this, "methodName", "message")
      val argumentCapture: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      verify(mockLogger, times(1)).warn(argumentCapture.capture)

      argumentCapture.getValue.contains("methodName") shouldBe true
      argumentCapture.getValue.contains("message")    shouldBe true
      argumentCapture.getValue.contains("somekey")    shouldBe true
    }

    "debug call Logger debug" in {
      MockBRMLogger.debug(this, "methodName", "message")
      val argumentCapture: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      verify(mockLogger, times(1)).debug(argumentCapture.capture)

      argumentCapture.getValue.contains("methodName") shouldBe true
      argumentCapture.getValue.contains("message")    shouldBe true
      argumentCapture.getValue.contains("somekey")    shouldBe true
    }

    "error call Logger error" in {
      MockBRMLogger.error(this, "methodNameForError", "errorMessage")
      val argumentCapture: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      verify(mockLogger, times(1)).error(argumentCapture.capture)

      argumentCapture.getValue.contains("methodNameForError") shouldBe true
      argumentCapture.getValue.contains("errorMessage")       shouldBe true
      argumentCapture.getValue.contains("somekey")            shouldBe true
    }

  }
}
