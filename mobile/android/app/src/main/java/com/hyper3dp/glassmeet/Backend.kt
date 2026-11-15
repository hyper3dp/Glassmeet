package com.hyper3dp.glassmeet

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.OffsetDateTime

/** The same Supabase project and publishable key as the web app. Row Level Security protects the data. */
object Backend {
    const val URL = "https://sjxraemrdpzleipfmwun.supabase.co"
    const val KEY = "sb_publishable_0T8UhXmO8GUjyZa67yIU5w_RZwkPa4O"
    /** Guests book on the web: /book/<event type id> */
    const val WEB = "https://glassmeet.run.place"
    fun bookingUrl(eventTypeId: String) = "$WEB/book/$eventTypeId"
}

val supabase = createSupabaseClient(Backend.URL, Backend.KEY) {
    defaultSerializer = KotlinXSerializer(Json { ignoreUnknownKeys = true })
    install(Auth)
    install(Postgrest)
}

// ── Rows: the columns this app reads and writes (supabase/migrations in the web app) ──

@Serializable
data class Profile(val id: String, val email: String? = null, @SerialName("full_name") val fullName: String? = null)

@Serializable
data class EventType(
    val id: String,
    val name: String,
    val description: String? = null,
    val duration: Int = 30,
    val location: String? = null,
    @SerialName("meeting_url") val meetingUrl: String? = null,
    val active: Boolean = true,
    @SerialName("requires_approval") val requiresApproval: Boolean = false,
    val color: String? = null,
)

@Serializable
data class EventTypeInput(
    val name: String,
    val description: String?,
    val duration: Int,
    val location: String,
    @SerialName("meeting_url") val meetingUrl: String?,
    @SerialName("requires_approval") val requiresApproval: Boolean,
)

@Serializable
data class Booking(
    val id: String,
    @SerialName("event_type_id") val eventTypeId: String? = null,
    @SerialName("event_name") val eventName: String? = null,
    @SerialName("guest_name") val guestName: String,
    @SerialName("guest_email") val guestEmail: String,
    @SerialName("guest_phone") val guestPhone: String? = null,
    @SerialName("guest_notes") val guestNotes: String? = null,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    val timezone: String? = null,
    @SerialName("meeting_url") val meetingUrl: String? = null,
    val status: String = "confirmed",
    @SerialName("approval_status") val approvalStatus: String = "approved",
) {
    val start: Instant get() = parseTime(startTime)
    val end: Instant get() = parseTime(endTime)
    val isCancelled get() = status == "cancelled"
    val isDeclined get() = approvalStatus == "declined"
    val isPending get() = approvalStatus == "pending" && !isCancelled
    fun isOver(now: Instant = Instant.now()) = end.isBefore(now)
    fun isUpcoming(now: Instant = Instant.now()) = status == "confirmed" && approvalStatus == "approved" && !isOver(now)
}

private fun parseTime(value: String): Instant =
    runCatching { OffsetDateTime.parse(value).toInstant() }.getOrElse { runCatching { Instant.parse(value) }.getOrDefault(Instant.EPOCH) }

@Serializable
data class AvailabilityRule(
    val id: String,
    @SerialName("day_of_week") val dayOfWeek: Int,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
)

@Serializable
private data class NewRule(
    @SerialName("day_of_week") val dayOfWeek: Int,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
)

@Serializable
data class WhatsAppSettings(
    val id: String,
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("confirmation_template") val confirmationTemplate: String? = null,
)

/** What saving sends: no id, so the database keeps (or makes) its own. */
@Serializable
private data class WhatsAppRow(
    @SerialName("created_by_id") val createdById: String,
    @SerialName("phone_number") val phoneNumber: String?,
    @SerialName("confirmation_template") val confirmationTemplate: String?,
)

// ── Requests ──

object Api {
    private fun owner(): String = supabase.auth.currentUserOrNull()?.id ?: error("You are signed out.")

    suspend fun profile(): Profile? =
        supabase.from("profiles").select { filter { eq("id", owner()) } }.decodeList<Profile>().firstOrNull()

    suspend fun updateName(name: String) {
        supabase.from("profiles").update({ set("full_name", name) }) { filter { eq("id", owner()) } }
    }

    suspend fun eventTypes(): List<EventType> =
        supabase.from("event_types").select {
            filter { eq("created_by_id", owner()) }
            order("created_date", Order.ASCENDING)
        }.decodeList()

    suspend fun eventType(id: String): EventType =
        supabase.from("event_types").select { filter { eq("id", id) } }.decodeSingle()

    suspend fun createEventType(input: EventTypeInput) {
        supabase.from("event_types").insert(input)
    }

    suspend fun updateEventType(id: String, input: EventTypeInput) {
        supabase.from("event_types").update(input) { filter { eq("id", id) } }
    }

    suspend fun setActive(id: String, active: Boolean) {
        supabase.from("event_types").update({ set("active", active) }) { filter { eq("id", id) } }
    }

    suspend fun deleteEventType(id: String) {
        supabase.from("event_types").delete { filter { eq("id", id) } }
    }

    suspend fun bookings(): List<Booking> =
        supabase.from("bookings").select {
            filter { eq("host_id", owner()) }
            order("start_time", Order.ASCENDING)
        }.decodeList()

    suspend fun booking(id: String): Booking =
        supabase.from("bookings").select { filter { eq("id", id) } }.decodeSingle()

    suspend fun updateBooking(id: String, status: String? = null, approval: String? = null): Booking =
        supabase.from("bookings").update({
            if (status != null) set("status", status)
            if (approval != null) set("approval_status", approval)
        }) {
            select()
            filter { eq("id", id) }
        }.decodeSingle()

    suspend fun availability(): List<AvailabilityRule> =
        supabase.from("availability_rules").select {
            filter { eq("created_by_id", owner()) }
            order("day_of_week", Order.ASCENDING)
            order("start_time", Order.ASCENDING)
        }.decodeList()

    /** Replaces one weekday's hours; null means the day is off. */
    suspend fun setDay(day: Int, from: String?, to: String?) {
        supabase.from("availability_rules").delete {
            filter {
                eq("created_by_id", owner())
                eq("day_of_week", day)
            }
        }
        if (from != null && to != null) supabase.from("availability_rules").insert(NewRule(day, from, to))
    }

    suspend fun whatsApp(): WhatsAppSettings? =
        supabase.from("whatsapp_settings").select { filter { eq("created_by_id", owner()) } }
            .decodeList<WhatsAppSettings>().firstOrNull()

    suspend fun saveWhatsApp(phone: String?, template: String?) {
        supabase.from("whatsapp_settings").upsert(WhatsAppRow(owner(), phone, template)) {
            onConflict = "created_by_id"
        }
    }
}
