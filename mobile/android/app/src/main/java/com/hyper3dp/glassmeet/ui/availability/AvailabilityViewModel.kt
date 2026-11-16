package com.hyper3dp.glassmeet.ui.availability

import androidx.lifecycle.viewModelScope
import com.hyper3dp.glassmeet.data.AvailabilityException
import com.hyper3dp.glassmeet.data.AvailabilityRepository
import com.hyper3dp.glassmeet.data.Booking
import com.hyper3dp.glassmeet.data.BookingsRepository
import com.hyper3dp.glassmeet.data.Holiday
import com.hyper3dp.glassmeet.data.ProfileRepository
import com.hyper3dp.glassmeet.data.friendly
import com.hyper3dp.glassmeet.ui.components.LoadingViewModel
import com.hyper3dp.glassmeet.util.Format
import com.hyper3dp.glassmeet.util.Snack
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One time range of a weekday; id is null until the insert comes back. */
data class Range(val id: String?, val start: Int, val end: Int)

data class Clash(val booking: Booking, val holiday: String)

data class AvailabilityData(
    val week: Map<Int, List<Range>>,
    val exceptions: List<AvailabilityException>,
    val dailyLimit: Int?,
    val holidaysOn: Boolean,
    val holidays: List<Holiday>,
    val clashes: List<Clash>,
    val zone: ZoneId,
)

class AvailabilityViewModel : LoadingViewModel<AvailabilityData>() {
    init { load() }

    override suspend fun fetch(): AvailabilityData = coroutineScope {
        val rules = async { AvailabilityRepository.rules() }
        val exceptions = async { AvailabilityRepository.exceptions() }
        val holidays = async { AvailabilityRepository.holidays() }
        val bookings = async { runCatching { BookingsRepository.list() }.getOrDefault(emptyList()) }
        val profile = ProfileRepository.load()
        val zone = Format.zone(profile.timezone)
        val week = rules.await().groupBy { it.dayOfWeek }.mapValues { (_, list) ->
            list.mapNotNull { r ->
                val s = Format.minutesOf(r.startTime) ?: return@mapNotNull null
                val e = Format.minutesOf(r.endTime) ?: return@mapNotNull null
                Range(r.id, s, if (e == 0) 1440 else e)
            }.sortedBy { it.start }
        }
        val holidayList = holidays.await()
        AvailabilityData(
            week = week,
            exceptions = exceptions.await(),
            dailyLimit = profile.maxPerDay,
            holidaysOn = profile.holidays == "ae",
            holidays = holidayList,
            clashes = clashes(bookings.await(), holidayList, zone),
            zone = zone,
        )
    }

    private fun clashes(bookings: List<Booking>, holidays: List<Holiday>, zone: ZoneId): List<Clash> {
        val names = holidays.associate { it.date to it.name }
        val now = Instant.now()
        return bookings.filter { !it.isCancelled && !it.isDeclined && it.start.isAfter(now) }
            .mapNotNull { b -> names[b.start.atZone(zone).toLocalDate().toString()]?.let { Clash(b, it) } }
            .sortedBy { it.booking.start }
    }

    private fun setDay(day: Int, ranges: List<Range>) = setData { d -> d.copy(week = d.week + (day to ranges.sortedBy { it.start })) }
    private fun rangesOf(day: Int) = state.value.data?.week?.get(day).orEmpty()

    private fun write(failure: String, work: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                work()
            } catch (e: Exception) {
                Snack.show("$failure ${e.friendly("")}".trim())
                reloadQuietly()
            }
        }
    }

    // ── Weekly hours: every change writes only the rows it touches ──

    fun toggleDay(day: Int, on: Boolean) {
        if (on) {
            write("Could not turn this day on.") {
                val row = AvailabilityRepository.addRule(day, 9 * 60, 17 * 60)
                setDay(day, listOf(Range(row.id, 9 * 60, 17 * 60)))
            }
        } else {
            val old = rangesOf(day)
            setDay(day, emptyList())
            write("Could not turn this day off.") { old.forEach { r -> r.id?.let { AvailabilityRepository.deleteRule(it) } } }
        }
    }

    fun addRange(day: Int) {
        val ranges = rangesOf(day)
        val lastEnd = ranges.maxOfOrNull { it.end } ?: (9 * 60)
        val start = if (ranges.isEmpty()) 9 * 60 else (lastEnd + 60).coerceAtMost(1380)
        val end = (start + 60).coerceAtMost(1440)
        if (start >= end) { Snack.show("No room left in this day."); return }
        write("Could not add the hours.") {
            val row = AvailabilityRepository.addRule(day, start, end)
            setDay(day, rangesOf(day) + Range(row.id, start, end))
        }
    }

    fun updateRange(day: Int, index: Int, start: Int, end: Int) {
        if (end <= start) { Snack.show("The end time must be after the start time."); return }
        val ranges = rangesOf(day)
        val r = ranges.getOrNull(index) ?: return
        setDay(day, ranges.mapIndexed { i, x -> if (i == index) x.copy(start = start, end = end) else x })
        val id = r.id ?: return
        write("Could not save these hours.") { AvailabilityRepository.updateRule(id, start, end) }
    }

    fun removeRange(day: Int, index: Int) {
        val ranges = rangesOf(day)
        val r = ranges.getOrNull(index) ?: return
        setDay(day, ranges.filterIndexed { i, _ -> i != index })
        val id = r.id ?: return
        write("Could not remove these hours.") { AvailabilityRepository.deleteRule(id) }
    }

    // ── Date overrides ──

    fun addException(date: LocalDate, mode: String, start: Int, end: Int, label: String) {
        if (mode != "off" && end <= start) { Snack.show("The end time must be after the start time."); return }
        write("Could not add this date.") {
            val row = AvailabilityRepository.addException(date, mode, start, end, label)
            setData { d -> d.copy(exceptions = (d.exceptions + row).sortedBy { it.date }) }
            Snack.show("Saved ${Format.shortDate(date)}")
        }
    }

    fun deleteException(e: AvailabilityException) {
        setData { d -> d.copy(exceptions = d.exceptions.filterNot { it.id == e.id }) }
        write("Could not remove this date.") { AvailabilityRepository.deleteException(e.id) }
    }

    /** Cancels a meeting that falls on a holiday (pushBooking follows), keeping it listed as cancelled. */
    fun cancelClash(c: Clash) {
        write("Could not cancel this meeting.") {
            val saved = BookingsRepository.cancel(c.booking.id)
            setData { d -> d.copy(clashes = d.clashes.map { if (it.booking.id == saved.id) it.copy(booking = saved) else it }) }
            Snack.show("Meeting cancelled. Send ${saved.name} a new time on WhatsApp.")
        }
    }

    // ── Daily limit and holidays (profile) ──

    fun setDailyLimit(limit: Int?) {
        setData { it.copy(dailyLimit = limit) }
        write("Could not save the daily limit.") { AvailabilityRepository.setDailyLimit(limit) }
    }

    fun setHolidays(on: Boolean) {
        setData { it.copy(holidaysOn = on) }
        write("Could not change the holiday setting.") { AvailabilityRepository.setHolidays(on) }
    }
}
