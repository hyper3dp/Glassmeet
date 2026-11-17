@file:OptIn(ExperimentalLayoutApi::class)

package com.hyper3dp.glassmeet.ui.calendar

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hyper3dp.glassmeet.data.Backend
import com.hyper3dp.glassmeet.data.parseInstant
import com.hyper3dp.glassmeet.ui.components.ConfirmDialog
import com.hyper3dp.glassmeet.ui.components.DetailScaffold
import com.hyper3dp.glassmeet.ui.components.ErrorText
import com.hyper3dp.glassmeet.ui.components.HintText
import com.hyper3dp.glassmeet.ui.components.LoadingState
import com.hyper3dp.glassmeet.ui.components.Pill
import com.hyper3dp.glassmeet.ui.components.Refreshable
import com.hyper3dp.glassmeet.ui.components.SectionCard
import com.hyper3dp.glassmeet.ui.components.SectionTitle
import com.hyper3dp.glassmeet.ui.components.SelectField
import com.hyper3dp.glassmeet.ui.components.SwitchRow
import com.hyper3dp.glassmeet.ui.theme.AppTheme
import com.hyper3dp.glassmeet.util.Format
import com.hyper3dp.glassmeet.util.Intents

@Composable
fun CalendarConnectionsScreen(onBack: () -> Unit) {
    val vm: CalendarConnectionsViewModel = viewModel()
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirm by remember { mutableStateOf<String?>(null) }
    val openTab: (String) -> Unit = { Intents.customTab(context, it) }

    DetailScaffold(title = "Calendar connections", onBack = onBack) { inner ->
        if (s.loading) {
            LoadingState(Modifier.padding(inner))
            return@DetailScaffold
        }
        Refreshable(s.refreshing, onRefresh = { vm.load(refreshing = true) }, modifier = Modifier.padding(inner)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FeedCard(s, onCopy = { Intents.copy(context, it, "Calendar feed", toast = "Link copied") }, onOpen = { Intents.open(context, it) }, onReset = { confirm = "feed" })

                // Google Calendar
                SectionTitle("Google Calendar")
                SectionCard {
                    val g = s.google
                    val busy = s.busy == "google"
                    if (g == null) {
                        Text("Connect Google Calendar so times you're busy stay off your booking pages, new bookings are added to your calendar, and events held on Google Meet get a link for each booking.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        ConnectButton("Connect Google Calendar", busy) { vm.connectGoogle(openTab) }
                    } else {
                        StatusLine(g.email, g.connected)
                        if (!g.connected) {
                            HintText("Google stopped accepting the connection. Connect again to keep busy times and bookings in sync.", Modifier.padding(top = 8.dp))
                            Spacer(Modifier.height(8.dp))
                            ConnectButton("Reconnect", busy) { vm.connectGoogle(openTab) }
                        } else {
                            HintText(
                                "Times you're busy in Google are hidden from your booking pages${if (g.autoSync) ", and new bookings are added to Google Calendar" else ""}. Events held on Google Meet get a new Meet link for each booking.",
                                Modifier.padding(top = 8.dp),
                            )
                            if (s.calendars.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                SelectField(
                                    "Add bookings to", s.calendars.map { it.id to (it.summary ?: it.id) + if (it.primary) " (main)" else "" },
                                    g.calendarId ?: "primary", vm::setGoogleCalendar, Modifier.fillMaxWidth(), enabled = !busy,
                                )
                            }
                            SwitchRow("Auto-sync", g.autoSync, vm::setGoogleAutoSync, subtitle = "Add new bookings to Google Calendar", enabled = !busy)
                            SyncRow(g.syncedDate, busy, onSync = vm::syncGoogle, onDisconnect = { confirm = "google" })
                            TextButton(onClick = vm::testGoogle, enabled = !busy) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (s.googleVerified) "Connection verified" else "Test connection")
                            }
                        }
                    }
                    ErrorText(s.googleError)
                }

                // Apple Calendar: a calendar file, no account needed
                SectionTitle("Apple Calendar")
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Apple Calendar", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Pill("Always available", AppTheme.tokens.success)
                    }
                    HintText(
                        "Add your upcoming meetings to Apple Calendar or any calendar app with a standard .ics file. No account connection required. Add it again to pick up new bookings, or subscribe above to stay updated.",
                        Modifier.padding(top = 8.dp, bottom = 8.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { vm.exportMeetings { name, ics -> Ics.open(context, name, ics) } }, enabled = s.busy == null) {
                            if (s.busy == "export") CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Add my meetings")
                        }
                        OutlinedButton(onClick = { vm.exportTestEvent { name, ics -> Ics.open(context, name, ics) } }, enabled = s.busy == null) {
                            Text("Try a test event")
                        }
                    }
                    s.exportNote?.let { HintText(it, Modifier.padding(top = 8.dp)) }
                }

                // Microsoft Outlook / Teams
                SectionTitle("Microsoft Outlook and Teams")
                SectionCard {
                        val m = s.microsoft
                        val busy = s.busy == "microsoft"
                        if (m == null && !s.microsoftAvailable) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Outlook and Teams", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Pill("Coming soon", AppTheme.tokens.link)
                            }
                            HintText("Microsoft isn't available yet. Until then, guests can add meetings to Outlook from their confirmation page.", Modifier.padding(top = 8.dp))
                        } else if (m == null) {
                            Text("Connect a Microsoft 365 or Outlook.com account so times you're busy stay off your booking pages and new bookings are added to your calendar. Events held on Microsoft Teams get a link for each booking, with a work or school account.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            ConnectButton("Connect Outlook", busy) { vm.connectMicrosoft(openTab) }
                        } else {
                            StatusLine(m.email, m.connected)
                            if (!m.connected) {
                                Spacer(Modifier.height(8.dp))
                                ConnectButton("Reconnect", busy) { vm.connectMicrosoft(openTab) }
                            } else {
                                HintText(
                                    if (m.teams) "Events held on Microsoft Teams get a new link for each booking."
                                    else "This account can't create Teams meetings, which need a work or school account.",
                                    Modifier.padding(top = 4.dp),
                                )
                                SwitchRow("Auto-sync", m.autoSync, vm::setMicrosoftAutoSync, subtitle = "Add new bookings to Outlook", enabled = !busy)
                                SyncRow(m.syncedDate, busy, onSync = vm::syncMicrosoft, onDisconnect = { confirm = "microsoft" })
                            }
                        }
                        ErrorText(s.microsoftError)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    when (confirm) {
        "google" -> ConfirmDialog(
            "Disconnect Google Calendar?", "Your booking pages stop checking it for busy times, and new bookings aren't added to it.", "Disconnect",
            onConfirm = { confirm = null; vm.disconnectGoogle() }, onDismiss = { confirm = null }, dismissLabel = "Cancel",
        )
        "microsoft" -> ConfirmDialog(
            "Disconnect Outlook?", "Your booking pages stop checking it for busy times, and new bookings aren't added to it.", "Disconnect",
            onConfirm = { confirm = null; vm.disconnectMicrosoft() }, onDismiss = { confirm = null }, dismissLabel = "Cancel",
        )
        "feed" -> ConfirmDialog(
            "Reset the link?", "Calendars subscribed to the current link stop updating. You'll need to subscribe again with the new one.", "Reset link",
            onConfirm = { confirm = null; vm.rotateFeed() }, onDismiss = { confirm = null }, dismissLabel = "Cancel",
        )
    }
}

/** Subscribe feed (.ics): one auto-updating link per host, first like the web page. */
@Composable
private fun FeedCard(s: CalendarUiState, onCopy: (String) -> Unit, onOpen: (String) -> Unit, onReset: () -> Unit) {
    SectionTitle("Subscribe to your GlassMeet calendar")
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Calendar link", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Pill("Auto-updates", AppTheme.tokens.success)
        }
        Text(
            "Add this link to your calendar app once. New bookings and cancellations then show up on their own. Apple Calendar refreshes about every hour; Google Calendar can take up to a day.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
        )
        val token = s.feedToken
        if (token == null) {
            Spacer(Modifier.height(8.dp))
            ErrorText(s.feedError ?: "Your subscription link didn't load.")
        } else {
            val url = Backend.feedUrl(token)
            val webcal = url.replaceFirst("https:", "webcal:")
            Spacer(Modifier.height(12.dp))
            Text(url, style = MaterialTheme.typography.bodySmall, color = AppTheme.tokens.link, maxLines = 2, overflow = TextOverflow.Ellipsis)
            HintText("Anyone with this link can see your meeting times and guest details. Keep it private, and reset it if it gets shared.", Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { onCopy(url) }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copy link")
                }
                OutlinedButton(onClick = { onOpen(webcal) }) { Text("Apple Calendar") }
                OutlinedButton(onClick = { onOpen("https://calendar.google.com/calendar/r?cid=" + Uri.encode(webcal)) }) { Text("Google") }
                OutlinedButton(onClick = { onOpen("https://outlook.live.com/calendar/0/addfromweb?url=" + Uri.encode(url) + "&name=" + Uri.encode("GlassMeet")) }) { Text("Outlook") }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onReset, enabled = s.busy == null) {
                if (s.busy == "feed") CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Reset link", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ConnectButton(label: String, busy: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
    }
}

@Composable
private fun StatusLine(email: String?, connected: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Event, contentDescription = null, tint = AppTheme.tokens.link)
        Spacer(Modifier.width(12.dp))
        Text(email ?: "Connected account", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (connected) Pill("Connected", AppTheme.tokens.success) else Pill("Reconnect", AppTheme.tokens.warning)
    }
}

@Composable
private fun SyncRow(syncedDate: String?, busy: Boolean, onSync: () -> Unit, onDisconnect: () -> Unit) {
    val last = parseInstant(syncedDate)
    if (last != null) HintText("Last synced ${Format.dateTime(last)}", Modifier.padding(top = 4.dp, bottom = 4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onSync, enabled = !busy) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Sync now")
        }
        TextButton(onClick = onDisconnect, enabled = !busy) { Text("Disconnect", color = MaterialTheme.colorScheme.error) }
    }
}
