package com.hyper3dp.glassmeet.ui.meetings

import androidx.lifecycle.viewModelScope
import com.hyper3dp.glassmeet.data.Booking
import com.hyper3dp.glassmeet.data.BookingsRepository
import com.hyper3dp.glassmeet.data.EventTypesRepository
import com.hyper3dp.glassmeet.data.ProfileRepository
import com.hyper3dp.glassmeet.ui.components.LoadingViewModel
import com.hyper3dp.glassmeet.ui.navigation.NavRequests
import com.hyper3dp.glassmeet.util.Format
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class MeetingFilter(val key: String, val label: String) {
    Upcoming("upcoming", "Upcoming"),
    Requests("pending", "Requests"),
    Past("past", "Past"),
    Cancelled("cancelled", "Cancelled"),
}

data class MeetingsData(val bookings: List<Booking>, val colors: Map<String, String?>, val zone: ZoneId)

class MeetingsViewModel : LoadingViewModel<MeetingsData>() {
    private val _filter = MutableStateFlow(MeetingFilter.Upcoming)
    val filter: StateFlow<MeetingFilter> = _filter.asStateFlow()
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            NavRequests.meetingsFilter.collect { key ->
                if (key != null) {
                    MeetingFilter.entries.firstOrNull { it.key == key }?.let { _filter.value = it }
                    _query.value = ""
                    NavRequests.meetingsFilter.value = null
                }
            }
        }
        viewModelScope.launch {
            BookingsRepository.changes.collect { changed ->
                setData { d -> d.copy(bookings = d.bookings.map { if (it.id == changed.id) changed else it }) }
            }
        }
    }

    fun setFilter(f: MeetingFilter) { _filter.value = f }
    fun setQuery(q: String) { _query.value = q }

    /** Approve a request from the list; the list updates through BookingsRepository.changes. */
    fun approve(id: String, done: (String?) -> Unit) {
        viewModelScope.launch {
            val error = runCatching { BookingsRepository.approve(id) }.exceptionOrNull()
            done(error?.let { "This request couldn't be approved. ${it.message.orEmpty()}".trim() })
        }
    }

    override suspend fun fetch(): MeetingsData {
        val (bookings, events) = BookingsRepository.both({ BookingsRepository.list() }, { EventTypesRepository.list() })
        val profile = ProfileRepository.ensureLoaded()
        return MeetingsData(bookings, events.associate { it.id to it.color }, Format.zone(profile.timezone))
    }

    companion object {
        fun requestsCount(list: List<Booking>) = list.count { it.isPending }

        /** The list for a filter, or search results across every filter (name, email, event, phone digits). */
        fun visible(list: List<Booking>, filter: MeetingFilter, query: String, now: Instant = Instant.now()): List<Booking> {
            val q = query.trim().lowercase()
            if (q.isNotEmpty()) {
                val digits = q.filter { it.isDigit() }
                return list.filter { b ->
                    listOf(b.guestName, b.guestEmail, b.eventName).any { it?.lowercase()?.contains(q) == true } ||
                        (digits.length >= 3 && Format.digits(b.guestPhone).contains(digits))
                }.sortedByDescending { it.start }
            }
            return when (filter) {
                MeetingFilter.Upcoming -> list.filter { it.isUpcoming(now) }.sortedBy { it.start }
                MeetingFilter.Requests -> list.filter { it.isPending }.sortedBy { it.start }
                MeetingFilter.Past -> list.filter { !it.isUpcoming(now) && !it.isCancelled }.sortedByDescending { it.start }
                MeetingFilter.Cancelled -> list.filter { it.isCancelled }.sortedByDescending { it.start }
            }
        }

        fun groups(list: List<Booking>, zone: ZoneId): List<Pair<LocalDate, List<Booking>>> {
            val out = mutableListOf<Pair<LocalDate, MutableList<Booking>>>()
            list.forEach { b ->
                val day = b.start.atZone(zone).toLocalDate()
                val last = out.lastOrNull()
                if (last != null && last.first == day) last.second.add(b) else out.add(day to mutableListOf(b))
            }
            return out
        }
    }
}
