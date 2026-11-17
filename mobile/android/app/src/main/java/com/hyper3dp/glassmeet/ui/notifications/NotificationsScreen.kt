package com.hyper3dp.glassmeet.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyper3dp.glassmeet.data.AppNotification
import com.hyper3dp.glassmeet.data.NotificationsRepository
import com.hyper3dp.glassmeet.data.parseInstant
import com.hyper3dp.glassmeet.ui.components.DetailScaffold
import com.hyper3dp.glassmeet.ui.components.EmptyState
import com.hyper3dp.glassmeet.ui.components.ErrorState
import com.hyper3dp.glassmeet.ui.components.LoadingState
import com.hyper3dp.glassmeet.ui.components.Refreshable
import com.hyper3dp.glassmeet.ui.components.SectionCard
import com.hyper3dp.glassmeet.ui.theme.AppTheme
import com.hyper3dp.glassmeet.util.Snack
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/** Booking messages: the same list as the web's bell. Tapping one opens what it's about. */
@Composable
fun NotificationsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val items by NotificationsRepository.items.collectAsStateWithLifecycle()
    val loaded by NotificationsRepository.loaded.collectAsStateWithLifecycle()
    val error by NotificationsRepository.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { NotificationsRepository.refresh() }

    fun markAll() = scope.launch {
        if (!NotificationsRepository.markRead(items.filter { !it.isRead }.map { it.id })) Snack.show("Couldn't mark them read. Try again.")
    }

    DetailScaffold(
        title = "Booking messages", onBack = onBack,
        actions = { if (items.any { !it.isRead }) TextButton(onClick = { markAll() }) { Text("Mark all read") } },
    ) { inner ->
        val message = error
        when {
            items.isEmpty() && !loaded && message == null -> LoadingState(Modifier.padding(inner))
            items.isEmpty() && message != null -> ErrorState(message, onRetry = { scope.launch { NotificationsRepository.refresh() } }, modifier = Modifier.padding(inner))
            else -> Refreshable(refreshing, onRefresh = {
                scope.launch { refreshing = true; NotificationsRepository.refresh(); refreshing = false }
            }, modifier = Modifier.padding(inner)) {
                if (items.isEmpty()) {
                    EmptyState(Icons.Outlined.Notifications, "No booking messages", "New bookings, requests and cancellations show up here.")
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp), modifier = Modifier.fillMaxSize()) {
                        item {
                            SectionCard(padding = 0.dp) {
                                items.forEachIndexed { i, n ->
                                    if (i > 0) HorizontalDivider(Modifier.padding(start = 68.dp), color = AppTheme.tokens.border)
                                    NotificationRow(n) {
                                        if (!n.isRead) scope.launch { NotificationsRepository.markRead(listOf(n.id)) }
                                        n.link?.takeIf { it.isNotBlank() }?.let(onOpen)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun iconFor(n: AppNotification): ImageVector {
    val text = "${n.title.orEmpty()} ${n.message.orEmpty()}".lowercase()
    return when {
        "cancel" in text || "declin" in text -> Icons.Outlined.EventBusy
        "request" in text || "approv" in text -> Icons.Outlined.HourglassEmpty
        "holiday" in text -> Icons.Outlined.WbSunny
        "book" in text -> Icons.Outlined.EventAvailable
        else -> Icons.Outlined.Notifications
    }
}

/** "just now", "5m ago", "3h ago", "2d ago", then the date (web: timeAgo) */
private fun timeAgo(iso: String?): String {
    val at = parseInstant(iso) ?: return ""
    val mins = Duration.between(at, Instant.now()).toMinutes()
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 1440 -> "${mins / 60}h ago"
        mins < 10080 -> "${mins / 1440}d ago"
        else -> com.hyper3dp.glassmeet.util.Format.dateTime(at)
    }
}

@Composable
private fun NotificationRow(n: AppNotification, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .background(if (n.isRead) androidx.compose.ui.graphics.Color.Transparent else AppTheme.tokens.primarySoft.copy(alpha = 0.5f))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(36.dp).background(AppTheme.tokens.primarySoft, CircleShape), contentAlignment = Alignment.Center) {
            Icon(iconFor(n), contentDescription = null, tint = AppTheme.tokens.link, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                n.title ?: "Notification", style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (n.isRead) FontWeight.Normal else FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            n.message?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            val ago = timeAgo(n.createdDate)
            if (ago.isNotEmpty()) Text(ago, style = MaterialTheme.typography.labelSmall, color = AppTheme.tokens.textTertiary)
        }
        if (!n.isRead) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.padding(top = 6.dp).size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        }
    }
}
