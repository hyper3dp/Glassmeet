package com.hyper3dp.glassmeet

import android.net.Uri
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Ported from the web app's src/lib/whatsapp.js. GlassMeet prepares the message; the host sends it. */
object WhatsApp {
    val confirmation = """
        Hi [Name]! Your meeting with [Host] is confirmed.

        Date: [Date]
        Time: [Time] ([Timezone])
        Duration: [Duration]
        Meeting: [Meeting Link]

        See you then!
    """.trimIndent()

    val cancellation = """
        Hi [Name], I'm sorry, I need to cancel our meeting on [Date] at [Time] ([Timezone]).

        You can pick a new time here: [Booking Link]
    """.trimIndent()

    val decline = """
        Hi [Name], thanks for your request for [Date] at [Time]. Unfortunately I can't make that time.

        Please pick another time here: [Booking Link]
    """.trimIndent()

    fun link(phone: String?, message: String): Uri {
        val digits = phone.orEmpty().filter { it.isDigit() }
        return Uri.parse(if (digits.isEmpty()) "https://wa.me/" else "https://wa.me/$digits")
            .buildUpon().appendQueryParameter("text", message).build()
    }

    fun fill(template: String, booking: Booking, eventType: EventType?, host: String): String {
        val zone = runCatching { ZoneId.of(booking.timezone ?: "") }.getOrDefault(ZoneId.systemDefault())
        val start = booking.start.atZone(zone)
        val minutes = Duration.between(booking.start, booking.end).toMinutes()
        val values = mapOf(
            "[Name]" to booking.guestName,
            "[Host]" to host.ifEmpty { "your host" },
            "[Date]" to DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).format(start),
            "[Time]" to DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(start),
            "[Duration]" to "${eventType?.duration ?: minutes} min",
            "[Meeting Link]" to (booking.meetingUrl ?: eventType?.meetingUrl ?: ""),
            "[Booking Link]" to (booking.eventTypeId?.let { Backend.bookingUrl(it) } ?: ""),
            "[Timezone]" to zone.id,
        )
        var text = template
        values.forEach { (k, v) -> text = text.replace(k, v) }
        // A line whose placeholder had no value ("Meeting: ") is left out rather than sent half empty
        val empty = Regex("^[^:\\n]{1,20}:\\s*(\\(\\s*\\))?\\s*$")
        return text.lines().filterNot { empty.matches(it) }.joinToString("\n")
    }
}
