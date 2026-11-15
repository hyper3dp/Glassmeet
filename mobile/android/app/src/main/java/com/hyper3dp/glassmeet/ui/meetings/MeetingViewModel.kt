package com.hyper3dp.glassmeet.ui.meetings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyper3dp.glassmeet.data.Booking
import com.hyper3dp.glassmeet.data.BookingsRepository
import com.hyper3dp.glassmeet.data.EventTypesRepository
import com.hyper3dp.glassmeet.data.Messages
import com.hyper3dp.glassmeet.data.ProfileRepository
import com.hyper3dp.glassmeet.data.WhatsAppRepository
import com.hyper3dp.glassmeet.data.WhatsAppSettings
import com.hyper3dp.glassmeet.data.friendly
import com.hyper3dp.glassmeet.util.Format
import com.hyper3dp.glassmeet.util.Snack
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId

enum class SaveState { Idle, Saving, Saved, Failed }

data class MeetingUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val booking: Booking? = null,
    val eventColor: String? = null,
    val zone: ZoneId = ZoneId.systemDefault(),
    val hostName: String = "",
    val notes: String = "",
    val notesSave: SaveState = SaveState.Idle,
    val link: String = "",
    val linkSave: SaveState = SaveState.Idle,
    val kind: Messages.Kind = Messages.Kind.Confirmation,
    val message: String = "",
    val busy: Boolean = false,
    val confirm: String? = null, // "cancel" | "decline"
)

class MeetingViewModel(private val id: String) : ViewModel() {
    private val _state = MutableStateFlow(MeetingUiState())
    val state: StateFlow<MeetingUiState> = _state.asStateFlow()
    private var settings: WhatsAppSettings? = null
    private var notesJob: Job? = null
    private var linkJob: Job? = null

    init { load() }

    fun load() {
        _state.update { it.copy(loading = it.booking == null, error = null) }
        viewModelScope.launch {
            try {
                val profile = ProfileRepository.ensureLoaded()
                val booking = BookingsRepository.get(id) ?: throw Exception("This meeting is no longer listed.")
                settings = WhatsAppRepository.cachedOrLoad()
                val color = booking.eventTypeId?.let { runCatching { EventTypesRepository.get(it)?.color }.getOrNull() }
                val kind = Messages.defaultKind(booking)
                _state.update {
                    it.copy(
                        loading = false, booking = booking, eventColor = color, zone = Format.zone(profile.timezone),
                        hostName = ProfileRepository.hostName, notes = booking.notes.orEmpty(), link = booking.meetingUrl.orEmpty(),
                        kind = kind,
                    )
                }
                regenerate()
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.friendly("This meeting didn't load.")) }
            }
        }
    }

    private fun regenerate() {
        val s = _state.value
        val b = s.booking ?: return
        val withLink = b.copy(meetingUrl = s.link.ifBlank { null })
        val text = Messages.fill(Messages.template(s.kind, settings), Messages.context(withLink, s.hostName, s.zone))
        _state.update { it.copy(message = text) }
    }

    fun setKind(kind: Messages.Kind) {
        _state.update { it.copy(kind = kind) }
        regenerate()
    }

    fun setMessage(text: String) = _state.update { it.copy(message = text) }

    // ── Autosave: your notes and the meeting link save shortly after you stop typing ──

    fun setNotes(text: String) {
        _state.update { it.copy(notes = text.take(5000), notesSave = SaveState.Saving) }
        notesJob?.cancel()
        notesJob = viewModelScope.launch {
            delay(900)
            try {
                BookingsRepository.saveNotes(id, _state.value.notes)
                _state.update { it.copy(notesSave = SaveState.Saved) }
            } catch (e: Exception) {
                _state.update { it.copy(notesSave = SaveState.Failed) }
            }
        }
    }

    fun setLink(text: String) {
        _state.update { it.copy(link = text.take(500), linkSave = SaveState.Saving) }
        regenerate()
        linkJob?.cancel()
        linkJob = viewModelScope.launch {
            delay(1200)
            try {
                val saved = BookingsRepository.saveMeetingUrl(id, _state.value.link)
                _state.update { it.copy(linkSave = SaveState.Saved, booking = saved.copy(notes = it.booking?.notes)) }
            } catch (e: Exception) {
                _state.update { it.copy(linkSave = SaveState.Failed) }
            }
        }
    }

    // ── Status changes: each one is copied to the host's calendars (pushBooking) ──

    fun askCancel() = _state.update { it.copy(confirm = "cancel") }
    fun askDecline() = _state.update { it.copy(confirm = "decline") }
    fun dismissConfirm() = _state.update { it.copy(confirm = null) }

    fun approve() = change("Could not approve this request.", Messages.Kind.Confirmation) { BookingsRepository.approve(id) }

    fun confirmChange() {
        val what = _state.value.confirm
        _state.update { it.copy(confirm = null) }
        when (what) {
            "cancel" -> change("Could not cancel this meeting.", Messages.Kind.Cancellation) { BookingsRepository.cancel(id) }
            "decline" -> change("Could not decline this request.", Messages.Kind.Decline) { BookingsRepository.decline(id) }
        }
    }

    private fun change(failure: String, thenMessage: Messages.Kind, work: suspend () -> Booking) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val saved = work()
                _state.update { it.copy(busy = false, booking = saved, kind = thenMessage) }
                regenerate()
                Snack.show(
                    when (thenMessage) {
                        Messages.Kind.Confirmation -> "Approved. Let ${saved.name} know below."
                        Messages.Kind.Cancellation -> "Meeting cancelled. Let ${saved.name} know below."
                        else -> "Request declined. Let ${saved.name} know below."
                    },
                )
            } catch (e: Exception) {
                _state.update { it.copy(busy = false) }
                Snack.show("$failure ${e.friendly("")}".trim())
            }
        }
    }
}
