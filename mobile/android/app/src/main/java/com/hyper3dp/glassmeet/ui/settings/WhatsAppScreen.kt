@file:OptIn(ExperimentalLayoutApi::class)

package com.hyper3dp.glassmeet.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hyper3dp.glassmeet.data.Messages
import com.hyper3dp.glassmeet.data.ProfileRepository
import com.hyper3dp.glassmeet.data.WhatsAppRepository
import com.hyper3dp.glassmeet.data.friendly
import com.hyper3dp.glassmeet.ui.components.DetailScaffold
import com.hyper3dp.glassmeet.ui.components.ErrorState
import com.hyper3dp.glassmeet.ui.components.ErrorText
import com.hyper3dp.glassmeet.ui.components.HintText
import com.hyper3dp.glassmeet.ui.components.LoadingState
import com.hyper3dp.glassmeet.ui.components.SectionCard
import com.hyper3dp.glassmeet.ui.components.SectionTitle
import com.hyper3dp.glassmeet.ui.theme.AppTheme
import com.hyper3dp.glassmeet.util.Snack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WhatsAppUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val phone: String = "",
    val confirmation: TextFieldValue = TextFieldValue(Messages.CONFIRMATION),
    val reminder24h: TextFieldValue = TextFieldValue(Messages.REMINDER_24H),
    val reminder1h: TextFieldValue = TextFieldValue(Messages.REMINDER_1H),
    val saving: Boolean = false,
    val saveError: String? = null,
    val hostName: String = "",
)

class WhatsAppViewModel : ViewModel() {
    private val _state = MutableStateFlow(WhatsAppUiState())
    val state: StateFlow<WhatsAppUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val profile = runCatching { ProfileRepository.ensureLoaded() }.getOrNull()
                val s = WhatsAppRepository.get()
                _state.update {
                    it.copy(
                        loading = false,
                        phone = s?.phoneNumber ?: profile?.whatsappNumber.orEmpty(),
                        confirmation = TextFieldValue(Messages.template(Messages.Kind.Confirmation, s)),
                        reminder24h = TextFieldValue(Messages.template(Messages.Kind.Reminder24h, s)),
                        reminder1h = TextFieldValue(Messages.template(Messages.Kind.Reminder1h, s)),
                        hostName = ProfileRepository.hostName,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.friendly("Your WhatsApp settings didn't load.")) }
            }
        }
    }

    fun setPhone(v: String) = _state.update { it.copy(phone = v.filter { c -> c.isDigit() || c in "+ -()" }.take(24), saveError = null) }
    fun set(kind: Messages.Kind, v: TextFieldValue) = _state.update {
        val t = if (v.text.length > 2000) v.copy(text = v.text.take(2000)) else v
        when (kind) {
            Messages.Kind.Reminder24h -> it.copy(reminder24h = t)
            Messages.Kind.Reminder1h -> it.copy(reminder1h = t)
            else -> it.copy(confirmation = t)
        }
    }

    /** Inserts a placeholder at the cursor. */
    fun insert(kind: Messages.Kind, placeholder: String) {
        val s = _state.value
        val v = when (kind) { Messages.Kind.Reminder24h -> s.reminder24h; Messages.Kind.Reminder1h -> s.reminder1h; else -> s.confirmation }
        val start = v.selection.min.coerceIn(0, v.text.length)
        val end = v.selection.max.coerceIn(0, v.text.length)
        val text = v.text.substring(0, start) + placeholder + v.text.substring(end)
        set(kind, TextFieldValue(text, TextRange(start + placeholder.length)))
    }

    fun reset(kind: Messages.Kind) = set(kind, TextFieldValue(Messages.template(kind, null)))

    fun save() {
        val s = _state.value
        val digits = s.phone.filter { it.isDigit() }
        if (s.phone.isNotBlank() && digits.length !in 7..15) {
            _state.update { it.copy(saveError = "Enter the number with its country code, like +971 50 123 4567.") }
            return
        }
        _state.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            try {
                WhatsAppRepository.save(s.phone, s.confirmation.text, s.reminder24h.text, s.reminder1h.text)
                _state.update { it.copy(saving = false) }
                Snack.show("WhatsApp settings saved")
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, saveError = e.friendly("Could not save.")) }
            }
        }
    }
}

@Composable
fun WhatsAppScreen(onBack: () -> Unit) {
    val vm: WhatsAppViewModel = viewModel()
    val s by vm.state.collectAsStateWithLifecycle()

    DetailScaffold(
        title = "WhatsApp messages",
        onBack = onBack,
        actions = {
            if (!s.loading && s.error == null) {
                TextButton(onClick = vm::save, enabled = !s.saving) {
                    if (s.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Save")
                }
            }
        },
    ) { inner ->
        when {
            s.loading -> LoadingState(Modifier.padding(inner))
            s.error != null -> ErrorState(s.error ?: "", onRetry = vm::load, modifier = Modifier.padding(inner))
            else -> Column(
                Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionCard {
                    Text(
                        "GlassMeet prepares the message and opens WhatsApp; you send it.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                SectionTitle("Your WhatsApp number")
                OutlinedTextField(
                    s.phone, vm::setPhone, singleLine = true, placeholder = { Text("+971 50 123 4567") },
                    supportingText = { Text("With the country code. Guests see it on their confirmation.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
                )
                val sample = Messages.sample(s.hostName, ProfileRepository.zone)
                TemplateEditor("Confirmation", s.confirmation, { vm.set(Messages.Kind.Confirmation, it) }, { vm.insert(Messages.Kind.Confirmation, it) }, { vm.reset(Messages.Kind.Confirmation) }, sample)
                TemplateEditor("Reminder 24 hours before", s.reminder24h, { vm.set(Messages.Kind.Reminder24h, it) }, { vm.insert(Messages.Kind.Reminder24h, it) }, { vm.reset(Messages.Kind.Reminder24h) }, sample)
                TemplateEditor("Reminder 1 hour before", s.reminder1h, { vm.set(Messages.Kind.Reminder1h, it) }, { vm.insert(Messages.Kind.Reminder1h, it) }, { vm.reset(Messages.Kind.Reminder1h) }, sample)
                ErrorText(s.saveError)
                Button(onClick = vm::save, enabled = !s.saving, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Save") }
                HintText("Reminders aren't sent for you: open a meeting and choose the reminder to send it yourself.")
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun TemplateEditor(
    title: String,
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    onInsert: (String) -> Unit,
    onReset: () -> Unit,
    sample: Messages.Context,
) {
    SectionTitle(title, action = { TextButton(onClick = onReset) { Text("Default") } })
    SectionCard {
        OutlinedTextField(value, onChange, minLines = 4, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Messages.PLACEHOLDERS.forEach { p -> AssistChip(onClick = { onInsert(p) }, label = { Text(p) }) }
        }
        Spacer(Modifier.height(12.dp))
        Text("Preview", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.widthIn(max = 320.dp)
                .background(AppTheme.tokens.whatsappBubble, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 4.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(Messages.fill(value.text, sample), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
