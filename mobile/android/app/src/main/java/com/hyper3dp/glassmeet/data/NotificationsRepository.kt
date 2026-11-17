package com.hyper3dp.glassmeet.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** public.notifications: the web's bell (new bookings, requests, cancellations, holidays). */
@Serializable
data class AppNotification(
    val id: String,
    val title: String? = null,
    val message: String? = null,
    /** A web path such as /bookings?id=<uuid> */
    val link: String? = null,
    val read: Boolean? = null,
    @SerialName("created_date") val createdDate: String? = null,
) {
    val isRead get() = read == true
}

/** Booking messages, shared by Home's bell and the list, checked every minute while the app is open. */
object NotificationsRepository {
    private val _items = MutableStateFlow<List<AppNotification>>(emptyList())
    val items: StateFlow<List<AppNotification>> = _items.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    suspend fun refresh() {
        try {
            _items.value = supabase.from("notifications").select(Columns.list("id", "title", "message", "link", "read", "created_date")) {
                filter { eq("created_by_id", ownerId()) }
                order("created_date", Order.DESCENDING)
                limit(50)
            }.decodeList()
            _loaded.value = true
            _error.value = null
        } catch (e: Exception) {
            // a background check failing keeps the list already shown
            if (!_loaded.value) _error.value = e.friendly("Booking messages didn't load.")
        }
    }

    /** Marks them read here first; puts them back if the server says no. */
    suspend fun markRead(ids: List<String>): Boolean {
        if (ids.isEmpty()) return true
        val before = _items.value
        val marked = ids.toSet()
        _items.value = before.map { if (it.id in marked) it.copy(read = true) else it }
        return try {
            updateRows("notifications", buildJsonObject { put("read", true) }) { isIn("id", ids) }
            true
        } catch (e: Exception) {
            _items.value = before
            false
        }
    }

    fun clear() {
        _items.value = emptyList()
        _loaded.value = false
        _error.value = null
    }
}
