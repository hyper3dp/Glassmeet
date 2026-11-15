package com.hyper3dp.glassmeet.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Google Calendar ("google-calendar" edge function), Microsoft Outlook / Teams ("connections")
 * and the .ics subscription feed (web: calenderconnections.jsx, localApi.js).
 */
object CalendarRepository {
    private const val GOOGLE = "google-calendar"
    private const val CONNECTIONS = "connections"

    /** Copies a booking change to the host's connected calendars. Never fails the caller. */
    suspend fun pushBooking(bookingId: String) = coroutineScope {
        launch { runCatching { callFunction(GOOGLE, buildJsonObject { put("action", "pushBooking"); put("bookingId", bookingId) }) } }
        launch { runCatching { callFunction(CONNECTIONS, buildJsonObject { put("action", "pushBooking"); put("bookingId", bookingId) }) } }
        Unit
    }

    // ── Google ──

    /** The connection row, or null when not connected (or the table isn't there yet). */
    suspend fun googleStatus(): GoogleAccount? = runCatching {
        supabase.from("google_accounts").select(
            Columns.list("email", "calendar_id", "calendar_name", "auto_sync", "connected", "synced_date"),
        ) { filter { eq("user_id", ownerId()) } }.decodeList<GoogleAccount>().firstOrNull()
    }.getOrNull()

    suspend fun googleAuthUrl(): String =
        callFunction(GOOGLE, buildJsonObject { put("action", "authUrl"); put("returnTo", Backend.CALENDAR_CALLBACK) }).field("url")
            ?: throw FunctionException("unavailable", "Google Calendar isn't available yet.")

    suspend fun googleFinish(code: String, state: String) {
        callFunction(GOOGLE, buildJsonObject { put("action", "finish"); put("code", code); put("state", state) })
    }

    suspend fun googleCalendars(): List<GoogleCalendarItem> {
        val result = callFunction(GOOGLE, buildJsonObject { put("action", "listCalendars") })
        val array = result as? JsonArray ?: ((result as? JsonObject)?.get("calendars") as? JsonArray) ?: return emptyList()
        return array.mapNotNull { item ->
            val o = item as? JsonObject ?: return@mapNotNull null
            val id = (o["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            GoogleCalendarItem(id, (o["summary"] as? JsonPrimitive)?.contentOrNull, (o["primary"] as? JsonPrimitive)?.booleanOrNull ?: false)
        }
    }

    suspend fun googleSaveSettings(calendarId: String? = null, autoSync: Boolean? = null) {
        callFunction(GOOGLE, buildJsonObject {
            put("action", "saveSettings")
            if (calendarId != null) put("calendarId", calendarId)
            if (autoSync != null) put("autoSync", autoSync)
        })
    }

    /** Returns the time the sync finished, if the function says. */
    suspend fun googleSync(): String? = callFunction(GOOGLE, buildJsonObject { put("action", "sync") }).field("syncedDate")

    suspend fun googleDisconnect() {
        callFunction(GOOGLE, buildJsonObject { put("action", "disconnect") })
    }

    // ── Microsoft (Outlook, Teams) ──

    suspend fun microsoftAvailable(): Boolean = runCatching {
        val result = callFunction(CONNECTIONS, buildJsonObject { put("action", "available") })
        ((result as? JsonObject)?.get("microsoft") as? JsonPrimitive)?.booleanOrNull ?: false
    }.getOrDefault(false)

    suspend fun microsoftStatus(): CalendarConnection? = runCatching {
        supabase.from("connections").select(
            Columns.list("provider", "email", "teams", "auto_sync", "connected", "synced_date"),
        ) { filter { eq("user_id", ownerId()) } }.decodeList<CalendarConnection>().firstOrNull { it.provider == "microsoft" }
    }.getOrNull()

    suspend fun microsoftAuthUrl(): String =
        callFunction(CONNECTIONS, buildJsonObject {
            put("action", "authUrl"); put("provider", "microsoft"); put("returnTo", Backend.CALENDAR_CALLBACK)
        }).field("url") ?: throw FunctionException("unavailable", "This connection isn't available yet.")

    suspend fun connectionFinish(code: String, state: String) {
        callFunction(CONNECTIONS, buildJsonObject { put("action", "finish"); put("code", code); put("state", state) })
    }

    suspend fun microsoftSaveSettings(autoSync: Boolean) {
        callFunction(CONNECTIONS, buildJsonObject { put("action", "saveSettings"); put("provider", "microsoft"); put("autoSync", autoSync) })
    }

    suspend fun microsoftSync(): String? =
        callFunction(CONNECTIONS, buildJsonObject { put("action", "sync"); put("provider", "microsoft") }).field("syncedDate")

    suspend fun microsoftDisconnect() {
        callFunction(CONNECTIONS, buildJsonObject { put("action", "disconnect"); put("provider", "microsoft") })
    }

    // ── Subscription feed (.ics) ──

    /** The host's feed token, made on first use. */
    suspend fun feedToken(): String {
        val uid = ownerId()
        suspend fun read() = supabase.from("cal_feeds").select(Columns.list("token")) { filter { eq("owner", uid) } }
            .decodeList<CalFeed>().firstOrNull()?.token
        read()?.let { return it }
        return try {
            insertRow("cal_feeds", buildJsonObject { put("owner", uid) }).decodeSingle<CalFeed>().token
        } catch (e: Exception) {
            // another device made it first
            read() ?: throw e
        }
    }

    /** New token; calendars subscribed to the old link stop updating. */
    suspend fun rotateFeed(): String {
        val data = supabase.postgrest.rpc("rotate_cal_feed").data
        val parsed = runCatching { AppJson.parseToJsonElement(data) }.getOrNull()
        return (parsed as? JsonPrimitive)?.contentOrNull
            ?: parsed.field("token")
            ?: ((parsed as? JsonArray)?.firstOrNull()).field("rotate_cal_feed")
            ?: feedToken()
    }
}
