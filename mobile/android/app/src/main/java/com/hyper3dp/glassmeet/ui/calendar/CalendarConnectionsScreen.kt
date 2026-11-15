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
                // Google Calendar
                SectionTitle("Google Calendar")
                SectionCard {
                    val g = s.google
                    val busy = s.busy == "google"
                    if (g == null) {
                        Text("Hide times you're busy in Google Calendar from your booking pages, and add new bookings to it.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        ConnectButton("Connect Google Calendar", busy) { vm.connectGoogle(openTab) }
                    } else {
                        StatusLine(g.email, g.connected)
                        if (!g.connected) {
                            Spacer(Modifier.height(8.dp))
                            ConnectButton("Reconnect", busy) { vm.connectGoogle(openTab) }
                        } else {
                            if (s.calendars.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                SelectField(
                                    "Add bookings to", s.calendars.map { it.id to (it.summary ?: it.id) + if (it.primary) " (main)" else "" },
                                    g.calendarId ?: "primary", vm::setGoogleCalendar, Modifier.fillMaxWidth(), enabled = !busy,
                                )
                            }
                            SwitchRow("Auto-sync", g.autoSync, vm::setGoogleAutoSync, subtitle = "Add new bookings to Google Calendar", enabled = !busy)
                            SyncRow(g.syncedDate, busy, onSync = vm::syncGoogle, onDisconnect = { confirm = "google" })
                        }
                    }
                    ErrorText(s.googleError)
                }

                // Microsoft Outlook / Teams: hidden while the server doesn't offer it
                if (s.microsoftAvailable || s.microsoft != null) {
                    SectionTitle("Microsoft Outlook and Teams")
                    SectionCard {
                        val m = s.microsoft
                        val busy = s.busy == "microsoft"
                        if (m == null) {
                            Text("Hide times you're busy in Outlook, add new bookings to it, and make Teams links.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                }

                // Subscribe feed (.ics)
                SectionTitle("Subscribe in any calendar")
                SectionCard {
                    Text(
                        "A private link that shows your GlassMeet meetings in Apple Calendar, Google Calendar or Outlook. They refresh it every few hours.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        Spacer(Modifier.height(12.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { Intents.copy(context, url, "Calendar feed", toast = "Link copied") }) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Copy link")
                            }
                            OutlinedButton(onClick = { Intents.open(context, webcal) }) { Text("Apple Calendar") }
                            OutlinedButton(onClick = { Intents.open(context, "https://calendar.google.com/calendar/r?cid=" + Uri.encode(webcal)) }) { Text("Google") }
                            OutlinedButton(onClick = {
                                Intents.open(context, "https://outlook.live.com/calendar/0/addfromweb?url=" + Uri.encode(url) + "&name=" + Uri.encode("GlassMeet"))
                            }) { Text("Outlook") }
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { confirm = "feed" }, enabled = s.busy == null) {
                            Text("Reset link", color = MaterialTheme.colorScheme.error)
                        }
                    }
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
