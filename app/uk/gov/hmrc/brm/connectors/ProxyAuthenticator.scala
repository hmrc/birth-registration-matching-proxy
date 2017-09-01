/*
 * Copyright 2017 HM Revenue & Customs
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

import play.mvc.Http.HeaderNames
import uk.co.bigbeeconsultants.http.util.Base64
import uk.gov.hmrc.brm.config.ProxyConfiguration
import uk.gov.hmrc.brm.utils.BrmLogger

/**
  * Created by mew on 01/09/2017.
  */

object ProxyAuthenticator extends ProxyAuthenticator {
  override protected val username: String = ProxyConfiguration.username
  override protected val password: String = ProxyConfiguration.password
  override protected val required: Boolean = ProxyConfiguration.required
}

trait ProxyAuthenticator {

  protected val username : String
  protected val password : String
  protected val required : Boolean

  def setProxyAuthHeader() : Map[String, String] = {
    if (required) {
      BrmLogger.info("ProxyAuthenticator", "setProxyAuthHeader", "setting header")
      val encoded: String = new String(Base64.encodeBytes(s"$username:$password".getBytes))
      Map(HeaderNames.PROXY_AUTHORIZATION -> s"Basic $encoded")
    } else {
      BrmLogger.info("ProxyAuthenticator", "setProxyAuthHeader", "not setting header")
      Map()
    }
  }

}
