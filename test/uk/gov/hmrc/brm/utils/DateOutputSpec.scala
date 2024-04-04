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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.time.Period

class DateOutputSpec extends AnyWordSpecLike with Matchers {

  val OneYear = Period.ofYears(1)
  val OneMonth = Period.ofMonths(1)
  val OneWeek = Period.ofWeeks(1)
  val OneDay = Period.ofDays(1)
  val TwelveYearsSixMonthsTwoWeeksOneDay = Period.ofWeeks(2).plusYears(12).plusMonths(6).plusDays(1)

  "DateOutput.getDurations" should {

    "count the correct time in years" in {
      DateOutput.getDurations(OneYear) shouldBe  DateOutput(years = 1)
    }

    "count the correct time in months" in {
      DateOutput.getDurations(OneMonth) shouldBe DateOutput(months = 1)
    }

    "count the correct time in weeks" in {
      DateOutput.getDurations(OneWeek) shouldBe DateOutput(weeks = 1)
    }

    "count the correct time in days" in {
      DateOutput.getDurations(OneDay) shouldBe DateOutput(days = 1)
    }

    "count the correct time in years, months, and days" in {
      DateOutput.getDurations(TwelveYearsSixMonthsTwoWeeksOneDay) shouldBe DateOutput(years = 12, months = 6, weeks = 2, days = 1)
    }

  }

  "DateOutput.formatDurations" should {

    "format the correct time in years and months" in {
      DateOutput.formatDurations(Period.ofYears(1).plusMonths(1)) shouldBe "1 year, 1 month"
    }

    "format the correct time in years and weeks" in {
      DateOutput.formatDurations(Period.ofWeeks(1).plusYears(2)) shouldBe "2 years, 1 week"
    }

    "format the correct time in years and days" in {
      DateOutput.formatDurations(Period.ofYears(1).plusDays(1)) shouldBe "1 year, 1 day"
    }

    "format the correct time in years, months, and weeks" in {
      DateOutput.formatDurations(Period.ofWeeks(1).plusYears(1).plusDays(2)) shouldBe "1 year, 1 week, 2 days"
    }

    "format the correct time period in years, months, and days" in {
      DateOutput.formatDurations(Period.ofYears(1).plusMonths(2).plusDays(1)) shouldBe "1 year, 2 months, 1 day"
    }

    "format the correct time in years, months, weeks, and days" in {
      DateOutput.formatDurations(TwelveYearsSixMonthsTwoWeeksOneDay) shouldBe "12 years, 6 months, 2 weeks, 1 day"
    }

    "format the correct time period in months and weeks" in {
      DateOutput.formatDurations(Period.ofWeeks(2).plusMonths(1)) shouldBe "1 month, 2 weeks"
    }

    "format the correct time in months and days" in {
      DateOutput.formatDurations(Period.ofMonths(1).plusDays(2)) shouldBe "1 month, 2 days"
    }

    "format the correct time in weeks and days" in {
      DateOutput.formatDurations(Period.ofWeeks(1).plusDays(1)) shouldBe "1 week, 1 day"
    }

    "format the correct time for a period with no duration" in {
      DateOutput.formatDurations(Period.ofDays(0)) shouldBe "0 days"
    }

  }

  "DateOutput.formatDurations singular" should {

    "format the correct time in years" in {
      DateOutput.formatDurations(OneYear) shouldBe "1 year"
    }

    "format the correct time in months" in {
      DateOutput.formatDurations(OneMonth) shouldBe "1 month"
    }

    "format the correct time in weeks" in {
      DateOutput.formatDurations(OneWeek) shouldBe "1 week"
    }

    "format the correct time in days" in {
      DateOutput.formatDurations(OneDay) shouldBe "1 day"
    }

  }

  "DateOutput.formatDurations plural" should {

    "format the correct time in years" in {
      DateOutput.formatDurations(Period.ofYears(2)) shouldBe "2 years"
    }

    "format the correct time in months" in {
      DateOutput.formatDurations(Period.ofMonths(2)) shouldBe "2 months"
    }

    "format the correct time in weeks" in {
      DateOutput.formatDurations(Period.ofWeeks(2)) shouldBe "2 weeks"
    }

    "format the correct time in days" in {
      DateOutput.formatDurations(Period.ofDays(2)) shouldBe "2 days"
    }

  }

}
