package com.hyper3dp.glassmeet.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Dates, times and names as the screens show them. Times are in the host's zone. */
object Format {
    private val time: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val shortDay: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    private val shortDayYear: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())
    private val longDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault())
    private val dateTime: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

    fun zone(id: String?): ZoneId = runCatching { ZoneId.of(id.orEmpty()) }.getOrDefault(ZoneId.systemDefault())

    fun duration(minutes: Int): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} hr"
        else -> "${minutes / 60} hr ${minutes % 60} min"
    }

    fun time(instant: Instant, zone: ZoneId): String = time.format(instant.atZone(zone))
    fun timeRange(start: Instant, end: Instant, zone: ZoneId) = "${time(start, zone)} – ${time(end, zone)}"
    fun longDate(instant: Instant, zone: ZoneId): String = longDate.format(instant.atZone(zone))
    fun longDate(date: LocalDate): String = longDate.format(date)
    fun dateTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String = dateTime.format(instant.atZone(zone))

    /** "Today", "Tomorrow", "Wed, Nov 18" (with the year when it isn't this year) */
    fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> if (date.year == today.year) shortDay.format(date) else shortDayYear.format(date)
    }

    /** "Today at 2:30 PM" */
    fun dayAndTime(instant: Instant, zone: ZoneId): String {
        val date = instant.atZone(zone).toLocalDate()
        return "${dayLabel(date, LocalDate.now(zone))} at ${time(instant, zone)}"
    }

    fun shortDate(date: LocalDate): String =
        if (date.year == LocalDate.now().year) shortDay.format(date) else shortDayYear.format(date)

    fun initials(name: String): String =
        name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

    fun firstName(name: String?): String = name.orEmpty().trim().split(Regex("\\s+")).firstOrNull().orEmpty()

    // ── Clock times stored as "HH:MM" or "HH:MM:SS"; 24:00 is midnight at the end of the day ──

    fun minutesOf(value: String?): Int? {
        val parts = value?.trim()?.split(":") ?: return null
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return (h * 60 + m).takeIf { it in 0..1440 }
    }

    /** "09:00" for the database: always Latin digits (Locale.ROOT), whatever the phone's language. */
    fun hhmm(minutes: Int): String = String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60)

    fun clock(minutes: Int): String =
        if (minutes >= 1440) "12:00 AM (midnight)"
        else time.format(LocalTime.of(minutes / 60, minutes % 60))

    fun clock(value: String?): String = minutesOf(value)?.let { clock(it) } ?: value.orEmpty()

    /** Digits only, as ASCII 0-9 (an Arabic-Indic "٠٥٠" becomes "050"), for links and the database. */
    fun digits(value: String?): String = value.orEmpty().filter { it.isDigit() }.map { '0' + Character.digit(it, 10) }.joinToString("")

    /** Typed numbers and phone numbers with any script's digits turned into ASCII digits. */
    fun asciiDigits(value: String): String = value.map { if (it.isDigit()) '0' + Character.digit(it, 10) else it }.joinToString("")
}
