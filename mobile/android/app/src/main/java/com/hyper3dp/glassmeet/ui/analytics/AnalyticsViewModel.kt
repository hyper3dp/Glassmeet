package com.hyper3dp.glassmeet.ui.analytics

import com.hyper3dp.glassmeet.data.BookingsRepository
import com.hyper3dp.glassmeet.data.EventTypesRepository
import com.hyper3dp.glassmeet.data.ProfileRepository
import com.hyper3dp.glassmeet.ui.components.LoadingViewModel
import com.hyper3dp.glassmeet.util.Format
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class MonthBar(val label: String, val count: Int)

data class AnalyticsData(
    val total: String,
    val confirmed: Int,
    val cancelled: Int,
    val averageLength: String,
    val popularEvent: String,
    val popularTime: String,
    val attendees: Int,
    val eventTypes: Int,
    val months: List<MonthBar>,
    /** 7 rows (Sun..Sat) x 12 hours (8am-8pm) */
    val heat: List<List<Int>>,
)

/** Same numbers as the web's Analytics.jsx; times are in the host's zone. */
class AnalyticsViewModel : LoadingViewModel<AnalyticsData>() {
    init { load() }

    override suspend fun fetch(): AnalyticsData {
        val (bookings, events) = BookingsRepository.both({ BookingsRepository.forAnalytics() }, { EventTypesRepository.list() })
        val zone = Format.zone(ProfileRepository.ensureLoaded().timezone)
        // cancelled and declined bookings don't count towards popularity, length or attendees
        val held = bookings.filter { !it.isCancelled }

        val popularEvent = held.groupingBy { it.eventName ?: "" }.eachCount().filterKeys { it.isNotBlank() }
            .maxByOrNull { it.value }?.key ?: "—"
        val hours = held.groupingBy { it.start.atZone(zone).hour }.eachCount().entries.sortedByDescending { it.value }
        val top = hours.getOrNull(0)
        val popularHour = if (top != null && top.value > 1 && (hours.size < 2 || top.value > hours[1].value)) top.key else null
        val timed = held.filter { it.endTime != null }
        val avg = if (timed.isEmpty()) 0 else (timed.sumOf { it.minutes } / timed.size).toInt()

        val heat = List(7) { MutableList(12) { 0 } }
        held.forEach { b ->
            val t = b.start.atZone(zone)
            val day = t.dayOfWeek.value % 7 // Sunday = 0
            if (t.hour in 8..19) heat[day][t.hour - 8]++
        }
        val monthFormat = DateTimeFormatter.ofPattern("MMM yy", Locale.getDefault())
        val months = held.groupingBy { YearMonth.from(it.start.atZone(zone)) }.eachCount().toSortedMap()
            .map { (m, n) -> MonthBar(monthFormat.format(m), n) }

        return AnalyticsData(
            total = if (bookings.size >= 1000) "1000+" else bookings.size.toString(),
            confirmed = bookings.count { it.status == "confirmed" && it.approvalStatus != "pending" },
            cancelled = bookings.count { it.isCancelled },
            averageLength = if (avg == 0) "—" else Format.duration(avg),
            popularEvent = popularEvent,
            popularTime = popularHour?.let { Format.clock(it * 60) } ?: "Not enough data",
            attendees = held.sumOf { it.attendeeCount ?: 1 },
            eventTypes = events.size,
            months = months,
            heat = heat,
        )
    }}
