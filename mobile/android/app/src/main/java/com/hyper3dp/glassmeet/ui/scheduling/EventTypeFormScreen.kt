@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.hyper3dp.glassmeet.ui.scheduling

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hyper3dp.glassmeet.data.CustomQuestion
import com.hyper3dp.glassmeet.ui.components.ChoiceChips
import com.hyper3dp.glassmeet.ui.components.ConfirmDialog
import com.hyper3dp.glassmeet.ui.components.DateField
import com.hyper3dp.glassmeet.ui.components.DatePickDialog
import com.hyper3dp.glassmeet.ui.components.DetailScaffold
import com.hyper3dp.glassmeet.ui.components.ErrorState
import com.hyper3dp.glassmeet.ui.components.ErrorText
import com.hyper3dp.glassmeet.ui.components.HintText
import com.hyper3dp.glassmeet.ui.components.LoadingState
import com.hyper3dp.glassmeet.ui.components.NumberField
import com.hyper3dp.glassmeet.ui.components.SectionCard
import com.hyper3dp.glassmeet.ui.components.SectionTitle
import com.hyper3dp.glassmeet.ui.components.SelectField
import com.hyper3dp.glassmeet.ui.components.SwitchRow
import com.hyper3dp.glassmeet.ui.components.timeOptions
import com.hyper3dp.glassmeet.ui.theme.AppTheme
import com.hyper3dp.glassmeet.util.Format
import java.time.LocalDate

/** Weekdays Monday first; values are day_of_week (0 = Sunday). */
val WEEKDAYS = listOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 0 to "Sun")

@Composable
fun EventTypeFormScreen(id: String?, onBack: () -> Unit) {
    val vm: EventTypeFormViewModel = viewModel(key = "event-${id ?: "new"}") { EventTypeFormViewModel(id) }
    val s by vm.state.collectAsStateWithLifecycle()
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(s.done) { if (s.done) onBack() }
    BackHandler(enabled = s.dirty && !s.saving && !s.done) { confirmDiscard = true }
    val back: () -> Unit = { if (s.dirty && !s.done) { confirmDiscard = true } else { onBack() } }

    DetailScaffold(
        title = if (id == null) "New event type" else "Edit event type",
        onBack = back,
        actions = {
            if (!s.loading && s.loadError == null) {
                TextButton(onClick = vm::save, enabled = !s.saving) {
                    if (s.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Save")
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Save as template") }, onClick = { menu = false; vm.saveAsTemplate() })
                        if (id != null) DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                }
            }
        },
    ) { inner ->
        when {
            s.loading -> LoadingState(Modifier.padding(inner))
            s.loadError != null -> ErrorState(s.loadError ?: "", onRetry = vm::load, modifier = Modifier.padding(inner))
            else -> FormContent(s, vm, Modifier.padding(inner), onDelete = { confirmDelete = true })
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            "Discard changes?", "Your changes to this event type aren't saved.", "Discard",
            onConfirm = { confirmDiscard = false; onBack() }, onDismiss = { confirmDiscard = false }, dismissLabel = "Keep editing",
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            "Delete this event type?", "Its booking link stops working. Meetings already booked stay in Meetings.", "Delete",
            onConfirm = { confirmDelete = false; vm.delete() }, onDismiss = { confirmDelete = false }, dismissLabel = "Cancel",
        )
    }
}

@Composable
private fun FormContent(s: FormUiState, vm: EventTypeFormViewModel, modifier: Modifier, onDelete: () -> Unit) {
    val f = s.form
    val edit = vm::edit
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (s.error != null) {
            SectionCard { ErrorText(s.error) }
        }

        // 1. Basics
        SectionTitle("Basics")
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val kinds = listOf("one_on_one" to "One-on-one", "group" to "Group", "one_off" to "One-off")
                    kinds.forEachIndexed { i, (value, label) ->
                        SegmentedButton(
                            selected = f.kind == value,
                            onClick = { edit { it.copy(kind = value) } },
                            shape = SegmentedButtonDefaults.itemShape(i, kinds.size),
                            label = { Text(label, maxLines = 1) },
                        )
                    }
                }
                HintText(
                    when (f.kind) {
                        "group" -> "Several guests can book the same time."
                        "one_off" -> "A time outside your regular schedule."
                        else -> "One guest per time."
                    },
                )
                OutlinedTextField(
                    f.name, { v -> edit { it.copy(name = v.take(120)) } }, label = { Text("Name") }, singleLine = true,
                    placeholder = { Text("30 minute meeting") }, modifier = Modifier.fillMaxWidth(),
                )
                Text("Duration", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DURATIONS.forEach { d ->
                        FilterChip(
                            selected = !f.customDuration && f.duration == d.toString(),
                            onClick = { edit { it.copy(duration = d.toString(), customDuration = false) } },
                            label = { Text(Format.duration(d)) },
                        )
                    }
                    FilterChip(selected = f.customDuration, onClick = { edit { it.copy(customDuration = true) } }, label = { Text("Custom") })
                }
                if (f.customDuration) {
                    NumberField("Minutes", f.duration, { v -> edit { it.copy(duration = v) } }, Modifier.fillMaxWidth(), suffix = "min", supporting = "5 to 720 minutes")
                }
                SelectField("Location", LOCATIONS.map { it.first to it.first }, f.location, { v -> edit { it.copy(location = v) } }, Modifier.fillMaxWidth())
                when (f.locationKind) {
                    LocationKind.Video -> OutlinedTextField(
                        f.meetingUrl, { v -> edit { it.copy(meetingUrl = v) } }, label = { Text("Meeting link (optional)") }, singleLine = true,
                        supportingText = { Text("Leave empty if your connected calendar makes one for each booking.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
                    )
                    LocationKind.Other -> {
                        OutlinedTextField(
                            f.customLocation, { v -> edit { it.copy(customLocation = v.take(120)) } }, label = { Text("Where") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            f.meetingUrl, { v -> edit { it.copy(meetingUrl = v) } }, label = { Text("Link (optional)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    LocationKind.InPerson -> {
                        if (s.addresses.isNotEmpty()) {
                            SelectField(
                                "Address", s.addresses.map { it.id to (it.label?.takeIf { l -> l.isNotBlank() } ?: it.address) } + ("new" to "New address"),
                                f.addressChoice, vm::pickAddress, Modifier.fillMaxWidth(),
                            )
                        }
                        if (f.addressChoice == "new" || s.addresses.isEmpty()) {
                            OutlinedTextField(
                                f.address, { v -> edit { it.copy(address = v.take(300)) } }, label = { Text("Address") }, minLines = 2,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            SwitchRow("Save this address", f.saveNewAddress, { v -> edit { it.copy(saveNewAddress = v) } })
                            if (f.saveNewAddress) {
                                OutlinedTextField(
                                    f.newAddressLabel, { v -> edit { it.copy(newAddressLabel = v.take(60)) } }, label = { Text("Label (optional)") },
                                    placeholder = { Text("Office") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    LocationKind.Phone -> HintText("You call the guest on the number they give when booking.")
                }
                OutlinedTextField(
                    f.description, { v -> edit { it.copy(description = v.take(500)) } }, label = { Text("Description") }, minLines = 3,
                    supportingText = { Text("${f.description.length}/500 · Guests see this") }, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    f.note, { v -> edit { it.copy(note = v.take(2000)) } }, label = { Text("Private note") }, minLines = 2,
                    supportingText = { Text("Only you see this") }, modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // 2. When
        SectionTitle("When")
        SectionCard { WhenSection(f, edit) }

        // 3. Rules
        SectionTitle("Rules")
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Minimum notice", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumberField("Notice", f.noticeValue, { v -> edit { it.copy(noticeValue = v) } }, Modifier.weight(1f))
                    SelectField("Unit", NOTICE_UNITS.map { it.first to it.first }, f.noticeUnit, { v -> edit { it.copy(noticeUnit = v) } }, Modifier.weight(1f))
                }
                if (f.repeats && f.range == "rolling") {
                    NumberField(
                        "How far ahead", f.maxFutureDays, { v -> edit { it.copy(maxFutureDays = v) } }, Modifier.fillMaxWidth(),
                        suffix = "days", supporting = "Guests can book up to this many days ahead (1 to 730).",
                    )
                }
                SwitchRow("Limit per day", f.limitOn, { v -> edit { it.copy(limitOn = v) } }, subtitle = "Most bookings of this event in one day")
                if (f.limitOn) NumberField("Bookings per day", f.maxPerDay, { v -> edit { it.copy(maxPerDay = v) } }, Modifier.fillMaxWidth(), supporting = "1 to 100")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField("Buffer before", f.bufferBefore, { v -> edit { it.copy(bufferBefore = v) } }, Modifier.weight(1f), suffix = "min")
                    NumberField("Buffer after", f.bufferAfter, { v -> edit { it.copy(bufferAfter = v) } }, Modifier.weight(1f), suffix = "min")
                }
            }
        }

        // 4. Booking form
        SectionTitle("Booking form")
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SwitchRow("Email required", f.emailRequired, { v -> edit { it.copy(emailRequired = v) } }, subtitle = "Guests always give their name and WhatsApp number")
                f.questions.forEachIndexed { i, q ->
                    HorizontalDivider(color = AppTheme.tokens.border)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Question ${i + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { edit { it.copy(questions = it.questions.filterIndexed { j, _ -> j != i }) } }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove question ${i + 1}")
                        }
                    }
                    fun setQ(change: (QuestionForm) -> QuestionForm) = edit { it.copy(questions = it.questions.mapIndexed { j, x -> if (j == i) change(x) else x }) }
                    OutlinedTextField(q.text, { v -> setQ { it.copy(text = v.take(200)) } }, label = { Text("Your question") }, modifier = Modifier.fillMaxWidth())
                    SelectField("Answer type", CustomQuestion.TYPES, q.type, { v -> setQ { it.copy(type = v) } }, Modifier.fillMaxWidth())
                    if (q.hasChoices) {
                        OutlinedTextField(
                            q.options, { v -> setQ { it.copy(options = v.take(2000)) } }, label = { Text("Answers") }, minLines = 3,
                            supportingText = { Text("One answer per line") }, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    SwitchRow("Required", q.required, { v -> setQ { it.copy(required = v) } })
                }
                if (f.questions.size < 10) {
                    OutlinedButton(onClick = { edit { it.copy(questions = it.questions + QuestionForm()) } }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add a question")
                    }
                } else HintText("Up to 10 questions.")
            }
        }

        // 5. Options
        SectionTitle("Options")
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SwitchRow("Requires approval", f.requiresApproval, { v -> edit { it.copy(requiresApproval = v) } }, subtitle = "Bookings wait as requests until you approve them")
                SwitchRow("On", f.active, { v -> edit { it.copy(active = v) } }, subtitle = "Guests can book this event type")
                if (f.kind == "group") {
                    NumberField("Group size", f.maxAttendees, { v -> edit { it.copy(maxAttendees = v) } }, Modifier.fillMaxWidth(), supporting = "Guests per time, 2 to 500")
                }
            }
        }

        if (s.error != null) ErrorText(s.error)
        Button(onClick = vm::save, enabled = !s.saving, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (s.saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Text(if (s.editing) "Save changes" else "Create event type")
        }

        // 6. Danger
        if (s.editing) {
            SectionTitle("Danger")
            SectionCard {
                Text("Deleting stops its booking link. Meetings already booked stay.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete event type")
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun WhenSection(f: EventForm, edit: ((EventForm) -> EventForm) -> Unit) {
    var picking by remember { mutableStateOf<String?>(null) } // "start" | "end" | "date"
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!f.oneOff) {
            ChoiceChips(listOf("hours" to "Use my working hours", "custom" to "Custom"), f.avail) { v -> edit { it.copy(avail = v) } }
        }
        if (f.usesHours) {
            HintText("Guests can book any free time inside your weekly hours (Availability tab).")
        } else {
            if (!f.oneOff) {
                ChoiceChips(listOf("weekly" to "Repeats", "dates" to "Specific dates"), f.schedule) { v -> edit { it.copy(schedule = v) } }
            }
            if (f.repeats) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumberField("Every", f.every, { v -> edit { it.copy(every = v) } }, Modifier.weight(1f))
                    SelectField(
                        "Repeat", listOf("day" to "Day(s)", "week" to "Week(s)", "month" to "Month(s)"), f.freq,
                        { v -> edit { it.copy(freq = v) } }, Modifier.weight(1.4f),
                    )
                }
                if (f.freq == "week") {
                    Text("On", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WEEKDAYS.forEach { (dow, label) ->
                            FilterChip(
                                selected = dow in f.days,
                                onClick = { edit { it.copy(days = if (dow in it.days) it.days - dow else it.days + dow) } },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                DateField("Starts", f.startDate, onClick = { picking = "start" }, modifier = Modifier.fillMaxWidth())
                ChoiceChips(listOf("rolling" to "Rolling window", "dates" to "Until a date"), f.range) { v -> edit { it.copy(range = v) } }
                if (f.range == "dates") DateField("Last date", f.endDate, onClick = { picking = "end" }, modifier = Modifier.fillMaxWidth())
            } else {
                Text(if (f.oneOff) "Date" else "Dates", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    f.dates.forEach { d ->
                        InputChip(
                            selected = false,
                            onClick = { edit { it.copy(dates = it.dates - d) } },
                            label = { Text(Format.shortDate(d)) },
                            trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
                if (!f.oneOff || f.dates.isEmpty()) {
                    OutlinedButton(onClick = { picking = "date" }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (f.oneOff) "Pick the date" else "Add a date")
                    }
                }
            }

            if (!f.oneOff) {
                ChoiceChips(listOf("hours" to "Any free time", "fixed" to "Fixed start times"), f.times) { v -> edit { it.copy(times = v) } }
            }
            if (f.fixed) {
                Text("Start times", style = MaterialTheme.typography.labelLarge)
                f.fixedTimes.forEachIndexed { i, t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SelectField(
                            "Start ${i + 1}", timeOptions(allowMidnight = false), t,
                            { v -> edit { it.copy(fixedTimes = it.fixedTimes.mapIndexed { j, x -> if (j == i) v else x }) } },
                            Modifier.weight(1f),
                        )
                        if (f.fixedTimes.size > 1) {
                            IconButton(onClick = { edit { it.copy(fixedTimes = it.fixedTimes.filterIndexed { j, _ -> j != i }) } }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove start time ${i + 1}")
                            }
                        }
                    }
                }
                if (f.fixedTimes.size < 48) {
                    TextButton(onClick = { edit { it.copy(fixedTimes = it.fixedTimes + ((it.fixedTimes.maxOrNull() ?: 480) + 60).coerceAtMost(1425)) } }) {
                        Text("+ Add a start time")
                    }
                }
            }
        }
    }

    when (picking) {
        "start" -> DatePickDialog(f.startDate, { d -> edit { it.copy(startDate = d) } }, { picking = null })
        "end" -> DatePickDialog(f.endDate ?: f.startDate, { d -> edit { it.copy(endDate = d) } }, { picking = null }, notBefore = f.startDate)
        "date" -> DatePickDialog(null, { d -> edit { it.copy(dates = (it.dates + d).distinct().sorted()) } }, { picking = null })
    }
}
