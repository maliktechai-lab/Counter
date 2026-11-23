package com.reeltracker.domain.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Pure date-string utilities.  All methods work with "YYYY-MM-DD" strings in the
 * device's default time zone, matching the [localDate] column in Room.
 */
object DateUtils {

    private const val FORMAT = "yyyy-MM-dd"

    private fun sdf(): SimpleDateFormat =
        SimpleDateFormat(FORMAT, Locale.US).also { it.timeZone = TimeZone.getDefault() }

    /** Returns today's date as "YYYY-MM-DD" in the device local time zone. */
    fun today(nowMs: Long = System.currentTimeMillis()): String =
        sdf().format(Date(nowMs))

    /** Converts an epoch-ms timestamp to a local "YYYY-MM-DD" string. */
    fun toLocalDate(timestampMs: Long): String =
        sdf().format(Date(timestampMs))

    /**
     * Returns the Monday–Sunday ISO week range that contains [referenceDate].
     * Result: Pair(mondayDate, sundayDate), both inclusive.
     */
    fun isoWeekRange(referenceDate: String): Pair<String, String> {
        val cal = parseToCalendar(referenceDate)
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val daysFromMonday = when (dow) {
            Calendar.SUNDAY -> 6
            else            -> dow - Calendar.MONDAY
        }
        val monday = (cal.clone() as Calendar).also { it.add(Calendar.DAY_OF_MONTH, -daysFromMonday) }
        val sunday = (monday.clone() as Calendar).also { it.add(Calendar.DAY_OF_MONTH, 6) }
        return sdf().format(monday.time) to sdf().format(sunday.time)
    }

    /**
     * Returns the first–last day range of the calendar month containing [referenceDate].
     * Result: Pair(firstDay, lastDay), both inclusive.
     */
    fun monthRange(referenceDate: String): Pair<String, String> {
        val cal = parseToCalendar(referenceDate)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val first = sdf().format(cal.time)
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        return first to sdf().format(cal.time)
    }

    /** All dates (inclusive) in the ISO week that contains [referenceDate]. */
    fun isoWeekDates(referenceDate: String = today()): List<String> {
        val (start, _) = isoWeekRange(referenceDate)
        val cal = parseToCalendar(start)
        return (0..6).map {
            val d = sdf().format(cal.time)
            cal.add(Calendar.DAY_OF_MONTH, 1)
            d
        }
    }

    private fun parseToCalendar(date: String): Calendar =
        Calendar.getInstance().also { it.time = sdf().parse(date)!! }
}
