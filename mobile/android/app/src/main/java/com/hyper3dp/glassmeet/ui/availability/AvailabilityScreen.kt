@file:OptIn(ExperimentalMaterial3Api::class)

package com.hyper3dp.glassmeet.ui.availability

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import com.hyper3dp.glassmeet.data.Messages
import com.hyper3dp.glassmeet.util.Intents
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hyper3dp.glassmeet.data.AvailabilityException
import com.hyper3dp.glassmeet.ui.components.ChoiceChips
import com.hyper3dp.glassmeet.ui.components.ConfirmDialog
import com.hyper3dp.glassmeet.ui.components.DateField
import com.hyper3dp.glassmeet.ui.components.DatePickDialog
import com.hyper3dp.glassmeet.ui.components.HintText
import com.hyper3dp.glassmeet.ui.components.LoadableContent
import com.hyper3dp.glassmeet.ui.components.SectionCard
import com.hyper3dp.glassmeet.ui.components.SectionTitle
import com.hyper3dp.glassmeet.ui.components.SelectField
import com.hyper3dp.glassmeet.ui.components.SwitchRow
import com.hyper3dp.glassmeet.ui.components.TopLevelScaffold
import com.hyper3dp.glassmeet.ui.components.timeOptions
import com.hyper3dp.glassmeet.ui.scheduling.WEEKDAYS
import com.hyper3dp.glassmeet.ui.theme.AppTheme
import com.hyper3dp.glassmeet.util.Format
import java.time.LocalDate

private val DAY_NAMES = mapOf(0 to "Sunday", 1 to "Monday", 2 to "Tuesday", 3 to "Wednesday", 4 to "Thursday", 5 to "Friday", 6 to "Saturday")

@Composable
fun AvailabilityScreen() {
    val vm: AvailabilityViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<AvailabilityException?>(null) }
    var cancelling by remember { mutableStateOf<Clash?>(null) }
    val context = LocalContext.current

    TopLevelScaffold(
        title = "Availability",
        actions = { IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, contentDescription = "Add a date override") } },
    ) { inner ->
        LoadableContent(state, onRetry = { vm.load() }, onRefresh = vm::refresh, modifier = Modifier.padding(top = inner.calculateTopPadding())) { d ->
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)) {
                item {
                    SectionTitle("Weekly hours")
                    HintText("When guests can book you. Times are in ${d.zone.id}.", Modifier.padding(start = 4.dp, bottom = 8.dp))
                    SectionCard(padding = 0.dp) {
                        WEEKDAYS.forEachIndexed { i, (dow, _) ->
                            if (i > 0) HorizontalDivider(color = AppTheme.tokens.border)
                            DayRow(
                                name = DAY_NAMES.getValue(dow),
                                ranges = d.week[dow].orEmpty(),
                                onToggle = { vm.toggleDay(dow, it) },
                                onAdd = { vm.addRange(dow) },
                                onChange = { index, s, e -> vm.updateRange(dow, index, s, e) },
                                onRemove = { index -> vm.removeRange(dow, index) },
                            )
                        }
                    }
                }

                item {
                    SectionTitle("Date overrides", action = {
                        TextButton(onClick = { adding = true }) { Text("Add") }
                    })
                    SectionCard(padding = 0.dp) {
                        if (d.exceptions.isEmpty()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("No upcoming overrides", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Take a day off, block a few hours, or open extra hours on one date.",
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            d.exceptions.forEachIndexed { i, e ->
                                if (i > 0) HorizontalDivider(color = AppTheme.tokens.border)
                                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                        val date = runCatching { LocalDate.parse(e.date) }.getOrNull()
                                        Text(date?.let { Format.shortDate(it) } ?: e.date, style = MaterialTheme.typography.titleMedium)
                                        Text(describe(e), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { removing = e }) { Icon(Icons.Outlined.Delete, contentDescription = "Remove override") }
                                }
                            }
                        }
                    }
                }

                item {
                    SectionTitle("Daily limit")
                    SectionCard {
                        SwitchRow(
                            "Limit meetings per day", d.dailyLimit != null,
                            { on -> vm.setDailyLimit(if (on) 5 else null) },
                            subtitle = "Across all your event types",
                        )
                        val limit = d.dailyLimit
                        if (limit != null) {
                            Spacer(Modifier.height(8.dp))
                            SelectField(
                                "Most meetings in a day", (1..100).map { it to it.toString() }, limit,
                                { vm.setDailyLimit(it) }, Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                item {
                    SectionTitle("Holidays")
                    SectionCard {
                        SwitchRow(
                            "UAE public holidays", d.holidaysOn, vm::setHolidays,
                            subtitle = "Guests can't book on UAE and Islamic holidays",
                        )
                        if (d.holidaysOn) {
                            if (d.clashes.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text("Meetings already booked on a holiday", style = MaterialTheme.typography.labelLarge, color = AppTheme.tokens.warning)
                                d.clashes.forEach { c ->
                                    ClashRow(
                                        c, d.zone,
                                        onCancel = { cancelling = c },
                                        onMessage = {
                                            Intents.open(context, Messages.whatsAppLink(c.booking.guestPhone, Messages.rescheduleMessage(c.booking, c.holiday, d.zone)))
                                        },
                                    )
                                }
                            }
                            val next = d.holidays.take(3)
                            if (next.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text("Coming up", style = MaterialTheme.typography.labelLarge)
                                next.forEach { h ->
                                    val date = runCatching { LocalDate.parse(h.date) }.getOrNull()
                                    Text(
                                        "${date?.let { Format.shortDate(it) } ?: h.date} · ${h.name}",
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        AddOverrideSheet(
            onSave = { date, mode, start, end, label -> vm.addException(date, mode, start, end, label); adding = false },
            onDismiss = { adding = false },
        )
    }
    cancelling?.let { c ->
        ConfirmDialog(
            "Cancel this meeting?",
            "${c.booking.name}'s meeting falls on ${c.holiday}. After cancelling, send them a message with a link to pick a new time.",
            "Cancel meeting", dismissLabel = "Keep it",
            onConfirm = { cancelling = null; vm.cancelClash(c) }, onDismiss = { cancelling = null },
        )
    }
    removing?.let { e ->
        ConfirmDialog(
            "Remove this override?", "Your weekly hours apply on ${e.date} again.", "Remove",
            onConfirm = { removing = null; vm.deleteException(e) }, onDismiss = { removing = null }, dismissLabel = "Cancel",
        )
    }
}

@Composable
private fun ClashRow(c: Clash, zone: java.time.ZoneId, onCancel: () -> Unit, onMessage: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(c.booking.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "${Format.dayAndTime(c.booking.start, zone)} · ${c.holiday}", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (c.booking.isCancelled) {
                Text("Cancelled", style = MaterialTheme.typography.labelLarge, color = AppTheme.tokens.danger, modifier = Modifier.padding(top = 12.dp))
            } else {
                TextButton(onClick = onCancel) { Text("Cancel meeting", color = MaterialTheme.colorScheme.error) }
            }
            TextButton(onClick = onMessage) { Text("Message on WhatsApp") }
        }
    }
}

private fun describe(e: AvailabilityException): String {
    val times = if (e.startTime != null && e.endTime != null) "${Format.clock(e.startTime)} – ${Format.clock(e.endTime)}" else null
    val what = when {
        e.type == "available" -> "Available ${times ?: ""}".trim()
        times == null -> "Day off"
        else -> "Blocked $times"
    }
    return if (e.label.isNullOrBlank()) what else "$what · ${e.label}"
}

@Composable
private fun DayRow(
    name: String,
    ranges: List<Range>,
    onToggle: (Boolean) -> Unit,
    onAdd: () -> Unit,
    onChange: (Int, Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    val on = ranges.isNotEmpty()
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (!on) Text("Unavailable", style = MaterialTheme.typography.bodyMedium, color = AppTheme.tokens.textTertiary, modifier = Modifier.padding(end = 12.dp))
            Switch(checked = on, onCheckedChange = onToggle)
        }
        ranges.forEachIndexed { i, r ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                SelectField("From", timeOptions(allowMidnight = false), r.start, { s -> onChange(i, s, r.end) }, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                SelectField("To", timeOptions(allowMidnight = true, from = 15), r.end, { e -> onChange(i, r.start, e) }, Modifier.weight(1f))
                IconButton(onClick = { onRemove(i) }) { Icon(Icons.Filled.Close, contentDescription = "Remove these hours") }
            }
        }
        if (on) {
            TextButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add hours")
            }
        }
    }
}

@Composable
private fun AddOverrideSheet(onSave: (LocalDate, String, Int, Int, String) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var date by remember { mutableStateOf<LocalDate?>(null) }
    var mode by remember { mutableStateOf("off") }
    var start by remember { mutableIntStateOf(9 * 60) }
    var end by remember { mutableIntStateOf(17 * 60) }
    var label by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = AppTheme.tokens.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Date override", style = MaterialTheme.typography.titleLarge)
            DateField("Date", date, onClick = { picking = true }, modifier = Modifier.fillMaxWidth())
            ChoiceChips(listOf("off" to "Day off", "block" to "Block hours", "custom" to "Custom hours"), mode) { mode = it }
            if (mode != "off") {
                Row {
                    SelectField("From", timeOptions(allowMidnight = false), start, { start = it }, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    SelectField("To", timeOptions(allowMidnight = true, from = 15), end, { end = it }, Modifier.weight(1f))
                }
                HintText(if (mode == "block") "Guests can't book these hours on this date." else "Only these hours are open on this date.")
            }
            OutlinedTextField(label, { label = it.take(80) }, label = { Text("Label (optional)") }, placeholder = { Text("Dentist") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { date?.let { onSave(it, mode, start, end, label) } },
                enabled = date != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save") }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (picking) DatePickDialog(date, { date = it }, { picking = false })
}
