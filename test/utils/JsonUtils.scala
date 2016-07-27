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

import play.api.Logger
import play.api.libs.json.{JsValue, Json}

import scala.io.Source

/**
  * Created by chrisianson on 08/07/16.
  */
object JsonUtils {

  def getJsonFromFile[A](filename : A) = loadResource(s"/resources/$filename.json")

  def loadResource(path : String) = {

    def resourceAsString(resourcePath: String): Option[String] = {
      Option(getClass.getResourceAsStream(resourcePath)) map { is =>
        Source.fromInputStream(is).getLines.mkString("\n")
      }
    }

    Logger.debug(s"Loading JSON: $path")

    resourceAsString(path) match {
      case Some(x) =>
        Logger.debug(s"source: $x")
        val json : JsValue = Json.parse(x)
        json
      case _ =>
        throw new RuntimeException("cannot load json")
    }
  }
}
