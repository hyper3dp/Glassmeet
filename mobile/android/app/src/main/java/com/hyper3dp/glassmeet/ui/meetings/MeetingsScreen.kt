@file:OptIn(ExperimentalMaterial3Api::class)

package com.hyper3dp.glassmeet.ui.meetings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.platform.LocalContext
import com.hyper3dp.glassmeet.data.Booking
import com.hyper3dp.glassmeet.data.Messages
import com.hyper3dp.glassmeet.util.Intents
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hyper3dp.glassmeet.ui.components.EmptyState
import com.hyper3dp.glassmeet.ui.components.LoadableContent
import com.hyper3dp.glassmeet.ui.components.SectionCard
import com.hyper3dp.glassmeet.ui.theme.AppTheme
import com.hyper3dp.glassmeet.util.Format
import java.time.LocalDate

@Composable
fun MeetingsScreen(onOpenMeeting: (String) -> Unit) {
    val vm: MeetingsViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    BackHandler(enabled = searching) { searching = false; vm.setQuery("") }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (searching) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { searching = false; vm.setQuery("") }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search") }
                    },
                    title = {
                        TextField(
                            value = query, onValueChange = vm::setQuery, singleLine = true,
                            placeholder = { Text("Name, email, event or phone") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    },
                    actions = {
                        if (query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
                    },
                )
            } else {
                LargeTopAppBar(
                    title = { Text("Meetings") },
                    actions = { IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, contentDescription = "Search meetings") } },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(top = inner.calculateTopPadding())) {
            val all = state.data?.bookings.orEmpty()
            if (!searching) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MeetingFilter.entries.forEach { f ->
                        val count = if (f == MeetingFilter.Requests) MeetingsViewModel.requestsCount(all) else 0
                        FilterChip(
                            selected = filter == f,
                            onClick = { vm.setFilter(f) },
                            label = { Text(if (count > 0) "${f.label} ($count)" else f.label) },
                        )
                    }
                }
            }
            LoadableContent(state, onRetry = { vm.load() }, onRefresh = vm::refresh) { d ->
                val list = MeetingsViewModel.visible(d.bookings, filter, query)
                if (list.isEmpty()) {
                    when {
                        query.isNotBlank() -> EmptyState(Icons.Outlined.SearchOff, "No matches", "No meeting matches “${query.trim()}”. Try a name, email or event.")
                        searching -> EmptyState(Icons.Filled.Search, "Search your meetings", "Type a guest's name, email, event or phone number.")
                        filter == MeetingFilter.Upcoming -> EmptyState(Icons.Outlined.Today, "No upcoming meetings", "Share your booking link and new meetings show up here.")
                        filter == MeetingFilter.Requests -> EmptyState(Icons.Outlined.HourglassEmpty, "No requests", "Meetings that need your approval wait here.")
                        filter == MeetingFilter.Past -> EmptyState(Icons.Outlined.History, "No past meetings", "Meetings you've had show up here.")
                        else -> EmptyState(Icons.Outlined.EventBusy, "Nothing cancelled", "Cancelled meetings stay here for two days.")
                    }
                } else {
                    val groups = MeetingsViewModel.groups(list, d.zone)
                    val today = LocalDate.now(d.zone)
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        groups.forEach { (day, items) ->
                            item(key = "$day-${items.first().id}") {
                                SectionCard(padding = 0.dp) {
                                    Text(
                                        Format.dayLabel(day, today), style = MaterialTheme.typography.labelLarge, color = AppTheme.tokens.link,
                                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
                                    )
                                    items.forEachIndexed { i, b ->
                                        if (i > 0) HorizontalDivider(Modifier.padding(start = 72.dp), color = AppTheme.tokens.border)
                                        QuickActionsRow(b, d, vm, onOpenMeeting)
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

/** A meeting row whose long press offers Approve, Join and WhatsApp without opening it, like Calendly and Cal.com */
@Composable
private fun QuickActionsRow(b: Booking, d: MeetingsData, vm: MeetingsViewModel, onOpenMeeting: (String) -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val link = b.meetingUrl?.takeIf { it.isNotBlank() && b.isUpcoming() && !b.isPending }
    val phone = b.guestPhone?.takeIf { it.isNotBlank() }
    val hasActions = b.isPending || link != null || phone != null
    Box {
        MeetingRow(b, d.zone, d.colors[b.eventTypeId], onLongClick = if (hasActions) ({ menu = true }) else null) { onOpenMeeting(b.id) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (b.isPending) {
                DropdownMenuItem(
                    text = { Text("Approve") }, leadingIcon = { Icon(Icons.Outlined.Check, contentDescription = null) },
                    onClick = {
                        menu = false
                        vm.approve(b.id) { error ->
                            Toast.makeText(context, error ?: "Approved. Open it to message ${b.name}.", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
            if (link != null) {
                DropdownMenuItem(
                    text = { Text("Join") }, leadingIcon = { Icon(Icons.Outlined.Videocam, contentDescription = null) },
                    onClick = { menu = false; Intents.open(context, link) },
                )
            }
            if (phone != null) {
                DropdownMenuItem(
                    text = { Text("WhatsApp") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null) },
                    onClick = { menu = false; Intents.open(context, Messages.whatsAppLink(phone, "")) },
                )
            }
        }
    }
}
