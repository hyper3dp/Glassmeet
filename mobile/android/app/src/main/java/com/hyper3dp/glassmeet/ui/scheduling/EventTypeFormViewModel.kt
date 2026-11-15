package com.hyper3dp.glassmeet.ui.scheduling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyper3dp.glassmeet.data.AvailabilityRepository
import com.hyper3dp.glassmeet.data.CustomQuestion
import com.hyper3dp.glassmeet.data.EventType
import com.hyper3dp.glassmeet.data.EventTypesRepository
import com.hyper3dp.glassmeet.data.SavedAddress
import com.hyper3dp.glassmeet.data.friendly
import com.hyper3dp.glassmeet.ui.navigation.EventTypeSeed
import com.hyper3dp.glassmeet.util.Format
import com.hyper3dp.glassmeet.util.Snack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate

/** Where the meeting happens (web: Event types.jsx LOCATIONS). */
enum class LocationKind { Video, Phone, InPerson, Other }

val LOCATIONS = listOf(
    "Google Meet" to LocationKind.Video,
    "Zoom" to LocationKind.Video,
    "Microsoft Teams" to LocationKind.Video,
    "WhatsApp call" to LocationKind.Phone,
    "Phone call" to LocationKind.Phone,
    "In person" to LocationKind.InPerson,
    "Other" to LocationKind.Other,
)
val DURATIONS = listOf(15, 30, 45, 60, 90)
val NOTICE_UNITS = listOf("min" to 1, "hours" to 60, "days" to 1440)

data class QuestionForm(val text: String = "", val type: String = "text", val required: Boolean = false, val options: String = "") {
    val hasChoices get() = type == "dropdown" || type == "multiple_choice"
    fun cleanOptions() = options.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(20)
}

/** The form's values, close to the web form's state. Numbers are kept as typed and checked on save. */
data class EventForm(
    val kind: String = "one_on_one", // one_on_one | group | one_off
    val name: String = "",
    val description: String = "",
    val note: String = "",
    val duration: String = "30",
    val customDuration: Boolean = false,
    val location: String = "Google Meet",
    val customLocation: String = "",
    val meetingUrl: String = "",
    val address: String = "",
    val addressChoice: String = "new",
    val newAddressLabel: String = "",
    val saveNewAddress: Boolean = true,
    val avail: String = "hours", // hours | custom
    val schedule: String = "weekly", // weekly | dates
    val freq: String = "week",
    val every: String = "1",
    val days: Set<Int> = (0..6).toSet(),
    val range: String = "rolling", // rolling | dates
    val maxFutureDays: String = "60",
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    val dates: List<LocalDate> = emptyList(),
    val times: String = "hours", // hours | fixed
    val fixedTimes: List<Int> = listOf(9 * 60),
    val noticeValue: String = "30",
    val noticeUnit: String = "min",
    val limitOn: Boolean = false,
    val maxPerDay: String = "5",
    val bufferBefore: String = "0",
    val bufferAfter: String = "0",
    val questions: List<QuestionForm> = emptyList(),
    val emailRequired: Boolean = true,
    val requiresApproval: Boolean = false,
    val maxAttendees: String = "2",
    val active: Boolean = true,
) {
    val locationKind: LocationKind get() = LOCATIONS.firstOrNull { it.first == location }?.second ?: LocationKind.Other
    val oneOff get() = kind == "one_off"
    val usesHours get() = !oneOff && avail == "hours"
    val effectiveSchedule get() = if (oneOff) "dates" else if (usesHours) "weekly" else schedule
    val repeats get() = effectiveSchedule == "weekly"
    val fixed get() = oneOff || (!usesHours && times == "fixed")
    val effectiveFreq get() = if (usesHours) "week" else freq

    companion object {
        fun from(src: EventType?, editing: Boolean): EventForm {
            if (src == null) return EventForm()
            val known = LOCATIONS.any { it.first == src.location }
            val days = src.days?.filter { it in 0..6 }?.toSet()?.takeIf { it.isNotEmpty() }
            val freq = if (src.freq in listOf("day", "week", "month")) src.freq else "week"
            val notice = if (editing) src.minNotice else (src.minNotice.takeIf { it > 0 } ?: 30)
            val unit = when { notice > 0 && notice % 1440 == 0 -> "days"; notice > 0 && notice % 60 == 0 -> "hours"; else -> "min" }
            val fixed = src.fixedTimes.orEmpty().mapNotNull { Format.minutesOf(it) }.sorted()
            val custom = src.schedule == "dates" || fixed.isNotEmpty() || freq != "week" || src.every > 1 || (days != null && days.size < 7)
            return EventForm(
                kind = if (src.isGroup) "group" else if (src.schedule == "dates" && fixed.isNotEmpty()) "one_off" else "one_on_one",
                name = src.name,
                description = src.description.orEmpty().take(500),
                note = src.note.orEmpty(),
                duration = src.duration.toString(),
                customDuration = src.duration !in DURATIONS,
                location = if (known) src.location!! else if (!src.location.isNullOrBlank()) "Other" else "Google Meet",
                customLocation = if (known) "" else src.location.orEmpty(),
                meetingUrl = src.meetingUrl.orEmpty(),
                address = src.address.orEmpty(),
                avail = if (custom) "custom" else "hours",
                schedule = if (src.schedule == "dates") "dates" else "weekly",
                freq = freq,
                every = src.every.coerceAtLeast(1).toString(),
                days = days ?: (0..6).toSet(),
                range = if (src.endDate != null) "dates" else "rolling",
                maxFutureDays = src.maxFutureDays.takeIf { it > 0 }?.toString() ?: "60",
                startDate = src.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now(),
                endDate = src.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                dates = src.dates.orEmpty().mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sorted(),
                times = if (fixed.isNotEmpty()) "fixed" else "hours",
                fixedTimes = fixed.ifEmpty { listOf(9 * 60) },
                noticeValue = (if (notice == 0) 0 else notice / NOTICE_UNITS.first { it.first == unit }.second).toString(),
                noticeUnit = unit,
                limitOn = (src.maxPerDay ?: 0) > 0,
                maxPerDay = (src.maxPerDay ?: 5).toString(),
                bufferBefore = src.bufferBefore.toString(),
                bufferAfter = src.bufferAfter.toString(),
                questions = src.customQuestions.map { QuestionForm(it.text, it.type, it.required, it.options.joinToString("\n")) },
                emailRequired = src.emailRequired,
                requiresApproval = src.requiresApproval,
                maxAttendees = (if (src.maxAttendees > 1) src.maxAttendees else 2).toString(),
                active = if (editing) src.active else true,
            )
        }
    }
}

data class FormUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val editing: Boolean = false,
    val form: EventForm = EventForm(),
    val initial: EventForm = EventForm(),
    val addresses: List<SavedAddress> = emptyList(),
    val hostDays: Set<Int> = emptySet(),
    val saving: Boolean = false,
    val error: String? = null,
    val errorSection: String? = null,
    val done: Boolean = false,
) {
    val dirty get() = form != initial
}

class EventTypeFormViewModel(private val id: String?) : ViewModel() {
    private val _state = MutableStateFlow(FormUiState(editing = id != null))
    val state: StateFlow<FormUiState> = _state.asStateFlow()
    private val seed = EventTypeSeed.data.also { EventTypeSeed.data = null }

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            try {
                val src = if (id != null) EventTypesRepository.get(id) ?: throw Exception("This event type may have been deleted.")
                else EventTypesRepository.fromTemplate(seed)
                var form = EventForm.from(src, editing = id != null)
                val addresses = EventTypesRepository.addresses()
                val hostDays = runCatching { AvailabilityRepository.rules().map { it.dayOfWeek }.toSet() }.getOrDefault(emptySet())
                // saved address prefill, and a new event's days follow the host's working days
                val match = addresses.firstOrNull { it.address == form.address }
                form = when {
                    match != null -> form.copy(addressChoice = match.id)
                    form.address.isBlank() && addresses.isNotEmpty() -> form.copy(addressChoice = addresses[0].id, address = addresses[0].address)
                    else -> form
                }
                if (id == null && src?.days.isNullOrEmpty() && hostDays.isNotEmpty()) form = form.copy(days = hostDays)
                _state.update { it.copy(loading = false, form = form, initial = form, addresses = addresses, hostDays = hostDays) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loadError = e.friendly("This event type didn't load.")) }
            }
        }
    }

    fun edit(change: (EventForm) -> EventForm) = _state.update { it.copy(form = change(it.form), error = null, errorSection = null) }

    fun pickAddress(choice: String) = edit { f ->
        val a = _state.value.addresses.firstOrNull { it.id == choice }
        f.copy(addressChoice = choice, address = a?.address ?: "")
    }

    private fun fail(message: String, section: String? = null): Boolean {
        _state.update { it.copy(error = message, errorSection = section) }
        return false
    }

    private fun number(text: String, min: Int, max: Int, fallback: Int) = (text.trim().toIntOrNull() ?: fallback).coerceIn(min, max)

    private fun validate(f: EventForm): Boolean {
        if (f.name.isBlank()) return fail("Give the event a name.", "basics")
        if (f.locationKind == LocationKind.InPerson && f.address.isBlank()) return fail("Add the address where you will meet.", "basics")
        if (f.locationKind == LocationKind.Other && f.customLocation.isBlank()) return fail("Describe where the meeting happens.", "basics")
        val days = if (f.usesHours) (0..6).toSet() else f.days
        if (f.repeats && f.effectiveFreq == "week" && days.isEmpty()) return fail("Pick at least one day the event repeats on.", "when")
        if (f.repeats && f.range == "dates" && f.endDate == null) return fail("Pick the last date, or use a rolling range.", "when")
        if (f.repeats && f.range == "dates" && f.endDate != null && f.endDate < f.startDate) return fail("The last date is before the first date.", "when")
        if (!f.repeats && f.dates.isEmpty()) return fail(if (f.oneOff) "Pick the date for this one-off meeting." else "Pick at least one date.", "when")
        val duration = number(f.duration, 5, 720, 30)
        if (f.fixed && f.fixedTimes.isEmpty()) return fail("Add at least one start time.", "when")
        if (f.fixed && f.fixedTimes.any { it + duration > 1440 }) return fail("A start time is too late for a meeting this long to end by midnight.", "when")
        if (f.questions.any { it.text.isBlank() }) return fail("Write each invitee question, or remove the empty one.", "form")
        if (f.questions.any { it.hasChoices && it.cleanOptions().isEmpty() }) return fail("Add the answers guests can choose from.", "form")
        return true
    }

    /** The event type's columns as saved (web: EventModal values()). */
    private fun values(f: EventForm): JsonObject {
        val days = if (f.usesHours) (0..6).toSet() else f.days
        val endDate = if (f.repeats && f.range == "dates") f.endDate else null
        val kind = f.locationKind
        val unit = NOTICE_UNITS.firstOrNull { it.first == f.noticeUnit }?.second ?: 1
        return buildJsonObject {
            put("name", f.name.trim().take(120))
            put("description", f.description.trim().take(500).ifEmpty { null })
            put("note", f.note.trim().take(2000).ifEmpty { null })
            put("duration", number(f.duration, 5, 720, 30))
            put("location", if (kind == LocationKind.Other) f.customLocation.trim() else f.location)
            put("meeting_url", if (kind == LocationKind.Video || kind == LocationKind.Other) f.meetingUrl.trim() else "")
            if (kind == LocationKind.InPerson) put("address", f.address.trim()) else put("address", JsonNull)
            put("booking_type", if (f.kind == "group") "group" else "one_on_one")
            put("max_attendees", if (f.kind == "group") number(f.maxAttendees, 2, 500, 2) else 1)
            put("active", f.active)
            put("requires_approval", f.requiresApproval)
            put("email_required", f.emailRequired)
            put("schedule", f.effectiveSchedule)
            put("freq", f.effectiveFreq)
            put("every", if (f.usesHours) 1 else number(f.every, 1, 52, 1))
            if (f.repeats && f.effectiveFreq == "week" && days.size < 7) put("days", JsonArray(days.sorted().map { JsonPrimitive(it) }))
            else put("days", JsonNull)
            if (f.repeats) put("start_date", f.startDate.toString()) else put("start_date", JsonNull)
            if (endDate != null) put("end_date", endDate.toString()) else put("end_date", JsonNull)
            if (f.repeats) put("dates", JsonNull) else put("dates", JsonArray(f.dates.distinct().sorted().map { JsonPrimitive(it.toString()) }))
            if (f.fixed) put("fixed_times", JsonArray(f.fixedTimes.distinct().sorted().map { JsonPrimitive(Format.hhmm(it)) }))
            else put("fixed_times", JsonNull)
            put("max_future_days", number(f.maxFutureDays, 1, 730, 60))
            put("min_notice", (f.noticeValue.trim().toIntOrNull() ?: 0).coerceAtLeast(0) * unit)
            if (f.limitOn) put("max_per_day", number(f.maxPerDay, 1, 100, 5)) else put("max_per_day", JsonNull)
            put("buffer_before", number(f.bufferBefore, 0, 1440, 0))
            put("buffer_after", number(f.bufferAfter, 0, 1440, 0))
            put("custom_questions", JsonArray(f.questions.take(10).map { q ->
                buildJsonObject {
                    put("text", q.text.trim().take(200))
                    put("type", q.type)
                    put("required", q.required)
                    if (q.hasChoices) put("options", JsonArray(q.cleanOptions().map { JsonPrimitive(it) }))
                }
            }))
        }
    }

    fun save() {
        val s = _state.value
        if (s.saving || !validate(s.form)) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val f = s.form
                if (f.locationKind == LocationKind.InPerson && f.addressChoice == "new" && f.saveNewAddress && f.address.isNotBlank()) {
                    runCatching { EventTypesRepository.addAddress(f.newAddressLabel, f.address) }
                }
                EventTypesRepository.save(id, values(f))
                Snack.show(if (id == null) "Event type created" else "Saved")
                _state.update { it.copy(saving = false, done = true, initial = it.form) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = e.friendly("Could not save this event type.")) }
            }
        }
    }

    fun saveAsTemplate() {
        val f = _state.value.form
        if (f.name.isBlank()) { fail("Give the event a name first; the template uses it.", "basics"); return }
        viewModelScope.launch {
            try {
                EventTypesRepository.saveTemplate(f.name, EventTypesRepository.templateData(values(f)))
                Snack.show("Saved “${f.name.trim()}” as a template")
            } catch (e: Exception) {
                Snack.show("Could not save the template. ${e.friendly("")}".trim())
            }
        }
    }

    fun delete() {
        val target = id ?: return
        viewModelScope.launch {
            try {
                EventTypesRepository.delete(target)
                Snack.show("Event type deleted")
                _state.update { it.copy(done = true, initial = it.form) }
            } catch (e: Exception) {
                Snack.show("Could not delete this event type. ${e.friendly("")}".trim())
            }
        }
    }

    fun questionTypes() = CustomQuestion.TYPES
}
