package com.hyper3dp.glassmeet.data

import android.net.Uri
import com.hyper3dp.glassmeet.util.Format
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.ZoneId

/** whatsapp_settings: the host's number and message templates (web: whatsappsettings.jsx). */
object WhatsAppRepository {
    private var cached: WhatsAppSettings? = null

    suspend fun get(): WhatsAppSettings? {
        val row = supabase.from("whatsapp_settings").select { filter { eq("created_by_id", ownerId()) } }
            .decodeList<WhatsAppSettings>().firstOrNull()
        cached = row
        return row
    }

    suspend fun cachedOrLoad(): WhatsAppSettings? = cached ?: runCatching { get() }.getOrNull()

    suspend fun save(phone: String, confirmation: String, reminder24h: String, reminder1h: String): WhatsAppSettings {
        // created_by_id is unique and defaults to auth.uid(): upsert on it keeps one row per host
        val row = buildJsonObject {
            put("created_by_id", ownerId())
            put("phone_number", phone.trim().ifEmpty { null })
            put("provider", "deep_link")
            put("confirmation_template", confirmation.takeIf { it.isNotBlank() && it != Messages.CONFIRMATION })
            put("reminder_24h_template", reminder24h.takeIf { it.isNotBlank() && it != Messages.REMINDER_24H })
            put("reminder_1h_template", reminder1h.takeIf { it.isNotBlank() && it != Messages.REMINDER_1H })
        }
        val saved = supabase.from("whatsapp_settings").upsert(body = kotlinx.serialization.json.JsonArray(listOf(row))) {
            onConflict = "created_by_id"
            select()
        }.decodeList<WhatsAppSettings>().firstOrNull() ?: WhatsAppSettings(phoneNumber = phone)
        cached = saved
        return saved
    }
}

/** Message templates and filling (web: src/lib/whatsapp.js). GlassMeet prepares the message; the host sends it. */
object Messages {
    enum class Kind(val label: String) {
        Confirmation("Confirmation"),
        Reminder24h("Reminder 24h"),
        Reminder1h("Reminder 1h"),
        Cancellation("Cancellation"),
        Decline("Decline"),
    }

    val PLACEHOLDERS = listOf("[Name]", "[Host]", "[Date]", "[Time]", "[Duration]", "[Meeting Link]", "[Booking Link]", "[Timezone]")

    val CONFIRMATION = """
        Hi [Name]! Your meeting with [Host] is confirmed.

        Date: [Date]
        Time: [Time] ([Timezone])
        Duration: [Duration]
        Meeting: [Meeting Link]

        See you then!
    """.trimIndent()

    val REMINDER_24H = """
        Reminder: Your meeting with [Host] is tomorrow at [Time] ([Timezone]).

        Meeting: [Meeting Link]
    """.trimIndent()

    val REMINDER_1H = """
        Starting soon: Your meeting with [Host] is in 1 hour.

        Meeting: [Meeting Link]
    """.trimIndent()

    val CANCELLATION = """
        Hi [Name], I'm sorry, I need to cancel our meeting on [Date] at [Time] ([Timezone]).

        You can pick a new time here: [Booking Link]
    """.trimIndent()

    val DECLINE = """
        Hi [Name], thanks for your request for [Date] at [Time]. Unfortunately I can't make that time.

        Please pick another time here: [Booking Link]
    """.trimIndent()

    /** The message a meeting opens with: what just happened to it. */
    fun defaultKind(b: Booking): Kind = when {
        b.isDeclined -> Kind.Decline
        b.isCancelled -> Kind.Cancellation
        else -> Kind.Confirmation
    }

    fun template(kind: Kind, settings: WhatsAppSettings?): String = when (kind) {
        Kind.Confirmation -> settings?.confirmationTemplate?.takeIf { it.isNotBlank() } ?: CONFIRMATION
        Kind.Reminder24h -> settings?.reminder24hTemplate?.takeIf { it.isNotBlank() } ?: REMINDER_24H
        Kind.Reminder1h -> settings?.reminder1hTemplate?.takeIf { it.isNotBlank() } ?: REMINDER_1H
        Kind.Cancellation -> CANCELLATION
        Kind.Decline -> DECLINE
    }

    data class Context(
        val guestName: String,
        val hostName: String,
        val date: String,
        val time: String,
        val duration: String,
        val meetingUrl: String,
        val bookingLink: String,
        val timezone: String,
    )

    fun context(b: Booking, hostName: String, zone: ZoneId): Context = Context(
        guestName = b.name,
        hostName = hostName.ifBlank { "Your host" },
        date = Format.longDate(b.start, zone),
        time = Format.time(b.start, zone),
        duration = "${b.minutes} min",
        meetingUrl = b.meetingUrl.orEmpty(),
        bookingLink = b.eventTypeId?.let { Backend.bookingUrl(it) }.orEmpty(),
        timezone = zone.id,
    )

    /** A sample meeting for previews on the WhatsApp messages page. */
    fun sample(hostName: String, zone: ZoneId): Context {
        val start = java.time.ZonedDateTime.now(zone).plusDays(1).withHour(14).withMinute(30)
        return Context(
            guestName = "Sara",
            hostName = hostName.ifBlank { "Your host" },
            date = Format.longDate(start.toLocalDate()),
            time = Format.time(start.toInstant(), zone),
            duration = "30 min",
            meetingUrl = "https://meet.google.com/abc-defg-hij",
            bookingLink = "${Backend.WEB}/book/…",
            timezone = zone.id,
        )
    }

    /** Fills the placeholders; a line whose placeholder has no value ("Meeting: ") is left out. */
    fun fill(template: String, c: Context): String {
        val filled = template
            .replace("[Name]", c.guestName)
            .replace("[Host]", c.hostName)
            .replace("[Date]", c.date)
            .replace("[Time]", c.time)
            .replace("[Duration]", c.duration)
            .replace("[Meeting Link]", c.meetingUrl)
            .replace("[Booking Link]", c.bookingLink)
            .replace("[Timezone]", c.timezone)
        val empty = Regex("^[^:\\n]{1,20}:\\s*(\\(\\s*\\))?\\s*$")
        return filled.lines().filterNot { empty.matches(it) }.joinToString("\n")
    }

    /** wa.me link to the guest's number (or the share picker when there's no number). */
    fun whatsAppLink(phone: String?, message: String): Uri {
        val digits = Format.digits(phone)
        return Uri.parse(if (digits.isEmpty()) "https://wa.me/" else "https://wa.me/$digits")
            .buildUpon().appendQueryParameter("text", message).build()
    }
}
