package com.hyper3dp.glassmeet.ui.calendar

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.hyper3dp.glassmeet.data.Booking
import com.hyper3dp.glassmeet.util.Snack
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** iCalendar (RFC 5545) text for meetings, like the web's src/lib/ics.js. */
object Ics {
    data class Event(val id: String, val title: String, val start: Instant, val end: Instant, val details: String?, val link: String?)

    fun event(b: Booking): Event {
        val link = b.meetingUrl?.takeIf { it.isNotBlank() }
        val lines = buildList {
            add("Guest: ${b.name}")
            b.guestEmail?.takeIf { it.isNotBlank() }?.let { add("Email: $it") }
            b.guestPhone?.takeIf { it.isNotBlank() }?.let { add("Phone: $it") }
            link?.let { add("Join: $it") }
        }
        val title = b.eventName?.takeIf { it.isNotBlank() } ?: "Meeting"
        return Event("gm${b.id}", "$title with ${b.name}", b.start, b.end, lines.joinToString("\n"), link)
    }

    private val utc = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    fun calendar(name: String, events: List<Event>): String {
        val out = mutableListOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//GlassMeet//GlassMeet app//EN", "CALSCALE:GREGORIAN", "METHOD:PUBLISH", "X-WR-CALNAME:${text(name)}")
        val stamp = utc.format(Instant.now())
        events.forEach { e ->
            out += listOf("BEGIN:VEVENT", "UID:${e.id}@glassmeet", "DTSTAMP:$stamp", "DTSTART:${utc.format(e.start)}", "DTEND:${utc.format(e.end)}", "SUMMARY:${text(e.title)}")
            e.details?.takeIf { it.isNotBlank() }?.let { out += "DESCRIPTION:${text(it)}" }
            e.link?.let {
                out += "LOCATION:${text(it)}"
                if (it.startsWith("http")) out += "URL:$it"
            }
            out += "END:VEVENT"
        }
        out += "END:VCALENDAR"
        return out.joinToString("\r\n") { fold(it) } + "\r\n"
    }

    /** Escapes guest-written text (backslash, ; , and new lines). */
    private fun text(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\n", "\\n")

    /** Lines longer than 75 octets continue on the next line after a space. */
    private fun fold(line: String): String {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        line.forEach { ch ->
            val limit = if (parts.isEmpty()) 75 else 74
            if ((current.toString() + ch).toByteArray().size > limit) {
                parts += current.toString()
                current.clear()
            }
            current.append(ch)
        }
        parts += current.toString()
        return parts.joinToString("\r\n ")
    }

    /** Writes the file to the app's cache and offers it to a calendar app, or the share sheet. */
    fun open(context: Context, fileName: String, ics: String) {
        runCatching {
            val dir = File(context.cacheDir, "calendar").apply { mkdirs() }
            val file = File(dir, fileName).apply { writeText(ics) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/calendar").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val send = Intent(Intent.ACTION_SEND).setType("text/calendar").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val chooser = Intent.createChooser(send, "Add to calendar").putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(view))
            context.startActivity(chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Snack.show("No app on this phone can open calendar files.") }
    }
}
