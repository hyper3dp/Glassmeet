package com.hyper3dp.glassmeet.ui.meetings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hyper3dp.glassmeet.data.Booking
import com.hyper3dp.glassmeet.ui.components.Avatar
import com.hyper3dp.glassmeet.ui.components.ColorDot
import com.hyper3dp.glassmeet.ui.components.StatusChip
import com.hyper3dp.glassmeet.ui.theme.parseHexColor
import com.hyper3dp.glassmeet.util.Format
import java.time.ZoneId

/** One meeting in a list: initials, guest, time range, event with its colour dot, status. */
@Composable
fun MeetingRow(booking: Booking, zone: ZoneId, eventColor: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(booking.name, size = 40.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(booking.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                Format.timeRange(booking.start, booking.end, zone), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )
            if (!booking.eventName.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(parseHexColor(eventColor) ?: MaterialTheme.colorScheme.primary, size = 8.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        booking.eventName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        StatusChip(booking)
    }
}
