package com.hyper3dp.glassmeet.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.ZoneId

/** The host's profile, shared by every screen (web: useprofile.js, settings.jsx). */
object ProfileRepository {
    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    val zone: ZoneId get() = com.hyper3dp.glassmeet.util.Format.zone(_profile.value?.timezone)

    /** "Ann Lee", else the part of the email before the @ */
    val hostName: String
        get() = _profile.value?.fullName?.takeIf { it.isNotBlank() }
            ?: supabase.auth.currentUserOrNull()?.email?.substringBefore("@").orEmpty()

    fun clear() {
        _profile.value = null
        seeded = false
    }

    /** Loads the profile, creating it the first time (web: the profile is made on first load). */
    suspend fun load(): Profile {
        val uid = ownerId()
        val found = supabase.from("profiles").select { filter { eq("id", uid) } }.decodeList<Profile>().firstOrNull()
        val profile = found ?: run {
            val user = supabase.auth.currentUserOrNull()
            val name = (user?.userMetadata?.get("full_name") as? JsonPrimitive)?.content
                ?: (user?.userMetadata?.get("name") as? JsonPrimitive)?.content
            val row = buildJsonObject {
                put("id", uid)
                put("email", user?.email)
                put("full_name", name)
            }
            try {
                insertRow("profiles", row).decodeSingle<Profile>()
            } catch (e: Exception) {
                // made at the same moment by the database trigger or another device: read it again
                supabase.from("profiles").select { filter { eq("id", uid) } }.decodeList<Profile>().firstOrNull() ?: throw e
            }
        }
        _profile.value = profile
        runCatching { seedHours(profile) }
        return profile
    }

    private var seeded = false

    /**
     * First-run Mon-Fri 9:00-17:00 hours, so booking pages work out of the box (web: useprofile.js).
     * Seeded once per host: profiles.hours_seeded is claimed on the server (update ... where
     * hours_seeded = false), so a host who later turns every day off keeps them off.
     */
    private suspend fun seedHours(p: Profile) {
        if (seeded || p.hoursSeeded == true) return
        seeded = true
        if (p.hoursSeeded == false) {
            val claimed = updateRows("profiles", buildJsonObject { put("hours_seeded", true) }) {
                eq("id", p.id)
                eq("hours_seeded", false)
            }.decodeList<Profile>().isNotEmpty()
            if (!claimed) return
            try {
                insertDefaultHoursIfNone()
                _profile.value = _profile.value?.copy(hoursSeeded = true)
            } catch (e: Exception) {
                // let the next load try again
                runCatching { updateRows("profiles", buildJsonObject { put("hours_seeded", false) }) { eq("id", p.id) } }
                seeded = false
                throw e
            }
            return
        }
        // no hours_seeded column yet: hosts with no hours at all get the defaults
        insertDefaultHoursIfNone()
    }

    private suspend fun insertDefaultHoursIfNone() {
        if (AvailabilityRepository.rules().isNotEmpty()) return
        val rows = (1..5).map { dow ->
            buildJsonObject {
                put("day_of_week", dow)
                put("start_time", "09:00")
                put("end_time", "17:00")
            }
        }
        supabase.from("availability_rules").insert(body = kotlinx.serialization.json.JsonArray(rows))
    }

    suspend fun ensureLoaded(): Profile = _profile.value ?: load()

    /** Saves profile columns; a changed name, photo or time zone is copied onto every event type. */
    suspend fun update(values: JsonObject): Profile {
        val before = _profile.value
        val saved = try {
            updateRows("profiles", values) { eq("id", ownerId()) }.decodeSingle<Profile>()
        } catch (e: Exception) {
            val text = e.message.orEmpty()
            if ((text.contains("duplicate key", true) || text.contains("unique", true)) && text.contains("username", true)) {
                throw Exception("That username is taken. Try another.")
            }
            throw e
        }
        _profile.value = saved
        syncHostFields(before, saved)
        return saved
    }

    /**
     * Booking pages read the host's zone, name and photo from each event type (guests can't read
     * profiles), so changes are copied onto the host's event types, in one request.
     */
    private suspend fun syncHostFields(before: Profile?, after: Profile) {
        val changed = buildJsonObject {
            if (before?.fullName != after.fullName) put("host_name", after.fullName?.takeIf { it.isNotBlank() })
            if (before?.photo != after.photo) put("host_photo", after.photo?.takeIf { it.isNotBlank() })
            if (before?.timezone != after.timezone && !after.timezone.isNullOrBlank()) put("host_timezone", after.timezone)
        }
        if (changed.isEmpty()) return
        supabase.from("event_types").update(body = changed as kotlinx.serialization.json.JsonElement) {
            filter { eq("created_by_id", after.id) }
        }
    }

    /** The host_* columns an event type save writes (web: Event types.jsx onSave). */
    fun hostFields(): JsonObject {
        val p = _profile.value
        return buildJsonObject {
            put("host_name", p?.fullName?.takeIf { it.isNotBlank() })
            put("host_photo", p?.photo?.takeIf { it.isNotBlank() })
            put("host_timezone", p?.timezone?.takeIf { it.isNotBlank() } ?: ZoneId.systemDefault().id)
        }
    }

    /** Uploads to avatars/<uid>/avatar-<timestamp>.<ext> (insert-only) and returns the public URL. */
    suspend fun uploadAvatar(bytes: ByteArray, extension: String, mimeType: String): String {
        val uid = ownerId()
        val ext = extension.lowercase().filter { it.isLetterOrDigit() }.ifEmpty { "jpg" }
        val path = "$uid/avatar-${System.currentTimeMillis()}.$ext"
        val bucket = supabase.storage.from("avatars")
        try {
            bucket.upload(path, bytes) {
                upsert = false
                contentType = ContentType.parse(mimeType)
            }
        } catch (e: Exception) {
            if (e.message.orEmpty().contains("bucket not found", true)) throw Exception("Photo storage isn't set up yet.")
            throw e
        }
        return bucket.publicUrl(path)
    }

    /** Disconnects calendars, deletes the account (RPC delete_my_account), removes photos, signs out. */
    suspend fun deleteAccount() {
        val uid = ownerId()
        runCatching { callFunction("google-calendar", buildJsonObject { put("action", "disconnect") }) }
        runCatching { callFunction("connections", buildJsonObject { put("action", "disconnect"); put("provider", "microsoft") }) }
        supabase.postgrest.rpc("delete_my_account")
        runCatching {
            val bucket = supabase.storage.from("avatars")
            val files = bucket.list(uid)
            if (files.isNotEmpty()) bucket.delete(files.map { "$uid/${it.name}" })
        }
        AuthRepository.signOut()
    }
}
