/*
 * Copyright 2018 HM Revenue & Customs
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
import uk.gov.hmrc.brm.config.GROConnectorConfiguration._
import uk.gov.hmrc.brm.utils.BrmLogger._
import uk.gov.hmrc.play.audit.http.config.LoadAuditingConfig
import uk.gov.hmrc.play.audit.http.connector.AuditEventFailureKeys._
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult, LoggerProvider}
import uk.gov.hmrc.play.config.RunMode
import uk.gov.hmrc.play.http.HttpResponse
import uk.gov.hmrc.play.http.logging.LoggingDetails

import scala.concurrent.Future

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

          def message : String = if(isContainBlockedWord(body.toString()) && disableAuditingLogging) {
            makeFailureMessageWithoutBody()
          } else {
            makeFailureMessage(body)
          }

        logError(message, t)
        Future.failed(AuditResult.Failure(message, Some(t)))
      }
      .map { response =>
        checkResponse(body, response) match {
          case Some(error) =>

            def message : String = if(isContainBlockedWord(error) && disableAuditingLogging) {
              makeFailureMessageWithoutBody()
            } else{
              error
            }
            logError(message)
            throw AuditResult.Failure(message)
          case None => response
        }
      }
  }

  private def isContainBlockedWord(body: String) : Boolean = {
    val containsWord : Boolean = {
      val bodyString = body.toLowerCase
      val blackList = blockedBodyWords.getOrElse(Seq[String]())
      debug("BRMResultHandler", "blackList", s"$blackList")
      val excludedWords = blackList.filter(excluded => {
        val trimmedExcluded = excluded.trim.toLowerCase
        trimmedExcluded.nonEmpty && bodyString.contains(trimmedExcluded)
      })

      excludedWords.nonEmpty
    }
    info("BRMResultHandler", "isContainBlockedWord",s" isContainBlockedWord  $containsWord")
    containsWord
  }

  protected def makeFailureMessageWithoutBody(): String = s"$LoggingAuditRequestFailureKey : audit item : body removed, contains sensitive data."
}
