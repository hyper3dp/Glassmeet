@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.hyper3dp.glassmeet.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hyper3dp.glassmeet.util.Format
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** A calendar dialog; dates before [notBefore] can't be picked. */
@Composable
fun DatePickDialog(initial: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit, notBefore: LocalDate? = LocalDate.now()) {
    val startMillis = notBefore?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = startMillis == null || utcTimeMillis >= startMillis
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    onDismiss()
                },
                enabled = state.selectedDateMillis != null,
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}

/** A field that shows a date and opens the calendar. */
@Composable
fun DateField(label: String, date: LocalDate?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(modifier) {
        OutlinedTextField(
            value = date?.let { Format.shortDate(it) } ?: "", onValueChange = {}, readOnly = true, singleLine = true,
            label = { Text(label) }, placeholder = { Text("Pick a date") }, modifier = Modifier.fillMaxWidth(),
        )
        androidx.compose.foundation.layout.Box(
            Modifier.matchParentSize().padding(top = 8.dp).clickable(onClick = onClick),
        )
    }
}

/**
 * A time of day (minutes after midnight) that opens the system clock picker. With
 * [allowMidnight], 00:00 means the end of the day (1440), for "To" times.
 */
@Composable
fun TimeField(label: String, minutes: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier, allowMidnight: Boolean = false, enabled: Boolean = true) {
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.foundation.layout.Box(modifier) {
        OutlinedTextField(
            value = Format.clock(minutes), onValueChange = {}, readOnly = true, singleLine = true, enabled = enabled,
            label = { Text(label) }, modifier = Modifier.fillMaxWidth(),
            trailingIcon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Schedule, contentDescription = null) },
        )
        androidx.compose.foundation.layout.Box(
            Modifier.matchParentSize().padding(top = 8.dp).clickable(enabled = enabled, onClickLabel = "Change $label") { open = true },
        )
    }
    if (open) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val start = minutes.coerceIn(0, 1440) % 1440
        val state = androidx.compose.material3.rememberTimePickerState(
            initialHour = start / 60, initialMinute = start % 60, is24Hour = android.text.format.DateFormat.is24HourFormat(context),
        )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = { androidx.compose.material3.TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    val picked = state.hour * 60 + state.minute
                    onPick(if (allowMidnight && picked == 0) 1440 else picked)
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}

/** A whole-number field. */
@Composable
fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, suffix: String? = null, supporting: String? = null) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(Format.digits(it).take(4)) }, singleLine = true,
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        supportingText = supporting?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** Label with a switch on the right; the whole row toggles. 56 dp tall. */
@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** A row of single-choice chips. */
@Composable
fun <T> ChoiceChips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}
