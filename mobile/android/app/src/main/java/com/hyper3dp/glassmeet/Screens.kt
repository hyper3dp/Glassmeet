@file:OptIn(ExperimentalMaterial3Api::class)

package com.hyper3dp.glassmeet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

// ── Loading ──

/** Loads a screen's data, and reloads it on pull-to-refresh. */
class Loader<T>(private val scope: CoroutineScope, private val load: suspend () -> T) {
    var data by mutableStateOf<T?>(null)
    var error by mutableStateOf<String?>(null)
    var refreshing by mutableStateOf(false)
    fun refresh(pull: Boolean = false) {
        if (pull) refreshing = true
        scope.launch {
            runCatching { load() }.onSuccess { data = it; error = null }.onFailure { error = it.message ?: "Something went wrong." }
            refreshing = false
        }
    }
}

@Composable
fun <T> rememberLoader(load: suspend () -> T): Loader<T> {
    val scope = rememberCoroutineScope()
    val loader = remember { Loader(scope, load) }
    LaunchedEffect(Unit) { loader.refresh() }
    return loader
}

// ── Page frame ──

@Composable
fun Page(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = { actions() },
            )
        },
        bottomBar = bottomBar,
        content = content,
    )
}

fun LazyListScope.header(text: String) = item {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
fun Footer(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
fun GroupCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) { Column { content() } }
}

@Composable
fun Empty(icon: ImageVector, title: String, message: String, action: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action()
    }
}

@Composable
fun Badge(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = Color.Transparent, border = androidx.compose.foundation.BorderStroke(1.dp, color)) {
        Text(text, color = color, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
fun MeetingItem(b: Booking, nav: Nav) {
    ListItem(
        headlineContent = { Text(b.guestName) },
        supportingContent = { Text("${Fmt.whenText(b.start)} · ${b.eventName ?: "Meeting"}") },
        leadingContent = { Avatar(b.guestName) },
        trailingContent = {
            when {
                b.isPending -> Badge("Request", warningColor())
                b.isCancelled -> Badge("Cancelled", MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { nav.open(Page.Meeting(b.id)) },
    )
}

// ── Home ──

@Composable
fun HomeScreen(account: AccountState, nav: Nav) {
    val context = LocalContext.current
    val loader = rememberLoader { Api.bookings() to Api.eventTypes() }
    val first = account.hostName.substringBefore(" ")
    Page(if (first.isEmpty()) "Home" else "Hello, $first") { padding ->
        val (bookings, events) = loader.data ?: (null to null)
        if (bookings == null || events == null) { if (loader.error == null) Loading() else Footer(loader.error!!); return@Page }
        val upcoming = bookings.filter { it.isUpcoming() }
        val requests = bookings.filter { it.isPending }
        PullToRefreshBox(loader.refreshing, { loader.refresh(true) }, Modifier.padding(padding)) {
            LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                header("Next meeting")
                item {
                    val next = upcoming.firstOrNull()
                    GroupCard {
                        if (next == null) {
                            Empty(Icons.Filled.CalendarToday, "Nothing booked yet", "Share a booking link and new meetings will show up here.")
                        } else {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(next.guestName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(Fmt.whenText(next.start), color = MaterialTheme.colorScheme.primary)
                                Text(next.eventName ?: "Meeting", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = { nav.open(Page.Meeting(next.id)) }, modifier = Modifier.fillMaxWidth()) { Text("Open meeting") }
                            }
                        }
                    }
                }
                if (requests.isNotEmpty()) {
                    header("Waiting for your reply")
                    item { GroupCard { requests.take(5).forEachIndexed { i, b -> if (i > 0) HorizontalDivider(); MeetingItem(b, nav) } } }
                }
                if (upcoming.size > 1) {
                    header("Coming up")
                    item { GroupCard { upcoming.drop(1).take(4).forEachIndexed { i, b -> if (i > 0) HorizontalDivider(); MeetingItem(b, nav) } } }
                }
                header("Share a booking link")
                item {
                    GroupCard {
                        val links = events.filter { it.active }
                        if (links.isEmpty()) {
                            ListItem(headlineContent = { Text("Create your first event type") }, colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable { nav.open(Page.EditEvent(null)) })
                        }
                        links.take(4).forEachIndexed { i, e ->
                            if (i > 0) HorizontalDivider()
                            ListItem(
                                headlineContent = { Text(e.name) }, supportingContent = { Text("${e.duration} min") },
                                trailingContent = { Icon(Icons.Filled.Share, "Share", tint = MaterialTheme.colorScheme.primary) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable { shareText(context, Backend.bookingUrl(e.id)) },
                            )
                        }
                    }
                    Footer("Guests open the link, pick a time and book. They never need an account.")
                }
            }
        }
    }
}

// ── Meetings ──

@Composable
fun MeetingsScreen(nav: Nav) {
    val loader = rememberLoader { Api.bookings() }
    var filter by remember { mutableStateOf(0) }
    Page("Meetings") { padding ->
        val all = loader.data ?: run { if (loader.error == null) Loading() else Footer(loader.error!!); return@Page }
        val requests = all.filter { it.isPending }
        val shown = when (filter) {
            0 -> all.filter { it.isUpcoming() }
            1 -> requests
            else -> all.filter { !it.isUpcoming() && !it.isPending }.reversed()
        }
        PullToRefreshBox(loader.refreshing, { loader.refresh(true) }, Modifier.padding(padding)) {
            LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    val labels = listOf("Upcoming", if (requests.isEmpty()) "Requests" else "Requests (${requests.size})", "Past")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        labels.forEachIndexed { i, label ->
                            SegmentedButton(selected = filter == i, onClick = { filter = i }, shape = SegmentedButtonDefaults.itemShape(i, labels.size)) { Text(label) }
                        }
                    }
                }
                item {
                    GroupCard {
                        if (shown.isEmpty()) {
                            val (t, m) = when (filter) {
                                0 -> "No upcoming meetings" to "Meetings booked from your links appear here."
                                1 -> "No requests" to "Bookings for event types that need your approval wait here."
                                else -> "No past meetings" to "Finished and cancelled meetings are kept here."
                            }
                            Empty(Icons.Filled.Groups, t, m)
                        }
                        shown.forEachIndexed { i, b -> if (i > 0) HorizontalDivider(); MeetingItem(b, nav) }
                    }
                }
            }
        }
    }
}

@Composable
fun MeetingScreen(id: String, account: AccountState, nav: Nav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loader = rememberLoader {
        val b = Api.booking(id)
        Triple(b, b.eventTypeId?.let { runCatching { Api.eventType(it) }.getOrNull() }, runCatching { Api.whatsApp()?.confirmationTemplate }.getOrNull())
    }
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }

    val data = loader.data
    val b = data?.first
    val live = b != null && !b.isCancelled && !b.isDeclined && !b.isOver()
    fun message(t: String, bk: Booking) = WhatsApp.fill(t, bk, data?.second, account.hostName)
    fun change(status: String?, approval: String?, followUp: String?) {
        busy = true
        scope.launch {
            runCatching { Api.updateBooking(id, status, approval) }
                .onSuccess { updated ->
                    loader.data = Triple(updated, data?.second, data?.third)
                    if (followUp != null && updated.guestPhone != null) openUri(context, WhatsApp.link(updated.guestPhone, message(followUp, updated)))
                }
                .onFailure { failure = "This meeting could not be updated. Try again." }
            busy = false
        }
    }

    Page(
        "Meeting", onBack = nav::back,
        bottomBar = {
            if (b != null && live) Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    when {
                        b.isPending -> {
                            OutlinedButton(onClick = { confirm = "decline" }, enabled = !busy, modifier = Modifier.weight(1f).height(52.dp)) { Text("Decline") }
                            Button(onClick = { change(null, "approved", null) }, enabled = !busy, modifier = Modifier.weight(1f).height(52.dp)) { Text("Approve") }
                        }
                        b.guestPhone != null -> Button(
                            onClick = { openUri(context, WhatsApp.link(b.guestPhone, message(data.third ?: WhatsApp.confirmation, b))) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) { Icon(Icons.AutoMirrored.Filled.Send, null); Spacer(Modifier.size(8.dp)); Text("Confirm on WhatsApp") }
                        else -> Button(onClick = { openUrl(context, "mailto:${b.guestEmail}") }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Icon(Icons.Filled.Email, null); Spacer(Modifier.size(8.dp)); Text("Email guest")
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (b == null) { if (loader.error == null) Loading() else Footer(loader.error!!); return@Page }
        LazyColumn(Modifier.padding(padding), contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                GroupCard {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Avatar(b.guestName, 56)
                        Text(b.guestName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(b.eventName ?: data.second?.name ?: "Meeting", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        when {
                            b.isPending -> Badge("Waiting for your reply", warningColor())
                            b.isDeclined -> Badge("Declined", MaterialTheme.colorScheme.onSurfaceVariant)
                            b.isCancelled -> Badge("Cancelled", MaterialTheme.colorScheme.onSurfaceVariant)
                            b.isOver() -> Badge("Finished", MaterialTheme.colorScheme.onSurfaceVariant)
                            else -> Badge("Confirmed", successColor())
                        }
                    }
                }
            }
            failure?.let { item { Footer(it) } }
            header("When")
            item {
                GroupCard {
                    ListItem(headlineContent = { Text(Fmt.date(b.start)) }, supportingContent = { Text("${Fmt.time(b.start)} to ${Fmt.time(b.end)}") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                    val link = b.meetingUrl ?: data.second?.meetingUrl
                    if (link != null) {
                        HorizontalDivider()
                        ListItem(headlineContent = { Text("Join meeting") }, supportingContent = { Text(link) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.clickable { openUrl(context, link) })
                    }
                }
            }
            header("Guest")
            item {
                GroupCard {
                    ListItem(headlineContent = { Text("Email") }, trailingContent = { Text(b.guestEmail) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.clickable { openUrl(context, "mailto:${b.guestEmail}") })
                    b.guestPhone?.let { phone ->
                        HorizontalDivider()
                        ListItem(headlineContent = { Text("Phone") }, trailingContent = { Text(phone) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.clickable { openUrl(context, "tel:$phone") })
                    }
                }
            }
            b.guestNotes?.takeIf { it.isNotBlank() }?.let { notes ->
                header("Notes from the guest")
                item { GroupCard { Text(notes, Modifier.padding(16.dp)) } }
            }
            if (live && !b.isPending && b.guestPhone != null) {
                header("Message preview")
                item {
                    // The chat bubble squares off its top-right corner, as in the chat itself
                    Text(
                        message(data.third ?: WhatsApp.confirmation, b), style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().background(bubbleColor(), RoundedCornerShape(12.dp, 0.dp, 12.dp, 12.dp)).padding(14.dp),
                    )
                    Footer("The message opens in WhatsApp ready to send. Nothing is sent until you send it.")
                }
            }
            if (live && !b.isPending) {
                item { TextButton(onClick = { confirm = "cancel" }, modifier = Modifier.fillMaxWidth()) { Text("Cancel meeting", color = MaterialTheme.colorScheme.error) } }
            }
        }
    }

    confirm?.let { kind ->
        val decline = kind == "decline"
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (decline) "Decline this request?" else "Cancel this meeting?") },
            text = { Text(if (decline) "${b?.guestName} will not be booked for this time." else "The time becomes free again on your booking page.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    if (decline) change("cancelled", "declined", WhatsApp.decline) else change("cancelled", null, WhatsApp.cancellation)
                }) { Text(if (decline) "Decline" else "Cancel meeting", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Keep meeting") } },
        )
    }
}

// ── Scheduling ──

@Composable
fun SchedulingScreen(nav: Nav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loader = rememberLoader { Api.eventTypes() }
    var copied by remember { mutableStateOf<String?>(null) }
    Page("Scheduling", actions = {
        // Creating is an action, so it sits in the app bar, not the navigation bar
        IconButton(onClick = { nav.open(Page.EditEvent(null)) }) { Icon(Icons.Filled.Add, "New event type") }
    }) { padding ->
        val events = loader.data ?: run { if (loader.error == null) Loading() else Footer(loader.error!!); return@Page }
        PullToRefreshBox(loader.refreshing, { loader.refresh(true) }, Modifier.padding(padding)) {
            LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (events.isEmpty()) item {
                    GroupCard {
                        Empty(Icons.Filled.Link, "No event types yet", "An event type is a kind of meeting guests can book, with its own link.") {
                            Button(onClick = { nav.open(Page.EditEvent(null)) }) { Text("Create event type") }
                        }
                    }
                }
                items(events, key = { it.id }) { e ->
                    Box(Modifier.alpha(if (e.active) 1f else 0.6f)) {
                        GroupCard {
                            ListItem(
                                headlineContent = { Text(e.name, fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text(listOfNotNull("${e.duration} min", e.location, if (e.requiresApproval) "Needs approval" else null).joinToString(" · ")) },
                                trailingContent = {
                                    Switch(checked = e.active, onCheckedChange = { on ->
                                        loader.data = events.map { if (it.id == e.id) it.copy(active = on) else it }
                                        scope.launch { runCatching { Api.setActive(e.id, on) }.onFailure { loader.refresh() } }
                                    })
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable { nav.open(Page.EditEvent(e.id)) },
                            )
                            HorizontalDivider()
                            Row {
                                TextButton(onClick = { copyText(context, Backend.bookingUrl(e.id)); copied = e.id }, modifier = Modifier.weight(1f)) {
                                    Icon(if (copied == e.id) Icons.Filled.Check else Icons.Filled.ContentCopy, null, Modifier.size(18.dp))
                                    Spacer(Modifier.size(8.dp)); Text(if (copied == e.id) "Copied" else "Copy link")
                                }
                                TextButton(onClick = { shareText(context, Backend.bookingUrl(e.id)) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Filled.Share, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp)); Text("Share")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EventTypeEditor(id: String?, nav: Nav) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf(30) }
    var location by remember { mutableStateOf("Google Meet") }
    var link by remember { mutableStateOf("") }
    var approval by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id == null) return@LaunchedEffect
        runCatching { Api.eventType(id) }.onSuccess { e ->
            name = e.name; description = e.description.orEmpty(); duration = e.duration
            location = e.location ?: "Google Meet"; link = e.meetingUrl.orEmpty(); approval = e.requiresApproval
        }.onFailure { error = "This event type could not be loaded." }
    }

    fun save() {
        if (name.isBlank()) { error = "Give the event type a name, such as “30 minute meeting”."; return }
        busy = true
        val input = EventTypeInput(name.trim(), description.trim().ifEmpty { null }, duration, location, link.trim().ifEmpty { null }, approval)
        scope.launch {
            runCatching { if (id == null) Api.createEventType(input) else Api.updateEventType(id, input) }
                .onSuccess { nav.back() }
                .onFailure { error = "Your changes could not be saved. Check your connection and try again."; busy = false }
        }
    }

    Page(
        if (id == null) "New event type" else "Event type", onBack = nav::back,
        actions = { TextButton(onClick = ::save, enabled = !busy) { Text("Save") } },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, placeholder = { Text("30 minute meeting") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Duration", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf(15, 30, 45, 60, 90) + duration).distinct().sorted().forEach { d ->
                    FilterChip(selected = duration == d, onClick = { duration = d }, label = { Text("$d min") })
                }
            }
            Text("Where", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf("Google Meet", "Zoom", "WhatsApp call", "Phone call", "In person") + location).distinct().forEach { l ->
                    FilterChip(selected = location == l, onClick = { location = l }, label = { Text(l) })
                }
            }
            OutlinedTextField(link, { link = it }, label = { Text("Meeting link") }, placeholder = { Text("https://") }, singleLine = true,
                supportingText = { Text("Optional. Included in the confirmation message.") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text("Description") }, minLines = 3,
                supportingText = { Text("Optional. Guests see this on the booking page.") }, modifier = Modifier.fillMaxWidth())
            GroupCard {
                ListItem(
                    headlineContent = { Text("Approve each booking") },
                    supportingContent = { Text("A booking waits for your approval before it is confirmed.") },
                    trailingContent = { Switch(approval, { approval = it }) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            if (id != null) TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Delete event type", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmDelete && id != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this event type?") },
        text = { Text("Its booking link stops working. Meetings already booked are kept.") },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false
                scope.launch { runCatching { Api.deleteEventType(id) }.onSuccess { nav.back() }.onFailure { error = "This event type could not be deleted. Try again." } }
            }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
    )
}

// ── Hours ──

private data class Editing(val day: Int, val start: Boolean, val value: String)

@Composable
fun HoursScreen() {
    val scope = rememberCoroutineScope()
    val loader = rememberLoader { Api.availability() }
    var editing by remember { mutableStateOf<Editing?>(null) }
    var saving by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // Monday first, Sunday last (day_of_week: 0 = Sunday)
    val order = listOf(1, 2, 3, 4, 5, 6, 0)
    fun dayName(d: Int) = DayOfWeek.of(if (d == 0) 7 else d).getDisplayName(TextStyle.FULL, Locale.getDefault())

    fun setDay(day: Int, from: String?, to: String?) {
        if (from != null && to != null && from >= to) { error = "The end time must be after the start time."; return }
        saving = day; error = null
        scope.launch {
            runCatching { Api.setDay(day, from, to) }.onFailure { error = "${dayName(day)}’s hours could not be saved. Try again." }
            loader.refresh(); saving = null
        }
    }

    Page("Working hours") { padding ->
        val rules = loader.data ?: run { if (loader.error == null) Loading() else Footer(loader.error!!); return@Page }
        PullToRefreshBox(loader.refreshing, { loader.refresh(true) }, Modifier.padding(padding)) {
            LazyColumn(contentPadding = ScreenPadding) {
                error?.let { item { Footer(it) } }
                header("Weekly hours")
                item {
                    GroupCard {
                        order.forEachIndexed { i, day ->
                            if (i > 0) HorizontalDivider()
                            val dayRules = rules.filter { it.dayOfWeek == day }
                            Column(Modifier.padding(16.dp).alpha(if (saving == day) 0.5f else 1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(dayName(day), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    if (dayRules.isEmpty()) Text("Off", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
                                    Switch(
                                        checked = dayRules.isNotEmpty(), enabled = dayRules.size < 2 && saving == null,
                                        onCheckedChange = { on -> if (on) setDay(day, "09:00", "17:00") else setDay(day, null, null) },
                                    )
                                }
                                if (dayRules.size > 1) {
                                    // Split days are set on the web; shown here, not edited
                                    Text(dayRules.joinToString(", ") { "${Fmt.clock(it.startTime)} to ${Fmt.clock(it.endTime)}" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else dayRules.firstOrNull()?.let { r ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(true to r.startTime, false to r.endTime).forEach { (isStart, value) ->
                                            Column(
                                                Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
                                                    .clickable { editing = Editing(day, isStart, value.take(5)) }.padding(horizontal = 12.dp, vertical = 8.dp),
                                            ) {
                                                Text(if (isStart) "From" else "To", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(Fmt.clock(value), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Footer("Guests can book inside these hours. Dates off, holidays and split days are set on the web.")
                }
            }
        }
        editing?.let { e ->
            val state = rememberTimePickerState(e.value.take(2).toInt(), e.value.drop(3).take(2).toInt())
            AlertDialog(
                onDismissRequest = { editing = null },
                title = { Text("${dayName(e.day)} ${if (e.start) "start" else "end"}") },
                text = { TimePicker(state) },
                confirmButton = {
                    TextButton(onClick = {
                        editing = null
                        val picked = "%02d:%02d".format(state.hour, state.minute)
                        val rule = rules.first { it.dayOfWeek == e.day }
                        if (e.start) setDay(e.day, picked, rule.endTime.take(5)) else setDay(e.day, rule.startTime.take(5), picked)
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
            )
        }
    }
}

// ── Settings ──

@Composable
fun SettingsScreen(account: AccountState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember(account.profile) { mutableStateOf(account.profile?.fullName ?: account.hostName) }
    var phone by remember { mutableStateOf("") }
    var template by remember { mutableStateOf(WhatsApp.confirmation) }
    var saved by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmSignOut by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { Api.whatsApp() }.getOrNull()?.let { phone = it.phoneNumber.orEmpty(); template = it.confirmationTemplate ?: WhatsApp.confirmation }
    }

    Page("Settings") { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            GroupCard {
                ListItem(
                    headlineContent = { Text(account.hostName, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(account.email.orEmpty()) },
                    leadingContent = { Avatar(account.hostName, 52) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("Profile", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(name, { name = it; saved = null }, label = { Text("Your name") }, singleLine = true,
                supportingText = { Text("Guests see this on your booking page and in messages.") }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = {
                scope.launch {
                    runCatching { Api.updateName(name.trim()); account.reload() }
                        .onSuccess { saved = "profile"; error = null }.onFailure { error = "Your name could not be saved. Try again." }
                }
            }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (saved == "profile") "Saved" else "Save name") }

            Text("WhatsApp", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(phone, { phone = it; saved = null }, label = { Text("Your WhatsApp number") }, placeholder = { Text("+971 50 123 4567") },
                singleLine = true, supportingText = { Text("Include the country code.") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(template, { template = it; saved = null }, label = { Text("Confirmation message") }, minLines = 4, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = {
                scope.launch {
                    runCatching { Api.saveWhatsApp(phone.trim().ifEmpty { null }, template.trim().ifEmpty { null }) }
                        .onSuccess { saved = "whatsapp"; error = null }.onFailure { error = "Your WhatsApp settings could not be saved. Try again." }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (saved == "whatsapp") "Saved" else "Save WhatsApp settings") }
            Footer("GlassMeet prepares the message and opens WhatsApp; you send it yourself. [Name], [Date] and [Time] are filled in for each meeting.")

            Text("More on the web", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            GroupCard {
                listOf("Open GlassMeet on the web" to "/dashboard", "Privacy Policy" to "/privacy", "Terms of Service" to "/terms").forEachIndexed { i, (label, path) ->
                    if (i > 0) HorizontalDivider()
                    ListItem(headlineContent = { Text(label) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { openUrl(context, Backend.WEB + path) })
                }
            }
            TextButton(onClick = { confirmSignOut = true }, modifier = Modifier.fillMaxWidth()) { Text("Sign out", color = MaterialTheme.colorScheme.error) }
        }
    }

    if (confirmSignOut) AlertDialog(
        onDismissRequest = { confirmSignOut = false },
        title = { Text("Sign out of GlassMeet?") },
        confirmButton = {
            TextButton(onClick = { confirmSignOut = false; scope.launch { runCatching { supabase.auth.signOut() } } }) {
                Text("Sign out", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Stay signed in") } },
    )
}
