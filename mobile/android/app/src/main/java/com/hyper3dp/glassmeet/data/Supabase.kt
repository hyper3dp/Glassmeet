package com.hyper3dp.glassmeet.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.ExternalAuthAction
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import kotlinx.serialization.json.Json

/** The same Supabase project and publishable key as the web app. Row Level Security protects the data. */
object Backend {
    const val URL = "https://sjxraemrdpzleipfmwun.supabase.co"
    const val KEY = "sb_publishable_0T8UhXmO8GUjyZa67yIU5w_RZwkPa4O"

    /** Guests book on the web: /book/<event type id> */
    const val WEB = "https://glassmeet.run.place"

    /** Where OAuth providers send the browser back to (must be in Supabase Auth -> Redirect URLs). */
    const val AUTH_CALLBACK = "glassmeet://auth-callback"

    /** Where the calendar connection functions send the browser back to. */
    const val CALENDAR_CALLBACK = "glassmeet://calendar-connections"

    fun bookingUrl(eventTypeId: String) = "$WEB/book/$eventTypeId"
    fun feedUrl(token: String) = "$URL/functions/v1/calendar-feed/$token.ics"
}

/** Lenient decoding: unknown columns are ignored and a null in a column with a default falls back to it. */
val AppJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    explicitNulls = false
}

val supabase: SupabaseClient = createSupabaseClient(Backend.URL, Backend.KEY) {
    defaultSerializer = KotlinXSerializer(AppJson)
    install(Auth) {
        scheme = "glassmeet"
        host = "auth-callback"
        flowType = FlowType.PKCE
        defaultExternalAuthAction = ExternalAuthAction.CustomTabs()
    }
    install(Postgrest)
    install(Storage)
    install(Functions)
}

/** The signed-in host's id, or an error that the screens show as "You are signed out." */
fun ownerId(): String = supabase.auth.currentUserOrNull()?.id ?: error("You are signed out. Sign in again.")
