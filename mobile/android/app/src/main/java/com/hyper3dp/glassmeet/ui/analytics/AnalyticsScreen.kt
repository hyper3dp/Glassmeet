package com.hyper3dp.glassmeet.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hyper3dp.glassmeet.ui.components.DetailScaffold
import com.hyper3dp.glassmeet.ui.components.LoadableContent
import com.hyper3dp.glassmeet.ui.components.SectionCard
import com.hyper3dp.glassmeet.ui.components.SectionTitle
import com.hyper3dp.glassmeet.ui.components.StatTile
import com.hyper3dp.glassmeet.ui.theme.AppTheme

@Composable
fun AnalyticsScreen(onBack: () -> Unit) {
    val vm: AnalyticsViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    DetailScaffold(title = "Analytics", onBack = onBack) { inner ->
        LoadableContent(state, onRetry = { vm.load() }, onRefresh = vm::refresh, modifier = Modifier.padding(inner)) { d ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(d.total, "Total booked", Modifier.weight(1f))
                    StatTile(d.confirmed.toString(), "Confirmed", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(d.cancelled.toString(), "Cancelled", Modifier.weight(1f))
                    StatTile(d.averageLength, "Average length", Modifier.weight(1f))
                }

                SectionTitle("Insights")
                SectionCard {
                    Insight(Icons.Outlined.TrendingUp, "Most popular event", d.popularEvent)
                    Insight(Icons.Outlined.AccessTime, "Most popular time", d.popularTime)
                    Insight(Icons.Outlined.Groups, "Total attendees", d.attendees.toString())
                    Insight(Icons.Outlined.Category, "Event types", d.eventTypes.toString())
                }

                SectionTitle("Bookings by month")
                SectionCard {
                    if (d.months.isEmpty()) {
                        Text("No bookings yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        MonthChart(d.months)
                    }
                }

                SectionTitle("Busiest times")
                SectionCard {
                    Text("Bookings by weekday and hour, 8am to 8pm.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Heatmap(d.heat)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Insight(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = AppTheme.tokens.link, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 180.dp),
        )
    }
}

/** Bars drawn with Canvas; scrolls sideways when there are many months. */
@Composable
private fun MonthChart(months: List<MonthBar>) {
    val max = (months.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
    val bar = AppTheme.tokens.primary
    val track = AppTheme.tokens.surfaceRaised
    val description = months.joinToString { "${it.label}: ${it.count}" }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        months.forEach { m ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp)) {
                Text(m.count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Canvas(Modifier.width(28.dp).height(140.dp)) {
                    val r = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    drawRoundRect(track, cornerRadius = r)
                    val h = size.height * m.count / max
                    drawRoundRect(bar, topLeft = Offset(0f, size.height - h), size = Size(size.width, h), cornerRadius = r)
                }
                Spacer(Modifier.height(4.dp))
                Text(m.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

private val WEEKDAY_LABELS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
private val HOUR_LABELS = listOf("8", "", "10", "", "12", "", "2", "", "4", "", "6", "")

/** 7 weekdays x 12 hours; colour strength relative to the busiest cell. */
@Composable
private fun Heatmap(grid: List<List<Int>>) {
    val max = grid.flatten().maxOrNull()?.coerceAtLeast(1) ?: 1
    val base = AppTheme.tokens.primary
    val empty = AppTheme.tokens.surfaceRaised
    Column {
        Row {
            Spacer(Modifier.width(36.dp))
            HOUR_LABELS.forEach { h ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(h, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        grid.forEachIndexed { day, row ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                Text(WEEKDAY_LABELS[day], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp))
                Canvas(
                    Modifier.weight(1f).height(22.dp).semantics {
                        contentDescription = "${WEEKDAY_LABELS[day]}: ${row.sum()} bookings"
                    },
                ) {
                    val gap = 3.dp.toPx()
                    val w = (size.width - gap * (row.size - 1)) / row.size
                    row.forEachIndexed { i, count ->
                        val color = if (count == 0) empty else base.copy(alpha = 0.18f + 0.82f * count / max)
                        drawRoundRect(
                            color, topLeft = Offset(i * (w + gap), 0f), size = Size(w, size.height),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        )
                    }
                }
            }
        }
    }
}
