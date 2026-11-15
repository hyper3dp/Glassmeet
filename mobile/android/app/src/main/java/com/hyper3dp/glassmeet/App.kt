@file:OptIn(ExperimentalMaterial3Api::class)

package com.hyper3dp.glassmeet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch

/** Who is signed in, and their display name. */
class AccountState {
    var profile by mutableStateOf<Profile?>(null)
    val email: String? get() = supabase.auth.currentUserOrNull()?.email
    val hostName: String
        get() = profile?.fullName?.takeIf { it.isNotBlank() } ?: email?.substringBefore("@").orEmpty()
    suspend fun reload() {
        profile = runCatching { Api.profile() }.getOrNull()
    }
}

enum class Section(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home("Home", Icons.Outlined.Home, Icons.Filled.Home),
    Meetings("Meetings", Icons.Outlined.Groups, Icons.Filled.Groups),
    Scheduling("Scheduling", Icons.Filled.Link, Icons.Filled.Link),
    Hours("Hours", Icons.Outlined.Schedule, Icons.Filled.Schedule),
    Settings("Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
}

/** Pages opened on top of a section. */
sealed interface Page {
    data class Meeting(val id: String) : Page
    data class EditEvent(val id: String?) : Page
}

class Nav {
    val pages = mutableStateListOf<Page>()
    fun open(page: Page) = pages.add(page)
    fun back() { if (pages.isNotEmpty()) pages.removeAt(pages.lastIndex) }
}

@Composable
fun GlassMeetApp() {
    val status by supabase.auth.sessionStatus.collectAsState()
    when (status) {
        is SessionStatus.Initializing -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        is SessionStatus.Authenticated -> SignedIn()
        else -> SignIn()
    }
}

@Composable
private fun SignedIn() {
    val account = remember { AccountState() }
    val nav = remember { Nav() }
    var section by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { account.reload() }
    BackHandler(enabled = nav.pages.isNotEmpty()) { nav.back() }

    // Material 3 navigation bar: five destinations, each with a label and an icon that fills when selected
    Scaffold(
        bottomBar = {
            NavigationBar {
                Section.entries.forEachIndexed { i, s ->
                    NavigationBarItem(
                        selected = i == section,
                        onClick = { section = i; nav.pages.clear() },
                        icon = { Icon(if (i == section) s.selectedIcon else s.icon, contentDescription = null) },
                        label = { Text(s.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(bottom = padding.calculateBottomPadding())) {
            when (val page = nav.pages.lastOrNull()) {
                is Page.Meeting -> MeetingScreen(page.id, account, nav)
                is Page.EditEvent -> EventTypeEditor(page.id, nav)
                null -> when (Section.entries[section]) {
                    Section.Home -> HomeScreen(account, nav)
                    Section.Meetings -> MeetingsScreen(nav)
                    Section.Scheduling -> SchedulingScreen(nav)
                    Section.Hours -> HoursScreen()
                    Section.Settings -> SettingsScreen(account)
                }
            }
        }
    }
}

@Composable
private fun SignIn() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun signIn() {
        if (email.isBlank() || password.isEmpty()) { error = "Enter your email and password."; return }
        busy = true
        scope.launch {
            runCatching {
                supabase.auth.signInWith(Email) {
                    this.email = email.trim()
                    this.password = password
                }
            }.onFailure {
                val text = it.message.orEmpty()
                error = when {
                    text.contains("invalid", true) -> "That email and password don’t match. Check them and try again."
                    text.contains("confirm", true) -> "Confirm your email before signing in. Check your inbox (and spam) for the code."
                    else -> text.ifEmpty { "Sign-in failed. Check your connection and try again." }
                }
            }
            busy = false
        }
    }

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(Modifier.widthIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // The brand kit's mark, used as supplied and tinted brand teal
                Image(
                    painterResource(R.drawable.mark), contentDescription = "GlassMeet",
                    colorFilter = ColorFilter.tint(BrandTeal),
                    modifier = Modifier.size(72.dp).align(Alignment.CenterHorizontally),
                )
                Text(
                    "Sign in to GlassMeet", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().semantics { heading() },
                )
                Text(
                    "Schedule meetings. Stay on WhatsApp.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    email, { email = it }, label = { Text("Email") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    password, { password = it }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                Button(onClick = ::signIn, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Sign in")
                }
                TextButton(onClick = { openUrl(context, "${Backend.WEB}/forgot-password") }, modifier = Modifier.fillMaxWidth()) { Text("Forgot password?") }
                TextButton(onClick = { openUrl(context, "${Backend.WEB}/register") }, modifier = Modifier.fillMaxWidth()) { Text("Create an account") }
            }
        }
    }
}

// ── Small helpers shared by the screens ──

fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

fun openUri(context: Context, uri: Uri) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}

fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    context.startActivity(Intent.createChooser(send, null))
}

fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Booking link", text))
}

@Composable
fun Avatar(name: String, size: Int = 40) {
    Box(
        Modifier.size(size.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(Fmt.initials(name), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

val ScreenPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)

@Composable
fun Row2(content: @Composable () -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) { content() }
