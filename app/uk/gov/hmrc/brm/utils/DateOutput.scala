/*
 * Copyright 2024 HM Revenue & Customs
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

import java.time.Period

case class DateOutput(years: Int = 0, months: Int = 0, weeks: Int = 0, days: Int = 0)

object DateOutput {

  def formatDurations(period: Period): String = {

    val dateOutput = getDurations(period)

    def formatTimeField(value: Int, durationType: String): String = value match {
      case 0 => ""
      case 1 | -1 => s"$value $durationType"
      case _ => s"$value ${durationType}s" // plural
    }

    val textWithSingularOrPlural = Seq(
      formatTimeField(dateOutput.years, "year"),
      formatTimeField(dateOutput.months, "month"),
      formatTimeField(dateOutput.weeks, "week"),
      formatTimeField(dateOutput.days, "day")
    )

    // start from days moving towards years to format the output string with commas correctly
    val output = textWithSingularOrPlural.reduceRight { (prev, cur) =>
      if (prev.nonEmpty) {
        val textWithConditionalComma = if (cur.nonEmpty) ", " + cur else cur
        prev + textWithConditionalComma
      } else {
        cur
      }
    }

    if (output.isEmpty) "0 days" else output
  }

  // Count the number of days, weeks, months, and years of a given period
  def getDurations(initialPeriod: Period): DateOutput =
    (getYears andThen
      getMonths andThen
      getWeeks andThen
      getDays)(initialPeriod -> DateOutput())._2

  private def getYears: PartialFunction[(Period, DateOutput), (Period, DateOutput)] = {
    case (period, dateOutput) if period.getYears != 0 =>
      val years = period.getYears
      (period.minusYears(years), dateOutput.copy(years = years))
    case (period, dateOutput) => (period, dateOutput)
  }

  private def getMonths: PartialFunction[(Period, DateOutput), (Period, DateOutput)] = {
    case (period, dateOutput) if period.getMonths != 0 =>
      val months = period.getMonths
      (period.minusMonths(months), dateOutput.copy(months = months))
    case (period, dateOutput) => (period, dateOutput)
  }

  private def getWeeks: PartialFunction[(Period, DateOutput), (Period, DateOutput)] = {
    case (period, dateOutput) if period.getDays / 7 != 0 =>
      val numberOfWeeksInDays = (period.getDays / 7) * 7
      (period.minusDays(numberOfWeeksInDays), dateOutput.copy(weeks = numberOfWeeksInDays / 7))
    case (period, dateOutput) => (period, dateOutput)
  }

  private def getDays: PartialFunction[(Period, DateOutput), (Period, DateOutput)] = {
    case (period, dateOutput) if period.getDays != 0 =>
      val days = period.getDays
      (period.minusDays(days), dateOutput.copy(days = days))
    case (period, dateOutput) => (period, dateOutput)
  }

}
