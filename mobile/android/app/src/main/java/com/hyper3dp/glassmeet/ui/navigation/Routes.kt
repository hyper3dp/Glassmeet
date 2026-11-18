package com.hyper3dp.glassmeet.ui.navigation

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

// Type-safe navigation routes (navigation-compose). Each tab is a nested graph so its own back
// stack is saved and restored when switching tabs.

@Serializable object HomeGraph
@Serializable object MeetingsGraph
@Serializable object SchedulingGraph
@Serializable object AvailabilityGraph
@Serializable object SettingsGraph

@Serializable object HomeRoute
@Serializable object AnalyticsRoute
@Serializable object MeetingsRoute
@Serializable data class MeetingRoute(val id: String)

/** The meeting page opened from Home (its own type, so it stays in the Home tab's back stack). */
@Serializable data class HomeMeetingRoute(val id: String)
@Serializable object SchedulingRoute

/** id = null: a new event type, started from [EventTypeSeed] when one is set. */
@Serializable data class EventTypeFormRoute(val id: String? = null)
@Serializable object AvailabilityRoute
@Serializable object NotificationsRoute
/** Booking messages opened from Settings (its own type, so it stays in the Settings tab). */
@Serializable object SettingsNotificationsRoute
@Serializable object SettingsRoute
@Serializable object WhatsAppRoute
@Serializable object AddressesRoute
@Serializable object CalendarConnectionsRoute

// Signed out
@Serializable object SignInRoute
@Serializable object CreateAccountRoute
@Serializable data class VerifyCodeRoute(val email: String)
@Serializable object ResetPasswordRoute

/** One-shot requests between screens that don't fit a route argument. */
object NavRequests {
    /** Home's "{n} requests waiting" opens Meetings on this filter. */
    val meetingsFilter = MutableStateFlow<String?>(null)

    /** glassmeet://calendar-connections?... waiting to be finished on the Calendar connections page. */
    val calendarCallback = MutableStateFlow<Uri?>(null)

    /** A GlassMeet link opened from outside: "book:<event type id>" or a web path like /bookings?id=… */
    val openLink = MutableStateFlow<String?>(null)

    /** Scheduling opens its New event type sheet. */
    val createEventType = MutableStateFlow(false)
}

/** The data a new event type form starts from (a template or a duplicate). */
object EventTypeSeed {
    var data: kotlinx.serialization.json.JsonObject? = null
}
