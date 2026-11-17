package com.hyper3dp.glassmeet.ui.calendar

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyper3dp.glassmeet.data.CalendarConnection
import com.hyper3dp.glassmeet.data.CalendarRepository
import com.hyper3dp.glassmeet.data.FunctionException
import com.hyper3dp.glassmeet.data.GoogleAccount
import com.hyper3dp.glassmeet.data.GoogleCalendarItem
import com.hyper3dp.glassmeet.data.friendly
import com.hyper3dp.glassmeet.ui.navigation.NavRequests
import com.hyper3dp.glassmeet.util.Snack
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalendarUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val google: GoogleAccount? = null,
    val calendars: List<GoogleCalendarItem> = emptyList(),
    val microsoftAvailable: Boolean = false,
    val microsoft: CalendarConnection? = null,
    val feedToken: String? = null,
    val feedError: String? = null,
    /** "google" | "microsoft" | "feed" while that card works */
    val busy: String? = null,
    val googleError: String? = null,
    val microsoftError: String? = null,
    /** Test connection answered */
    val googleVerified: Boolean = false,
    /** The result of Add my meetings / test event */
    val exportNote: String? = null,
)

class CalendarConnectionsViewModel : ViewModel() {
    private val _state = MutableStateFlow(CalendarUiState())
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()

    init {
        load()
        // the browser came back with glassmeet://calendar-connections?...
        viewModelScope.launch {
            NavRequests.calendarCallback.filterNotNull().collect { uri ->
                NavRequests.calendarCallback.value = null
                finish(uri)
            }
        }
    }

    fun load(refreshing: Boolean = false) {
        _state.update { it.copy(refreshing = refreshing) }
        viewModelScope.launch {
            val google = async { CalendarRepository.googleStatus() }
            val msAvailable = async { CalendarRepository.microsoftAvailable() }
            val microsoft = async { CalendarRepository.microsoftStatus() }
            val feed = async { runCatching { CalendarRepository.feedToken() } }
            val g = google.await()
            val f = feed.await()
            _state.update {
                it.copy(
                    loading = false, refreshing = false, google = g, microsoftAvailable = msAvailable.await(), microsoft = microsoft.await(),
                    feedToken = f.getOrNull(),
                    feedError = f.exceptionOrNull()?.let { e ->
                        if (e.message.orEmpty().contains("does not exist") || e.message.orEmpty().contains("schema cache")) "Calendar subscriptions aren't set up on the server yet."
                        else e.friendly("Your subscription link didn't load.")
                    },
                )
            }
            if (g?.connected == true) loadCalendars()
        }
    }

    private suspend fun loadCalendars() {
        runCatching { CalendarRepository.googleCalendars() }.onSuccess { list -> _state.update { it.copy(calendars = list) } }
    }

    private fun message(e: Throwable, provider: String): String = when ((e as? FunctionException)?.code) {
        "not_configured", "unavailable" -> "Not available yet."
        "reconnect", "not_connected" -> "Connect $provider again to keep it in sync."
        "state" -> "That sign-in took too long. Try connecting again."
        "wrong_user" -> "That sign-in belongs to another GlassMeet account."
        "scopes" -> "Allow GlassMeet to see and edit your calendar, then try again."
        else -> e.friendly("$provider didn't respond. Try again.")
    }

    private fun work(card: String, onError: (String) -> Unit, block: suspend () -> Unit) {
        if (_state.value.busy != null) return
        _state.update { it.copy(busy = card, googleError = null, microsoftError = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                onError(message(e, if (card == "microsoft") "Outlook" else "Google Calendar"))
            } finally {
                _state.update { it.copy(busy = null) }
            }
        }
    }

    private fun googleError(m: String) = _state.update { it.copy(googleError = m) }
    private fun microsoftError(m: String) = _state.update { it.copy(microsoftError = m) }

    /** Returns the consent page URL to open in a Custom Tab. */
    fun connectGoogle(open: (String) -> Unit) = work("google", this::googleError) { open(CalendarRepository.googleAuthUrl()) }
    fun connectMicrosoft(open: (String) -> Unit) = work("microsoft", this::microsoftError) { open(CalendarRepository.microsoftAuthUrl()) }

    private fun finish(uri: Uri) {
        val google = uri.getQueryParameter("google")
        val connect = uri.getQueryParameter("connect")
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        val reason = uri.getQueryParameter("reason") ?: uri.getQueryParameter("error")
        when {
            google == "finish" && code != null && state != null -> work("google", this::googleError) {
                CalendarRepository.googleFinish(code, state)
                Snack.show("Google Calendar connected")
                load()
            }
            connect == "finish" && code != null && state != null -> work("microsoft", this::microsoftError) {
                CalendarRepository.connectionFinish(code, state)
                Snack.show("Outlook connected")
                load()
            }
            google == "error" -> googleError(if (reason == "access_denied") "You cancelled the Google sign-in." else "Google Calendar didn't connect. Try again.")
            connect == "error" -> microsoftError(if (reason == "access_denied") "You cancelled the Microsoft sign-in." else "Outlook didn't connect. Try again.")
        }
    }

    fun setGoogleCalendar(id: String) = work("google", this::googleError) {
        CalendarRepository.googleSaveSettings(calendarId = id)
        val name = _state.value.calendars.firstOrNull { it.id == id }?.summary
        _state.update { it.copy(google = it.google?.copy(calendarId = id, calendarName = name)) }
    }

    fun setGoogleAutoSync(on: Boolean) = work("google", this::googleError) {
        CalendarRepository.googleSaveSettings(autoSync = on)
        _state.update { it.copy(google = it.google?.copy(autoSync = on)) }
    }

    fun syncGoogle() = work("google", this::googleError) {
        val at = CalendarRepository.googleSync() ?: java.time.Instant.now().toString()
        _state.update { it.copy(google = it.google?.copy(syncedDate = at)) }
        Snack.show("Google Calendar is up to date")
    }

    /** Reads the calendar list: if Google answers, the connection works (web: Test). */
    fun testGoogle() = work("google", this::googleError) {
        _state.update { it.copy(googleVerified = false) }
        val list = CalendarRepository.googleCalendars()
        _state.update { it.copy(calendars = list, googleVerified = true) }
        Snack.show("Google Calendar is working")
    }

    /** Every upcoming confirmed meeting as one .ics file (web: exportMyMeetings). */
    fun exportMeetings(share: (String, String) -> Unit) {
        if (_state.value.busy != null) return
        _state.update { it.copy(busy = "export", exportNote = null) }
        viewModelScope.launch {
            try {
                val now = java.time.Instant.now()
                val upcoming = com.hyper3dp.glassmeet.data.BookingsRepository.list()
                    .filter { !it.isCancelled && !it.isPending && !it.end.isBefore(now) }
                    .sortedBy { it.start }
                if (upcoming.isEmpty()) {
                    _state.update { it.copy(exportNote = "No upcoming meetings to add yet. New bookings will appear here.") }
                } else {
                    val name = com.hyper3dp.glassmeet.data.ProfileRepository.profile.value?.fullName?.ifBlank { null } ?: "Meetings"
                    share("glassmeet-meetings.ics", Ics.calendar("GlassMeet: $name", upcoming.map(Ics::event)))
                    val n = upcoming.size
                    _state.update { it.copy(exportNote = "$n upcoming meeting${if (n == 1) "" else "s"} ready. Open the file with your calendar app.") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(exportNote = "Couldn't load your meetings. ${e.friendly("")}".trim()) }
            } finally {
                _state.update { it.copy(busy = null) }
            }
        }
    }

    fun exportTestEvent(share: (String, String) -> Unit) {
        val start = java.time.LocalDate.now().plusDays(1).atTime(10, 0).atZone(java.time.ZoneId.systemDefault()).toInstant()
        val event = Ics.Event(
            "glassmeet-test", "GlassMeet test event", start, start.plusSeconds(1800),
            "A test from GlassMeet. If you can see this, your calendar app accepts GlassMeet events. You can delete it.", null,
        )
        share("glassmeet-test.ics", Ics.calendar("GlassMeet test", listOf(event)))
        _state.update { it.copy(exportNote = "Test event made. Add it to check your calendar app accepts GlassMeet events.") }
    }

    fun disconnectGoogle() = work("google", this::googleError) {
        CalendarRepository.googleDisconnect()
        _state.update { it.copy(google = null, calendars = emptyList()) }
        Snack.show("Google Calendar disconnected")
    }

    fun setMicrosoftAutoSync(on: Boolean) = work("microsoft", this::microsoftError) {
        CalendarRepository.microsoftSaveSettings(on)
        _state.update { it.copy(microsoft = it.microsoft?.copy(autoSync = on)) }
    }

    fun syncMicrosoft() = work("microsoft", this::microsoftError) {
        val at = CalendarRepository.microsoftSync() ?: java.time.Instant.now().toString()
        _state.update { it.copy(microsoft = it.microsoft?.copy(syncedDate = at)) }
        Snack.show("Outlook is up to date")
    }

    fun disconnectMicrosoft() = work("microsoft", this::microsoftError) {
        CalendarRepository.microsoftDisconnect()
        _state.update { it.copy(microsoft = null) }
        Snack.show("Outlook disconnected")
    }

    fun rotateFeed() {
        if (_state.value.busy != null) return
        _state.update { it.copy(busy = "feed") }
        viewModelScope.launch {
            try {
                val token = CalendarRepository.rotateFeed()
                _state.update { it.copy(feedToken = token, feedError = null) }
                Snack.show("New link made. Calendars using the old one stop updating.")
            } catch (e: Exception) {
                Snack.show("Could not reset the link. ${e.friendly("")}".trim())
            } finally {
                _state.update { it.copy(busy = null) }
            }
        }
    }
}
