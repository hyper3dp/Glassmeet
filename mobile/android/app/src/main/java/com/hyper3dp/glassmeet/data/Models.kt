package com.hyper3dp.glassmeet.data

import com.hyper3dp.glassmeet.util.Format
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.time.OffsetDateTime

// Rows as the database has them (supabase/migrations in the web app). Every column that can be
// null is nullable here, and columns with a default fall back to it, so one odd row never breaks
// decoding a whole list.

@Serializable
data class Profile(
    val id: String,
    val email: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("profile_photo") val photo: String? = null,
    val timezone: String? = null,
    val username: String? = null,
    @SerialName("whatsapp_number") val whatsappNumber: String? = null,
    val bio: String? = null,
    val holidays: String? = null,
    @SerialName("max_per_day") val maxPerDay: Int? = null,
)

@Serializable
data class EventType(
    val id: String,
    val name: String = "",
    val description: String? = null,
    val duration: Int = 30,
    val location: String? = null,
    @SerialName("meeting_url") val meetingUrl: String? = null,
    val address: String? = null,
    @SerialName("booking_type") val bookingType: String = "one_on_one",
    @SerialName("max_attendees") val maxAttendees: Int = 1,
    val active: Boolean = true,
    @SerialName("requires_approval") val requiresApproval: Boolean = false,
    @SerialName("email_required") val emailRequired: Boolean = true,
    val color: String? = null,
    val schedule: String = "weekly",
    val freq: String = "week",
    val every: Int = 1,
    val days: List<Int>? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val dates: List<String>? = null,
    @SerialName("fixed_times") val fixedTimes: List<String>? = null,
    val note: String? = null,
    @SerialName("max_future_days") val maxFutureDays: Int = 60,
    @SerialName("min_notice") val minNotice: Int = 0,
    @SerialName("max_per_day") val maxPerDay: Int? = null,
    @SerialName("buffer_before") val bufferBefore: Int = 0,
    @SerialName("buffer_after") val bufferAfter: Int = 0,
    @SerialName("custom_questions") val customQuestionsJson: JsonElement? = null,
    @SerialName("created_date") val createdDate: String? = null,
) {
    val customQuestions: List<CustomQuestion> get() = CustomQuestion.parse(customQuestionsJson)
    val isGroup get() = bookingType == "group"

    /** "30 min · Google Meet · Needs approval" */
    val summary: String
        get() = buildList {
            add(Format.duration(duration))
            add(location?.takeIf { it.isNotBlank() } ?: "Google Meet")
            if (isGroup) add("Group of $maxAttendees")
            if (requiresApproval) add("Needs approval")
        }.joinToString(" · ")
}

/** One invitee question of an event type (custom_questions jsonb). */
data class CustomQuestion(
    val text: String = "",
    val type: String = "text",
    val required: Boolean = false,
    val options: List<String> = emptyList(),
) {
    val hasChoices get() = type == "dropdown" || type == "multiple_choice"

    companion object {
        val TYPES = listOf("text" to "One line", "long_text" to "Paragraph", "dropdown" to "Dropdown", "multiple_choice" to "Choose one")

        /** Reads whatever shape the column holds; anything unexpected is skipped, never a crash. */
        fun parse(json: JsonElement?): List<CustomQuestion> {
            val array = json as? JsonArray ?: return emptyList()
            return array.mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                CustomQuestion(
                    text = o.string("text") ?: o.string("label") ?: "",
                    type = o.string("type") ?: "text",
                    required = (o["required"] as? JsonPrimitive)?.booleanOrNull ?: false,
                    options = (o["options"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList(),
                )
            }
        }
    }
}

/** A guest's answer to an invitee question (custom_answers jsonb). */
data class CustomAnswer(val question: String, val answer: String) {
    companion object {
        fun parse(json: JsonElement?): List<CustomAnswer> = when (json) {
            is JsonArray -> json.mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                val q = o.string("question") ?: o.string("text") ?: o.string("label") ?: return@mapNotNull null
                CustomAnswer(q, o.string("answer") ?: o.string("value") ?: "")
            }
            is JsonObject -> json.entries.map { (k, v) -> CustomAnswer(k, (v as? JsonPrimitive)?.contentOrNull ?: v.toString()) }
            else -> emptyList()
        }
    }
}

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

@Serializable
data class Booking(
    val id: String,
    @SerialName("event_type_id") val eventTypeId: String? = null,
    @SerialName("event_name") val eventName: String? = null,
    @SerialName("guest_name") val guestName: String? = null,
    @SerialName("guest_email") val guestEmail: String? = null,
    @SerialName("guest_phone") val guestPhone: String? = null,
    @SerialName("guest_notes") val guestNotes: String? = null,
    val notes: String? = null,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,
    val timezone: String? = null,
    @SerialName("meeting_url") val meetingUrl: String? = null,
    val status: String = "confirmed",
    @SerialName("approval_status") val approvalStatus: String = "approved",
    @SerialName("custom_answers") val customAnswersJson: JsonElement? = null,
    @SerialName("attendee_count") val attendeeCount: Int? = null,
    @SerialName("created_date") val createdDate: String? = null,
    @SerialName("updated_date") val updatedDate: String? = null,
) {
    val name: String get() = guestName?.takeIf { it.isNotBlank() } ?: "Guest"
    val start: Instant get() = parseInstant(startTime) ?: Instant.EPOCH
    val end: Instant get() = parseInstant(endTime) ?: start.plusSeconds(30 * 60)
    val minutes: Long get() = java.time.Duration.between(start, end).toMinutes().coerceAtLeast(0)
    val customAnswers: List<CustomAnswer> get() = CustomAnswer.parse(customAnswersJson)
    val isCancelled get() = status == "cancelled"
    val isDeclined get() = approvalStatus == "declined"
    val isPending get() = approvalStatus == "pending" && !isCancelled

    /** A meeting stays upcoming until it ends, so one in progress is still listed (web: bookings.jsx). */
    fun isUpcoming(now: Instant = Instant.now()) = status == "confirmed" && !end.isBefore(now)

    /** Cancelled meetings leave the lists two days after they changed (web: lib/bookings.js). */
    fun isExpiredCancellation(now: Instant = Instant.now()): Boolean {
        if (!isCancelled) return false
        val changed = parseInstant(updatedDate) ?: parseInstant(createdDate) ?: return false
        return now.toEpochMilli() - changed.toEpochMilli() > 2 * 86_400_000L
    }
}

fun parseInstant(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        ?: runCatching { Instant.parse(value) }.getOrNull()
        // Postgres may send "2026-11-15 10:00:00+00"
        ?: runCatching { OffsetDateTime.parse(value.replace(' ', 'T').let { if (Regex("[+-]\\d\\d$").containsMatchIn(it)) "$it:00" else it }).toInstant() }.getOrNull()
}

@Serializable
data class AvailabilityRule(
    val id: String,
    @SerialName("day_of_week") val dayOfWeek: Int = 0,
    @SerialName("start_time") val startTime: String = "09:00",
    @SerialName("end_time") val endTime: String = "17:00",
)

@Serializable
data class AvailabilityException(
    val id: String,
    val date: String = "",
    val type: String = "blocked",
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,
    val label: String? = null,
)

@Serializable
data class Holiday(val date: String = "", val name: String = "")

@Serializable
data class WhatsAppSettings(
    val id: String? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("confirmation_template") val confirmationTemplate: String? = null,
    @SerialName("reminder_24h_template") val reminder24hTemplate: String? = null,
    @SerialName("reminder_1h_template") val reminder1hTemplate: String? = null,
)

@Serializable
data class SavedAddress(val id: String, val label: String? = null, val address: String = "")

@Serializable
data class EventTemplate(val id: String, val name: String = "", val data: JsonElement? = null)

@Serializable
data class GoogleAccount(
    val email: String? = null,
    @SerialName("calendar_id") val calendarId: String? = null,
    @SerialName("calendar_name") val calendarName: String? = null,
    @SerialName("auto_sync") val autoSync: Boolean = true,
    val connected: Boolean = true,
    @SerialName("synced_date") val syncedDate: String? = null,
)

@Serializable
data class CalendarConnection(
    val provider: String = "",
    val email: String? = null,
    val teams: Boolean = false,
    @SerialName("auto_sync") val autoSync: Boolean = true,
    val connected: Boolean = true,
    @SerialName("synced_date") val syncedDate: String? = null,
)

@Serializable
data class GoogleCalendarItem(val id: String = "", val summary: String? = null, val primary: Boolean = false)

@Serializable
data class CalFeed(val token: String = "")
