package com.hyper3dp.glassmeet.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hyper3dp.glassmeet.data.Backend
import com.hyper3dp.glassmeet.data.EventType
import com.hyper3dp.glassmeet.ui.components.Avatar
import com.hyper3dp.glassmeet.ui.components.ColorDot
import com.hyper3dp.glassmeet.ui.components.EmptyState
import com.hyper3dp.glassmeet.ui.components.LoadableContent
import com.hyper3dp.glassmeet.ui.components.NavRow
import com.hyper3dp.glassmeet.ui.components.SectionCard
import com.hyper3dp.glassmeet.ui.components.SectionTitle
import com.hyper3dp.glassmeet.ui.components.StatTile
import com.hyper3dp.glassmeet.ui.components.TopLevelScaffold
import com.hyper3dp.glassmeet.ui.components.screenPadding
import com.hyper3dp.glassmeet.ui.meetings.MeetingRow
import com.hyper3dp.glassmeet.ui.theme.AppTheme
import com.hyper3dp.glassmeet.ui.theme.parseHexColor
import com.hyper3dp.glassmeet.util.Format
import com.hyper3dp.glassmeet.util.Intents
import java.time.LocalDate

@Composable
fun HomeScreen(
    onOpenMeeting: (String) -> Unit,
    onOpenRequests: () -> Unit,
    onOpenMeetings: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenScheduling: () -> Unit,
) {
    val vm: HomeViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    TopLevelScaffold(title = "Home") { inner ->
        LoadableContent(state, onRetry = { vm.load() }, onRefresh = vm::refresh, modifier = Modifier.padding(top = inner.calculateTopPadding())) { d ->
            LazyColumn(contentPadding = screenPadding(androidx.compose.foundation.layout.PaddingValues(0.dp)), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Column(Modifier.padding(horizontal = 4.dp)) {
                        Text(d.greeting, style = MaterialTheme.typography.titleLarge)
                        Text(
                            when (d.todayCount) {
                                0 -> "No meetings today."
                                1 -> "You have 1 meeting today."
                                else -> "You have ${d.todayCount} meetings today."
                            },
                            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (d.pendingCount > 0) item {
                    Surface(
                        onClick = onOpenRequests, shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.HourglassTop, contentDescription = null, tint = AppTheme.tokens.warning)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (d.pendingCount == 1) "1 request waiting" else "${d.pendingCount} requests waiting",
                                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text("Approve or decline them in Meetings.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // A daily digest first (what's next, then the week), like Calendly's Home; the numbers come after
                if (d.allCount == 0) {
                    item {
                        SectionCard {
                            EmptyState(
                                icon = Icons.Outlined.EventAvailable,
                                title = "Nothing booked yet",
                                message = "Share your booking link and new meetings show up here.",
                                actionLabel = if (d.shareEvent != null) "Share" else "Create an event type",
                                actionIcon = if (d.shareEvent != null) Icons.Outlined.Share else null,
                                onAction = {
                                    val e = d.shareEvent
                                    if (e != null) Intents.share(context, shareText(e), "Share your booking link") else onOpenScheduling()
                                },
                                modifier = Modifier.height(380.dp),
                            )
                        }
                    }
                } else {
                    d.next?.let { next ->
                        item { SectionTitle("Next meeting") }
                        item {
                            SectionCard(onClick = { onOpenMeeting(next.id) }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(next.name, size = 48.dp)
                                    Spacer(Modifier.width(16.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(next.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(Format.dayAndTime(next.start, d.zone), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (!next.eventName.isNullOrBlank()) {
                                    Spacer(Modifier.height(12.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        ColorDot(parseHexColor(d.colors[next.eventTypeId]) ?: MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(8.dp))
                                        Text(next.eventName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val link = next.meetingUrl
                                    if (!link.isNullOrBlank()) {
                                        Button(onClick = { Intents.open(context, link) }) {
                                            Icon(Icons.Outlined.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Join")
                                        }
                                    }
                                    OutlinedButton(onClick = { onOpenMeeting(next.id) }) { Text("Open") }
                                }
                            }
                        }
                    }

                    item { SectionTitle("Upcoming") }
                    if (d.nextDays.isEmpty()) {
                        item {
                            SectionCard {
                                Text("Nothing in the next 7 days.", style = MaterialTheme.typography.bodyLarge)
                                Text("Upcoming meetings show up here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        d.nextDays.forEach { (day, list) ->
                            item(key = "day-$day") {
                                SectionCard(padding = 0.dp) {
                                    Text(
                                        Format.dayLabel(day, LocalDate.now(d.zone)), style = MaterialTheme.typography.labelLarge,
                                        color = AppTheme.tokens.link, modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
                                    )
                                    list.forEachIndexed { i, b ->
                                        if (i > 0) HorizontalDivider(Modifier.padding(start = 72.dp), color = AppTheme.tokens.border)
                                        MeetingRow(b, d.zone, d.colors[b.eventTypeId]) { onOpenMeeting(b.id) }
                                    }
                                }
                            }
                        }
                    }
                }

                d.shareEvent?.let { e ->
                    item { SectionTitle("Share your booking link") }
                    item {
                        SectionCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ColorDot(parseHexColor(e.color) ?: MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(e.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                Backend.bookingUrl(e.id), style = MaterialTheme.typography.bodyMedium, color = AppTheme.tokens.link,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { Intents.copy(context, Backend.bookingUrl(e.id), toast = "Link copied") }) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Copy")
                                }
                                Button(onClick = { Intents.share(context, shareText(e), "Share your booking link") }) {
                                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Share")
                                }
                                Spacer(Modifier.weight(1f))
                                androidx.compose.material3.IconButton(onClick = { Intents.open(context, Backend.bookingUrl(e.id)) }) {
                                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Open booking page")
                                }
                            }
                        }
                    }
                }

                item { SectionTitle("At a glance") }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatTile(d.todayCount.toString(), "Today", Modifier.weight(1f), onClick = onOpenMeetings)
                            StatTile(d.upcomingCount.toString(), "Upcoming", Modifier.weight(1f), onClick = onOpenMeetings)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatTile(if (d.allCount >= 1000) "1000+" else d.allCount.toString(), "All bookings", Modifier.weight(1f), onClick = onOpenMeetings)
                            StatTile(d.eventTypeCount.toString(), "Event types", Modifier.weight(1f), onClick = onOpenScheduling)
                        }
                        SectionCard(padding = 0.dp) {
                            NavRow(
                                title = "Insights", subtitle = "Bookings by month and your busiest times",
                                icon = Icons.Outlined.Insights, tint = AppTheme.tokens.link, onClick = onOpenAnalytics,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun shareText(e: EventType) = "Book a time with me: ${e.name}\n${Backend.bookingUrl(e.id)}"
