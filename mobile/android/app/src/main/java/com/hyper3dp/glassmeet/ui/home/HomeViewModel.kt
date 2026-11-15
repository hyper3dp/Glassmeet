package com.hyper3dp.glassmeet.ui.home

import androidx.lifecycle.viewModelScope
import com.hyper3dp.glassmeet.data.Booking
import com.hyper3dp.glassmeet.data.BookingsRepository
import com.hyper3dp.glassmeet.data.EventType
import com.hyper3dp.glassmeet.data.EventTypesRepository
import com.hyper3dp.glassmeet.data.ProfileRepository
import com.hyper3dp.glassmeet.ui.components.LoadingViewModel
import com.hyper3dp.glassmeet.util.Format
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class HomeData(
    val greeting: String,
    val todayCount: Int,
    val pendingCount: Int,
    val upcomingCount: Int,
    val allCount: Int,
    val eventTypeCount: Int,
    val next: Booking?,
    val nextDays: List<Pair<LocalDate, List<Booking>>>,
    val shareEvent: EventType?,
    val colors: Map<String, String?>,
    val zone: ZoneId,
)

class HomeViewModel : LoadingViewModel<HomeData>() {
    init {
        load()
        viewModelScope.launch { BookingsRepository.changes.collect { reloadQuietly() } }
        viewModelScope.launch { EventTypesRepository.version.drop(1).collect { reloadQuietly() } }
        viewModelScope.launch { ProfileRepository.profile.drop(1).collect { reloadQuietly() } }
    }

    override suspend fun fetch(): HomeData {
        val (bookings, events) = BookingsRepository.both({ BookingsRepository.list() }, { EventTypesRepository.list() })
        val profile = ProfileRepository.ensureLoaded()
        val zone = Format.zone(profile.timezone)
        val now = Instant.now()
        val today = LocalDate.now(zone)
        fun day(b: Booking) = b.start.atZone(zone).toLocalDate()

        val live = bookings.filter { !it.isCancelled && !it.isDeclined }
        val upcoming = bookings.filter { it.isUpcoming(now) }.sortedBy { it.start }
        val confirmedUpcoming = upcoming.filter { !it.isPending }
        val weekEnd = today.plusDays(7)
        val nextDays = confirmedUpcoming.filter { day(it) < weekEnd }
            .groupBy { day(it) }.toSortedMap().map { (d, list) -> d to list }

        val hour = LocalTime.now(zone).hour
        val part = when { hour < 12 -> "Good morning"; hour < 18 -> "Good afternoon"; else -> "Good evening" }
        val first = Format.firstName(ProfileRepository.hostName)

        return HomeData(
            greeting = if (first.isEmpty()) part else "$part, $first",
            todayCount = live.count { day(it) == today },
            pendingCount = bookings.count { it.isPending },
            upcomingCount = upcoming.size,
            allCount = bookings.size,
            eventTypeCount = events.size,
            next = confirmedUpcoming.firstOrNull(),
            nextDays = nextDays,
            shareEvent = events.filter { it.active }.maxByOrNull { it.createdDate.orEmpty() },
            colors = events.associate { it.id to it.color },
            zone = zone,
        )
    }
}
