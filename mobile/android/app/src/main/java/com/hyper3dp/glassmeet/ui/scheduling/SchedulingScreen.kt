@file:OptIn(ExperimentalMaterial3Api::class)

package com.hyper3dp.glassmeet.ui.scheduling

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hyper3dp.glassmeet.data.Backend
import com.hyper3dp.glassmeet.data.BuiltInTemplates
import com.hyper3dp.glassmeet.data.EventTemplate
import com.hyper3dp.glassmeet.data.EventType
import com.hyper3dp.glassmeet.data.EventTypesRepository
import com.hyper3dp.glassmeet.ui.components.ColorDot
import com.hyper3dp.glassmeet.ui.components.ConfirmDialog
import com.hyper3dp.glassmeet.ui.components.EmptyState
import com.hyper3dp.glassmeet.ui.components.LoadableContent
import com.hyper3dp.glassmeet.ui.components.SectionCard
import com.hyper3dp.glassmeet.ui.components.TopLevelScaffold
import com.hyper3dp.glassmeet.ui.navigation.EventTypeSeed
import com.hyper3dp.glassmeet.ui.theme.AppTheme
import com.hyper3dp.glassmeet.ui.theme.parseHexColor
import com.hyper3dp.glassmeet.util.Intents
import kotlinx.serialization.json.JsonObject

@Composable
fun SchedulingScreen(onEdit: (String) -> Unit, onNew: () -> Unit) {
    val vm: SchedulingViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<EventType?>(null) }
    val context = LocalContext.current

    fun startFrom(data: JsonObject?) {
        EventTypeSeed.data = data
        picking = false
        onNew()
    }

    TopLevelScaffold(
        title = "Scheduling",
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picking = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New event type") },
            )
        },
    ) { inner ->
        LoadableContent(state, onRetry = { vm.load() }, onRefresh = vm::refresh, modifier = Modifier.padding(top = inner.calculateTopPadding())) { d ->
            if (d.eventTypes.isEmpty()) {
                EmptyState(
                    Icons.Outlined.EditCalendar, "No event types yet",
                    "An event type is a kind of meeting guests can book, like a 30 minute call.",
                    actionLabel = "New event type", actionIcon = Icons.Filled.Add, onAction = { picking = true },
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(d.eventTypes, key = { it.id }) { e ->
                        EventTypeCard(
                            e,
                            onEdit = { onEdit(e.id) },
                            onToggle = { vm.setActive(e, it) },
                            onCopy = { Intents.copy(context, Backend.bookingUrl(e.id), "Booking link", toast = "Link copied") },
                            onShare = { Intents.share(context, "Book a time with me: ${e.name}\n${Backend.bookingUrl(e.id)}", "Share booking link") },
                            onDuplicate = { startFrom(EventTypesRepository.asTemplateData(e)) },
                            onDelete = { deleting = e },
                        )
                    }
                }
            }
        }
    }

    if (picking) {
        TemplateSheet(
            templates = state.data?.templates.orEmpty(),
            onPick = { startFrom(it) },
            onDeleteTemplate = vm::deleteTemplate,
            onDismiss = { picking = false },
        )
    }

    deleting?.let { e ->
        ConfirmDialog(
            title = "Delete ${e.name}?",
            text = "Its booking link stops working. Meetings already booked stay in Meetings. You can turn it off instead and bring it back later.",
            confirmLabel = "Delete",
            dismissLabel = "Cancel",
            onConfirm = { deleting = null; vm.delete(e) },
            onDismiss = { deleting = null },
            extra = {
                if (e.active) TextButton(onClick = { deleting = null; vm.setActive(e, false) }) { Text("Turn off instead") }
            },
        )
    }
}

@Composable
private fun EventTypeCard(
    e: EventType,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val color = parseHexColor(e.color) ?: MaterialTheme.colorScheme.primary
    SectionCard(onClick = onEdit, padding = 0.dp) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorDot(if (e.active) color else AppTheme.tokens.textTertiary, size = 12.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    e.name.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (e.active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(e.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Switch(checked = e.active, onCheckedChange = onToggle)
        }
        if (!e.active) {
            Text(
                "Off: guests can't book this", style = MaterialTheme.typography.labelMedium, color = AppTheme.tokens.textTertiary,
                modifier = Modifier.padding(start = 40.dp, top = 4.dp),
            )
        }
        HorizontalDivider(Modifier.padding(top = 12.dp), color = AppTheme.tokens.border)
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCopy, enabled = e.active) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Copy link")
            }
            TextButton(onClick = onShare, enabled = e.active) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Share")
            }
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More for ${e.name}") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Duplicate") }, leadingIcon = { Icon(Icons.Outlined.FileCopy, null) }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

/** New event type: start from scratch, a built-in template or one of the host's own. */
@Composable
private fun TemplateSheet(
    templates: List<EventTemplate>,
    onPick: (JsonObject?) -> Unit,
    onDeleteTemplate: (EventTemplate) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmDelete by remember { mutableStateOf<EventTemplate?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = AppTheme.tokens.surface) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.navigationBarsPadding()) {
            item {
                Text("New event type", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            }
            item {
                TemplateRow("Start from scratch", "Set everything yourself", onClick = { onPick(null) })
            }
            item { SheetLabel("Templates") }
            items(BuiltInTemplates.all, key = { it.id }) { t ->
                TemplateRow(t.name, BuiltInTemplates.summary(t.data), onClick = { onPick(t.data) })
            }
            if (templates.isNotEmpty()) {
                item { SheetLabel("Your templates") }
                items(templates, key = { "mine-${it.id}" }) { t ->
                    val data = t.data as? JsonObject
                    TemplateRow(
                        t.name, data?.let { BuiltInTemplates.summary(it) }.orEmpty(),
                        onClick = { onPick(data) },
                        onDelete = { confirmDelete = t },
                    )
                }
            }
        }
    }
    confirmDelete?.let { t ->
        ConfirmDialog(
            "Delete the template “${t.name}”?", "Event types made from it stay as they are.", "Delete",
            onConfirm = { confirmDelete = null; onDeleteTemplate(t) }, onDismiss = { confirmDelete = null }, dismissLabel = "Cancel",
        )
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = AppTheme.tokens.link,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun TemplateRow(name: String, summary: String, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).padding(0.dp),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Surface(color = AppTheme.tokens.primarySoft, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Description, contentDescription = null, tint = AppTheme.tokens.link) }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            if (summary.isNotBlank()) Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Delete template $name") }
        }
    }
}
