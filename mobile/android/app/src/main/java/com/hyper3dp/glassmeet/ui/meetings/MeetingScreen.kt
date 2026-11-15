package com.hyper3dp.glassmeet.ui.meetings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hyper3dp.glassmeet.data.Booking
import com.hyper3dp.glassmeet.data.Messages
import com.hyper3dp.glassmeet.ui.components.Avatar
import com.hyper3dp.glassmeet.ui.components.ColorDot
import com.hyper3dp.glassmeet.ui.components.ConfirmDialog
import com.hyper3dp.glassmeet.ui.components.DetailScaffold
import com.hyper3dp.glassmeet.ui.components.ErrorState
import com.hyper3dp.glassmeet.ui.components.HintText
import com.hyper3dp.glassmeet.ui.components.InfoLine
import com.hyper3dp.glassmeet.ui.components.LoadingState
import com.hyper3dp.glassmeet.ui.components.SectionCard
import com.hyper3dp.glassmeet.ui.components.SectionTitle
import com.hyper3dp.glassmeet.ui.components.StatusChip
import com.hyper3dp.glassmeet.ui.theme.AppTheme
import com.hyper3dp.glassmeet.ui.theme.parseHexColor
import com.hyper3dp.glassmeet.util.Format
import com.hyper3dp.glassmeet.util.Intents
import java.time.Instant

@Composable
fun MeetingScreen(id: String, onBack: () -> Unit) {
    val vm: MeetingViewModel = viewModel(key = "meeting-$id") { MeetingViewModel(id) }
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val booking = s.booking

    DetailScaffold(
        title = "Meeting",
        onBack = onBack,
        bottomBar = {
            if (booking != null) {
                // the action area: Approve / Decline for a request, else Send on WhatsApp
                Surface(color = AppTheme.tokens.surface, tonalElevation = 0.dp, shadowElevation = 8.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (booking.isPending) {
                            OutlinedButton(onClick = vm::askDecline, enabled = !s.busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                                Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Decline")
                            }
                            Button(onClick = vm::approve, enabled = !s.busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Approve")
                            }
                        } else {
                            Button(
                                onClick = { Intents.open(context, Messages.whatsAppLink(booking.guestPhone, s.message)) },
                                enabled = s.message.isNotBlank(),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Send on WhatsApp")
                            }
                        }
                    }
                }
            }
        },
    ) { inner ->
        when {
            booking != null -> MeetingContent(booking, s, vm, Modifier.padding(inner))
            s.loading -> LoadingState(Modifier.padding(inner))
            else -> ErrorState(s.error ?: "This meeting didn't load.", onRetry = vm::load, modifier = Modifier.padding(inner))
        }
    }

    when (s.confirm) {
        "cancel" -> ConfirmDialog(
            title = "Cancel this meeting?",
            text = "${booking?.name ?: "The guest"} isn't told automatically. After cancelling, you can send them a message from this page.",
            confirmLabel = "Cancel meeting", dismissLabel = "Keep it",
            onConfirm = vm::confirmChange, onDismiss = vm::dismissConfirm,
        )
        "decline" -> ConfirmDialog(
            title = "Decline this request?",
            text = "The time becomes free again. You can send ${booking?.name ?: "the guest"} a message from this page.",
            confirmLabel = "Decline", dismissLabel = "Keep it",
            onConfirm = vm::confirmChange, onDismiss = vm::dismissConfirm,
        )
    }
}

@Composable
private fun MeetingContent(b: Booking, s: MeetingUiState, vm: MeetingViewModel, modifier: Modifier) {
    val context = LocalContext.current
    val zone = s.zone
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // guest header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            Avatar(b.name, size = 56.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(b.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!b.eventName.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ColorDot(parseHexColor(s.eventColor) ?: MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(b.eventName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            StatusChip(b)
        }

        SectionTitle("When")
        SectionCard {
            InfoLine(Icons.Outlined.Today, "Date", Format.longDate(b.start, zone))
            InfoLine(Icons.Outlined.Schedule, "Time", "${Format.timeRange(b.start, b.end, zone)} (${Format.duration(b.minutes.toInt())})")
            InfoLine(Icons.Outlined.Language, "Time zone", zone.id)
            val link = s.link.trim()
            if (link.isNotEmpty() && !b.isCancelled) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = { Intents.open(context, link) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Join meeting")
                }
            }
        }

        SectionTitle("Guest")
        SectionCard {
            val email = b.guestEmail
            val phone = b.guestPhone
            if (email.isNullOrBlank() && phone.isNullOrBlank()) {
                Text("No contact details were given.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!email.isNullOrBlank()) {
                InfoLine(Icons.Outlined.Email, "Email", email, onClick = { Intents.email(context, email, "Our meeting", "") })
            }
            if (!phone.isNullOrBlank()) {
                InfoLine(Icons.Outlined.Call, "Phone", phone, onClick = { Intents.dial(context, phone) })
                InfoLine(Icons.AutoMirrored.Outlined.Chat, "WhatsApp", "Open a chat", onClick = { Intents.open(context, Messages.whatsAppLink(phone, "")) })
            }
        }

        val answers = b.customAnswers
        if (answers.isNotEmpty()) {
            SectionTitle("Answers")
            SectionCard {
                answers.forEachIndexed { i, a ->
                    if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = AppTheme.tokens.border)
                    Text(a.question, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(a.answer.ifBlank { "No answer" }, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        if (!b.guestNotes.isNullOrBlank()) {
            SectionTitle("Guest notes")
            SectionCard { Text(b.guestNotes, style = MaterialTheme.typography.bodyLarge) }
        }

        SectionTitle("Your notes")
        OutlinedTextField(
            value = s.notes, onValueChange = vm::setNotes, minLines = 3,
            placeholder = { Text("Only you see these") },
            supportingText = { SaveLabel(s.notesSave) },
            modifier = Modifier.fillMaxWidth(),
        )

        SectionTitle("Meeting link")
        OutlinedTextField(
            value = s.link, onValueChange = vm::setLink, singleLine = true,
            placeholder = { Text("https://") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            supportingText = { SaveLabel(s.linkSave, idle = "Saved links are copied to your connected calendars.") },
            modifier = Modifier.fillMaxWidth(),
        )

        SectionTitle("Message")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Messages.Kind.entries.forEach { k ->
                FilterChip(selected = s.kind == k, onClick = { vm.setKind(k) }, label = { Text(k.label) })
            }
        }
        OutlinedTextField(
            value = s.message, onValueChange = vm::setMessage, minLines = 5,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { Intents.copy(context, s.message, "Message", toast = "Message copied") }) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Copy")
            }
            OutlinedButton(onClick = { Intents.email(context, b.guestEmail, "Your meeting with ${s.hostName}".trim(), s.message) }) {
                Icon(Icons.Outlined.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Email")
            }
        }
        HintText("GlassMeet prepares the message and opens WhatsApp; you send it.")

        if (!b.isCancelled && !b.isPending && !b.end.isBefore(Instant.now())) {
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = vm::askCancel, enabled = !s.busy,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.EventBusy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Cancel meeting")
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SaveLabel(state: SaveState, idle: String? = null) {
    when (state) {
        SaveState.Saving -> Text("Saving…")
        SaveState.Saved -> Text("Saved")
        SaveState.Failed -> Text("Not saved. Check your connection.", color = MaterialTheme.colorScheme.error)
        SaveState.Idle -> if (idle != null) Text(idle)
    }
}
