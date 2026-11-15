package com.hyper3dp.glassmeet.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Event types, templates and saved addresses (web: Event types.jsx, lib/templates.js). */
object EventTypesRepository {
    private val _version = MutableStateFlow(0)

    /** Bumped after every change, so lists (Scheduling, Home) reload. */
    val version: StateFlow<Int> = _version.asStateFlow()
    private fun changed() { _version.value++ }

    suspend fun list(): List<EventType> =
        supabase.from("event_types").select {
            filter { eq("created_by_id", ownerId()) }
            order("created_date", Order.DESCENDING)
        }.decodeList()

    suspend fun get(id: String): EventType? =
        supabase.from("event_types").select { filter { eq("id", id) } }.decodeList<EventType>().firstOrNull()

    /** Saves the form's columns plus host_name, host_photo and host_timezone from the profile. */
    suspend fun save(id: String?, values: JsonObject): EventType {
        ProfileRepository.ensureLoaded()
        val row = JsonObject(values + ProfileRepository.hostFields())
        val saved = if (id == null) insertRow("event_types", row).decodeSingle<EventType>()
        else updateRows("event_types", row) { eq("id", id) }.decodeList<EventType>().firstOrNull()
            ?: throw Exception("This event type may have been deleted. Go back and refresh.")
        changed()
        return saved
    }

    suspend fun setActive(id: String, active: Boolean) {
        updateRows("event_types", buildJsonObject { put("active", active) }) { eq("id", id) }
        changed()
    }

    suspend fun delete(id: String) {
        supabase.from("event_types").delete { filter { eq("id", id) } }
        changed()
    }

    // ── Templates: built-in ones plus the host's own event_templates rows ──

    suspend fun templates(): List<EventTemplate> = runCatching {
        supabase.from("event_templates").select {
            filter { eq("created_by_id", ownerId()) }
            order("created_date", Order.DESCENDING)
        }.decodeList<EventTemplate>()
    }.getOrDefault(emptyList()) // the table may not exist yet; built-in templates still show

    suspend fun saveTemplate(name: String, data: JsonObject) {
        val clean = name.trim().ifEmpty { "Untitled template" }.take(80)
        insertRow("event_templates", buildJsonObject {
            put("name", clean)
            put("data", JsonObject(data + ("name" to JsonPrimitive(clean))))
        })
    }

    suspend fun deleteTemplate(id: String) {
        supabase.from("event_templates").delete { filter { eq("id", id) } }
    }

    suspend fun addresses(): List<SavedAddress> = runCatching {
        supabase.from("addresses").select {
            filter { eq("created_by_id", ownerId()) }
            order("created_date", Order.ASCENDING)
        }.decodeList<SavedAddress>()
    }.getOrDefault(emptyList())

    suspend fun addAddress(label: String, address: String): SavedAddress =
        insertRow("addresses", buildJsonObject {
            put("label", label.trim())
            put("address", address.trim().take(300))
        }).decodeSingle()

    suspend fun deleteAddress(id: String) {
        supabase.from("addresses").delete { filter { eq("id", id) } }
    }

    /** The template fields (lib/templates.js TEMPLATE_FIELDS) of an event type's saved columns. */
    val TEMPLATE_FIELDS = listOf(
        "name", "description", "duration", "location", "meeting_url", "address",
        "booking_type", "max_attendees", "requires_approval", "email_required",
        "schedule", "freq", "every", "days", "fixed_times",
        "note", "max_future_days", "min_notice", "max_per_day", "buffer_before", "buffer_after", "custom_questions",
    )

    fun templateData(values: JsonObject): JsonObject = JsonObject(values.filterKeys { it in TEMPLATE_FIELDS })

    /** An event type as a template's data (for Duplicate), dates included. */
    fun asTemplateData(e: EventType): JsonObject = buildJsonObject {
        put("name", "${e.name} (copy)")
        put("description", e.description)
        put("duration", e.duration)
        put("location", e.location)
        put("meeting_url", e.meetingUrl)
        put("address", e.address)
        put("booking_type", e.bookingType)
        put("max_attendees", e.maxAttendees)
        put("requires_approval", e.requiresApproval)
        put("email_required", e.emailRequired)
        put("schedule", e.schedule)
        put("freq", e.freq)
        put("every", e.every)
        e.days?.let { d -> put("days", JsonArray(d.map { JsonPrimitive(it) })) }
        e.fixedTimes?.let { t -> put("fixed_times", JsonArray(t.map { JsonPrimitive(it) })) }
        put("note", e.note)
        put("max_future_days", e.maxFutureDays)
        put("min_notice", e.minNotice)
        put("max_per_day", e.maxPerDay)
        put("buffer_before", e.bufferBefore)
        put("buffer_after", e.bufferAfter)
        e.customQuestionsJson?.let { put("custom_questions", it) }
        put("start_date", e.startDate)
        put("end_date", e.endDate)
        val today = java.time.LocalDate.now().toString()
        e.dates?.let { d -> put("dates", JsonArray(d.filter { it >= today }.map { JsonPrimitive(it) })) }
    }

    /** Decodes a template's data as an event type, so the form can start from it. */
    fun fromTemplate(data: JsonElement?): EventType? = runCatching {
        val o = data as? JsonObject ?: return null
        AppJson.decodeFromJsonElement(EventType.serializer(), JsonObject(o + ("id" to JsonPrimitive(""))))
    }.getOrNull()
}

/** Built-in templates (web: src/lib/templates.js). */
object BuiltInTemplates {
    data class Item(val id: String, val name: String, val data: JsonObject)

    private fun days(vararg d: Int) = JsonArray(d.map { JsonPrimitive(it) })

    val all = listOf(
        Item("quick-chat", "Quick chat", buildJsonObject {
            put("name", "Quick chat"); put("duration", 15); put("location", "Google Meet")
            put("description", "A short call to say hello or answer a quick question.")
        }),
        Item("meeting", "30 minute meeting", buildJsonObject {
            put("name", "30 minute meeting"); put("duration", 30); put("location", "Google Meet")
        }),
        Item("consultation", "Consultation", buildJsonObject {
            put("name", "Consultation"); put("duration", 60); put("location", "Zoom"); put("requires_approval", true)
            put("description", "A full hour to go through your needs. I confirm each request personally.")
        }),
        Item("whatsapp-call", "WhatsApp call", buildJsonObject {
            put("name", "WhatsApp call"); put("duration", 30); put("location", "WhatsApp call")
        }),
        Item("in-person", "In-person meeting", buildJsonObject {
            put("name", "In-person meeting"); put("duration", 60); put("location", "In person")
        }),
        Item("office-hours", "Office hours", buildJsonObject {
            put("name", "Office hours"); put("duration", 15); put("location", "Google Meet")
            put("freq", "week"); put("every", 1); put("days", days(2, 4))
            put("description", "Drop-in slots on Tuesdays and Thursdays.")
        }),
        Item("group-class", "Weekly class", buildJsonObject {
            put("name", "Weekly class"); put("duration", 60); put("location", "Zoom")
            put("booking_type", "group"); put("max_attendees", 10)
            put("freq", "week"); put("every", 1); put("days", days(1))
            put("description", "A group session every Monday. Book a spot.")
        }),
        Item("monthly-review", "Monthly check-in", buildJsonObject {
            put("name", "Monthly check-in"); put("duration", 45); put("location", "Google Meet")
            put("freq", "month"); put("every", 1)
            put("description", "One session a month to review progress.")
        }),
    )

    /** "30 min · Google Meet · Weekly" (lib/templates.js templateSummary) */
    fun summary(data: JsonObject): String {
        val e = EventTypesRepository.fromTemplate(data) ?: return ""
        val parts = mutableListOf("${e.duration} min", e.location ?: "Google Meet")
        if (e.isGroup) parts += "Group of ${e.maxAttendees}"
        if (e.schedule != "dates" && e.freq == "month") parts += "Monthly"
        else if (e.schedule != "dates" && !e.days.isNullOrEmpty()) parts += "Weekly"
        return parts.joinToString(" · ")
    }
}
