package com.hyper3dp.glassmeet.ui.onboarding

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hyper3dp.glassmeet.data.Backend
import com.hyper3dp.glassmeet.data.EventType
import com.hyper3dp.glassmeet.data.EventTypesRepository
import com.hyper3dp.glassmeet.data.ProfileRepository
import com.hyper3dp.glassmeet.data.friendly
import com.hyper3dp.glassmeet.ui.components.ErrorText
import com.hyper3dp.glassmeet.ui.components.SectionCard
import com.hyper3dp.glassmeet.ui.scheduling.EventForm
import com.hyper3dp.glassmeet.ui.scheduling.EventTypeFormViewModel
import com.hyper3dp.glassmeet.ui.theme.AppTheme
import com.hyper3dp.glassmeet.util.Intents
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.ZoneId

/** Whether this account has seen onboarding on this phone. */
object Onboarding {
    private fun prefs(context: Context) = context.getSharedPreferences("glassmeet", Context.MODE_PRIVATE)
    fun isDone(context: Context, userId: String) = prefs(context).getBoolean("onboarded-$userId", false)
    fun markDone(context: Context, userId: String) = prefs(context).edit().putBoolean("onboarded-$userId", true).apply()
}

private val LOCATIONS = listOf("WhatsApp call", "Google Meet", "Phone call", "Zoom")

/**
 * First run after signing up: what GlassMeet does, the host's name and WhatsApp number, a first
 * event type, and its link to share.
 */
@Composable
fun OnboardingScreen(hasEventTypes: Boolean, onFinish: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile = ProfileRepository.profile.value
    var step by rememberSaveable { mutableStateOf(0) }
    var name by rememberSaveable { mutableStateOf(profile?.fullName.orEmpty()) }
    var whatsapp by rememberSaveable { mutableStateOf(profile?.whatsappNumber.orEmpty()) }
    var eventName by rememberSaveable { mutableStateOf("30 minute meeting") }
    var duration by rememberSaveable { mutableStateOf(30) }
    var location by rememberSaveable { mutableStateOf(LOCATIONS[0]) }
    var created by remember { mutableStateOf<EventType?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val steps = 4

    fun go(back: Boolean = false) {
        error = null
        var target = (step + if (back) -1 else 1).coerceIn(0, steps - 1)
        // a host who already has event types doesn't need a first one
        if (target == 2 && hasEventTypes) target = if (back) 1 else 3
        step = target
    }

    fun next() {
        when (step) {
            0 -> go()
            1 -> {
                val number = whatsapp.filter { it.isDigit() || it == '+' }
                if (name.isBlank()) { error = "Enter your name."; return }
                if (number.isNotEmpty() && number.count { it.isDigit() } < 7) { error = "Enter the full WhatsApp number with its country code, or leave it empty."; return }
                busy = true
                scope.launch {
                    try {
                        ProfileRepository.update(buildJsonObject {
                            put("full_name", name.trim().take(80))
                            if (number.isNotEmpty()) put("whatsapp_number", if (number.startsWith("+")) number else "+$number")
                            if (ProfileRepository.profile.value?.timezone.isNullOrBlank()) put("timezone", ZoneId.systemDefault().id)
                        })
                        go()
                    } catch (e: Exception) {
                        error = e.friendly("Your details weren't saved.")
                    } finally {
                        busy = false
                    }
                }
            }
            2 -> {
                if (eventName.isBlank()) { error = "Give the event a name."; return }
                busy = true
                scope.launch {
                    try {
                        val form = EventForm(name = eventName.trim(), duration = duration.toString(), location = location, noticeValue = "30")
                        created = EventTypesRepository.save(null, EventTypeFormViewModel.values(form))
                        go()
                    } catch (e: Exception) {
                        error = e.friendly("The event type wasn't created.")
                    } finally {
                        busy = false
                    }
                }
            }
            else -> onFinish()
        }
    }

    BackHandler(enabled = step in 1..2) { go(back = true) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        // top bar: back, progress, skip
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp)) {
                if (step in 1..2) IconButton(onClick = { go(back = true) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            }
            Row(
                Modifier.weight(1f).semantics { contentDescription = "Step ${step + 1} of $steps" },
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                repeat(steps) { i ->
                    Box(
                        Modifier.height(8.dp).width(if (i == step) 20.dp else 8.dp)
                            .background(if (i <= step) MaterialTheme.colorScheme.primary else AppTheme.tokens.border, RoundedCornerShape(4.dp)),
                    )
                }
            }
            Box(Modifier.width(72.dp), contentAlignment = Alignment.CenterEnd) {
                if (step < steps - 1) TextButton(onClick = onFinish) { Text("Skip") }
            }
        }

        AnimatedContent(
            targetState = step, modifier = Modifier.weight(1f),
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { if (forward) -it / 3 else it / 3 } + fadeOut())
            },
            label = "onboarding",
        ) { s ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (s) {
                    0 -> {
                        Box(Modifier.size(72.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(36.dp))
                        }
                        Text("Welcome to GlassMeet", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("Guests book a time with you, and the meeting is confirmed on WhatsApp.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Feature(Icons.Outlined.Link, "Share one link", "Send your booking link in a chat, an email or your bio.")
                        Feature(Icons.Outlined.TouchApp, "Guests pick a time", "They see only the times you're free, in their own time zone.")
                        Feature(Icons.AutoMirrored.Outlined.Chat, "Confirm on WhatsApp", "Send the confirmation from your own WhatsApp with one tap.")
                    }
                    1 -> {
                        Heading("About you", "Guests see your name on your booking page.")
                        OutlinedTextField(name, { name = it.take(80) }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(
                            whatsapp, { whatsapp = it.filter { c -> c.isDigit() || c in "+ -()" }.take(24) },
                            label = { Text("WhatsApp number (optional)") }, placeholder = { Text("+971 50 123 4567") }, singleLine = true,
                            supportingText = { Text("Guests can message you on it after booking. Include the country code.") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
                        )
                        SectionCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Public, contentDescription = null, tint = AppTheme.tokens.link)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Time zone", style = MaterialTheme.typography.titleSmall)
                                    val zone = ProfileRepository.profile.value?.timezone?.takeIf { it.isNotBlank() } ?: ZoneId.systemDefault().id
                                    Text(zone.replace('_', ' '), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Text("Your hours start as Monday to Friday, 9:00 to 17:00. Change them any time in Availability.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    2 -> {
                        Heading("Your first event type", "An event type is a meeting guests can book. You can change everything later.")
                        OutlinedTextField(eventName, { eventName = it.take(80) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text("How long", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(15, 30, 45, 60).forEach { m ->
                                FilterChip(selected = duration == m, onClick = {
                                    if (eventName == "$duration minute meeting") eventName = "$m minute meeting"
                                    duration = m
                                }, label = { Text("$m min") })
                            }
                        }
                        Text("Where", style = MaterialTheme.typography.titleSmall)
                        SectionCard(padding = 0.dp) {
                            LOCATIONS.forEach { place ->
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                        .selectable(selected = location == place, onClick = { location = place }, role = Role.RadioButton)
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(selected = location == place, onClick = null)
                                    Spacer(Modifier.width(12.dp))
                                    Text(place, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                    else -> {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = AppTheme.tokens.success, modifier = Modifier.size(64.dp))
                        val event = created
                        Heading("You're ready", if (event == null) "Share a booking link from Scheduling whenever you like." else "Share this link and guests can book you straight away.")
                        if (event != null) {
                            val url = Backend.bookingUrl(event.id)
                            SectionCard {
                                Text(event.name, style = MaterialTheme.typography.titleMedium)
                                Text(url, style = MaterialTheme.typography.bodySmall, color = AppTheme.tokens.link, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(onClick = { Intents.copy(context, url, "Booking link", toast = "Link copied") }) {
                                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Copy")
                                    }
                                    FilledTonalButton(onClick = { Intents.share(context, "Book a time with me: ${event.name}\n$url", "Share booking link") }) {
                                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Share")
                                    }
                                }
                            }
                        }
                        Tip(Icons.Outlined.AddCircleOutline, "Tap Create in the bottom bar to add more event types.")
                        Tip(Icons.Outlined.Notifications, "New bookings show up in Booking messages on Home.")
                        Tip(Icons.Outlined.EventAvailable, "Connect your calendar in Settings so busy times stay off your page.")
                    }
                }
                ErrorText(error)
            }
        }

        Button(
            onClick = { next() }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).height(52.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Text(when (step) { 0 -> "Get started"; 1 -> "Continue"; 2 -> "Create event type"; else -> "Go to Home" })
        }
    }
}

@Composable
private fun Heading(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(44.dp).background(AppTheme.tokens.primarySoft, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = AppTheme.tokens.link)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Tip(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = AppTheme.tokens.link, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
