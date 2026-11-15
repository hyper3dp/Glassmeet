package com.hyper3dp.glassmeet.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/** Meetings (bookings) the host owns (web: bookings.jsx, dashboard.jsx). */
object BookingsRepository {
    private val _changes = MutableSharedFlow<Booking>(extraBufferCapacity = 8)

    /** Emits a booking each time this app changes one, so open lists update without reloading. */
    val changes: SharedFlow<Booking> = _changes.asSharedFlow()

    /** Up to 1000 meetings, newest first; cancellations older than two days are left out. */
    suspend fun list(): List<Booking> {
        val now = Instant.now()
        return supabase.from("bookings").select {
            filter { eq("host_id", ownerId()) }
            order("start_time", Order.DESCENDING)
            limit(1000)
        }.decodeList<Booking>().filterNot { it.isExpiredCancellation(now) }
    }

    /** For Analytics: the newest 1000 bookings, cancelled ones included (web: Analytics.jsx). */
    suspend fun forAnalytics(): List<Booking> =
        supabase.from("bookings").select(
            io.github.jan.supabase.postgrest.query.Columns.list(
                "id", "status", "approval_status", "event_name", "start_time", "end_time", "attendee_count",
            ),
        ) {
            filter { eq("host_id", ownerId()) }
            order("created_date", Order.DESCENDING)
            limit(1000)
        }.decodeList()

    suspend fun get(id: String): Booking? =
        supabase.from("bookings").select { filter { eq("id", id) } }.decodeList<Booking>().firstOrNull()

    private suspend fun update(id: String, values: JsonObject): Booking {
        val saved = updateRows("bookings", values) { eq("id", id) }.decodeList<Booking>().firstOrNull()
            ?: throw Exception("This meeting may have been deleted. Pull to refresh.")
        _changes.tryEmit(saved)
        return saved
    }

    suspend fun approve(id: String) = changeStatus(id, buildJsonObject { put("approval_status", "approved") })
    suspend fun cancel(id: String) = changeStatus(id, buildJsonObject { put("status", "cancelled") })

    /** A declined request frees its time again. */
    suspend fun decline(id: String) = changeStatus(id, buildJsonObject {
        put("approval_status", "declined")
        put("status", "cancelled")
    })

    private suspend fun changeStatus(id: String, values: JsonObject): Booking {
        val saved = update(id, values)
        CalendarRepository.pushBooking(id)
        return saved
    }

    suspend fun saveNotes(id: String, notes: String) = update(id, buildJsonObject { put("notes", notes.ifBlank { null }) })

    /** A changed meeting link is copied to the host's calendars too. */
    suspend fun saveMeetingUrl(id: String, url: String): Booking {
        val saved = update(id, buildJsonObject { put("meeting_url", url.trim().ifBlank { null }) })
        CalendarRepository.pushBooking(id)
        return saved
    }

    /** Small parallel helper for screens that load several things at once. */
    suspend fun <A, B> both(a: suspend () -> A, b: suspend () -> B): Pair<A, B> = coroutineScope {
        val x = async { a() }
        val y = async { b() }
        awaitAll(x, y)
        x.await() to y.await()
    }
}
