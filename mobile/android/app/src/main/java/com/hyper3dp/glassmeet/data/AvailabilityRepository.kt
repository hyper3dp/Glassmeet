package com.hyper3dp.glassmeet.data

import com.hyper3dp.glassmeet.util.Format
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate

/** Weekly hours, date overrides, the daily limit and holidays (web: Availability.jsx). */
object AvailabilityRepository {
    suspend fun rules(): List<AvailabilityRule> =
        supabase.from("availability_rules").select {
            filter { eq("created_by_id", ownerId()) }
            order("day_of_week", Order.ASCENDING)
            order("start_time", Order.ASCENDING)
        }.decodeList()

    // Each time range is its own row, saved on its own: update, insert or delete (never
    // delete-all-then-insert, which loses a day's hours if the insert fails).

    suspend fun addRule(day: Int, start: Int, end: Int): AvailabilityRule =
        insertRow("availability_rules", buildJsonObject {
            put("day_of_week", day)
            put("start_time", Format.hhmm(start))
            put("end_time", Format.hhmm(end))
        }).decodeSingle()

    suspend fun updateRule(id: String, start: Int, end: Int) {
        updateRows("availability_rules", buildJsonObject {
            put("start_time", Format.hhmm(start))
            put("end_time", Format.hhmm(end))
        }) { eq("id", id) }
    }

    suspend fun deleteRule(id: String) {
        supabase.from("availability_rules").delete { filter { eq("id", id) } }
    }

    /** Date overrides from today on. */
    suspend fun exceptions(): List<AvailabilityException> =
        supabase.from("availability_exceptions").select {
            filter {
                eq("created_by_id", ownerId())
                gte("date", LocalDate.now().toString())
            }
            order("date", Order.ASCENDING)
        }.decodeList()

    /**
     * mode: "off" = whole day blocked (no times), "block" = blocked hours, "custom" = available hours.
     */
    suspend fun addException(date: LocalDate, mode: String, start: Int?, end: Int?, label: String): AvailabilityException =
        insertRow("availability_exceptions", buildJsonObject {
            put("date", date.toString())
            put("type", if (mode == "custom") "available" else "blocked")
            if (mode == "off" || start == null || end == null) {
                put("start_time", JsonNull)
                put("end_time", JsonNull)
            } else {
                put("start_time", Format.hhmm(start))
                put("end_time", Format.hhmm(end))
            }
            put("label", label.trim().ifEmpty { null })
        }).decodeSingle()

    suspend fun deleteException(id: String) {
        supabase.from("availability_exceptions").delete { filter { eq("id", id) } }
    }

    suspend fun holidays(region: String = "ae"): List<Holiday> = runCatching {
        supabase.from("holidays").select(Columns.list("date", "name")) {
            filter {
                eq("region", region)
                gte("date", LocalDate.now().toString())
            }
            order("date", Order.ASCENDING)
        }.decodeList<Holiday>()
    }.getOrDefault(emptyList())

    /** Daily limit across all event types: 1-100, or null for no limit. */
    suspend fun setDailyLimit(limit: Int?) {
        ProfileRepository.update(buildJsonObject {
            if (limit == null) put("max_per_day", JsonNull) else put("max_per_day", limit.coerceIn(1, 100))
        })
    }

    suspend fun setHolidays(on: Boolean) {
        ProfileRepository.update(buildJsonObject {
            if (on) put("holidays", "ae") else put("holidays", JsonNull)
        })
    }
}
