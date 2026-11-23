package com.reeltracker.domain.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.TimeZone

class DateUtilsTest {

    @Test
    fun `today returns YYYY-MM-DD format`() {
        val result = DateUtils.today(1_700_000_000_000L) // Nov 14 2023 UTC
        assertTrue(result.matches(Regex("\\d{4}-\\d{2}-\\d{2}")), "Format mismatch: $result")
    }

    @Test
    fun `toLocalDate converts timestamp to date string`() {
        // 2024-01-15 12:00:00 UTC = 1705320000000
        // Result depends on local TZ; just check format
        val result = DateUtils.toLocalDate(1_705_320_000_000L)
        assertTrue(result.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }

    @Test
    fun `isoWeekRange returns Monday and Sunday around a Wednesday`() {
        // 2024-01-17 is a Wednesday
        val (monday, sunday) = DateUtils.isoWeekRange("2024-01-17")
        assertEquals("2024-01-15", monday) // that Monday
        assertEquals("2024-01-21", sunday) // that Sunday
    }

    @Test
    fun `isoWeekRange works when input is Monday`() {
        val (monday, sunday) = DateUtils.isoWeekRange("2024-01-15")
        assertEquals("2024-01-15", monday)
        assertEquals("2024-01-21", sunday)
    }

    @Test
    fun `isoWeekRange works when input is Sunday`() {
        val (monday, sunday) = DateUtils.isoWeekRange("2024-01-21")
        assertEquals("2024-01-15", monday)
        assertEquals("2024-01-21", sunday)
    }

    @Test
    fun `monthRange returns first and last day of month`() {
        val (first, last) = DateUtils.monthRange("2024-02-15")
        assertEquals("2024-02-01", first)
        assertEquals("2024-02-29", last) // 2024 is a leap year
    }

    @Test
    fun `monthRange handles January correctly`() {
        val (first, last) = DateUtils.monthRange("2024-01-01")
        assertEquals("2024-01-01", first)
        assertEquals("2024-01-31", last)
    }

    @Test
    fun `isoWeekDates returns exactly 7 dates`() {
        val dates = DateUtils.isoWeekDates("2024-01-17")
        assertEquals(7, dates.size)
    }

    @Test
    fun `isoWeekDates starts on Monday`() {
        val dates = DateUtils.isoWeekDates("2024-01-17")
        assertEquals("2024-01-15", dates.first())
        assertEquals("2024-01-21", dates.last())
    }

    @Test
    fun `isoWeekDates dates are consecutive`() {
        val dates = DateUtils.isoWeekDates("2024-01-17")
        for (i in 1 until dates.size) {
            val prev = dates[i - 1]
            val curr = dates[i]
            assertTrue(curr > prev, "Dates not in order: $prev, $curr")
        }
    }
}
