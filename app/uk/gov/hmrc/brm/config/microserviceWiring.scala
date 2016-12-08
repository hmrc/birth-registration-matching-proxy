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

package uk.gov.hmrc.brm.config

import play.api.libs.json.JsValue
import uk.gov.hmrc.brm.utils.BrmLogger._
import uk.gov.hmrc.play.audit.http.config.LoadAuditingConfig
import uk.gov.hmrc.play.audit.http.connector.AuditEventFailureKeys._
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult, LoggerProvider}
import uk.gov.hmrc.play.config.{AppName, RunMode}
import uk.gov.hmrc.play.http.HttpResponse
import uk.gov.hmrc.play.http.hooks.HttpHook
import uk.gov.hmrc.play.http.logging.LoggingDetails
import uk.gov.hmrc.play.http.ws._
import scala.util.control.Breaks._

import scala.concurrent.Future

object WSHttp extends WSGet with WSPut with WSPost with WSDelete with WSPatch with AppName {
  override val hooks: Seq[HttpHook] = NoneRequired
}

object MicroserviceAuditConnector extends AuditConnector with RunMode with BRMResultHandler {
  override lazy val auditingConfig = LoadAuditingConfig(s"auditing")
}

trait BRMResultHandler extends uk.gov.hmrc.play.audit.http.connector.ResultHandler {

  this: LoggerProvider =>
  import scala.concurrent.ExecutionContext.Implicits.global
  override protected def handleResult(resultF: Future[HttpResponse], body: JsValue)(implicit ld: LoggingDetails): Future[HttpResponse] = {
    resultF
      .recoverWith {
        case t =>
          println("body0......... " )
          var message = ""
          if (isContainBlockedWord(body)) {
            message = makeFailureMessageWithoutBody(body)

          } else {
            message = makeFailureMessage(body)
          }
          println("body01......... " + message)
        logError(message, t)
        Future.failed(AuditResult.Failure(message, Some(t)))
      }
      .map { response =>
        checkResponse(body, response) match {
          case Some(error) =>
            println("body1......... " + error)
            logError(error)
            throw AuditResult.Failure(error)
          case None => response
        }
      }
  }

  private def isContainBlockedWord(body: JsValue) : Boolean = {
    var  returnValue: Boolean = false
    var stringValue = body.toString()
   // val noAuditWordList = Seq("child", "subjects", "givenname")
    val noAuditWordList =  GROConnectorConfiguration.blockedBodyWords.get
    breakable {
      for (notAllowedworld <- noAuditWordList) {
        var isContains = stringValue.toLowerCase.contains(notAllowedworld.toLowerCase())
        if (isContains) {
          returnValue = true
          scala.util.control.Breaks.break();
        }
      }
    }
    info("BRMResultHandler", "isContainBlockedWord",s" isContainBlockedWord  ${returnValue}")
    returnValue
  }

 protected def makeFailureMessageWithoutBody(body: JsValue): String = s"$LoggingAuditRequestFailureKey :"
}
