package com.hyper3dp.glassmeet.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hyper3dp.glassmeet.data.Backend
import com.hyper3dp.glassmeet.ui.components.Avatar
import com.hyper3dp.glassmeet.ui.components.ConfirmDialog
import com.hyper3dp.glassmeet.ui.components.ErrorState
import com.hyper3dp.glassmeet.ui.components.ErrorText
import com.hyper3dp.glassmeet.ui.components.HintText
import com.hyper3dp.glassmeet.ui.components.LoadingState
import com.hyper3dp.glassmeet.ui.components.NavRow
import com.hyper3dp.glassmeet.ui.components.SectionCard
import com.hyper3dp.glassmeet.ui.components.SectionTitle
import com.hyper3dp.glassmeet.ui.components.SelectField
import com.hyper3dp.glassmeet.ui.components.TopLevelScaffold
import com.hyper3dp.glassmeet.ui.theme.AppTheme
import com.hyper3dp.glassmeet.util.Intents

@Composable
fun SettingsScreen(
    onOpenWhatsApp: () -> Unit,
    onOpenCalendars: () -> Unit,
    onOpenAddresses: () -> Unit,
    onOpenAvailability: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
) {
    val vm: SettingsViewModel = viewModel()
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmSignOut by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.uploadPhoto(context, uri)
    }

    TopLevelScaffold(
        title = "Settings",
        actions = {
            if (s.dirty) {
                TextButton(onClick = vm::save, enabled = !s.saving) {
                    if (s.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Save")
                }
            }
        },
    ) { inner ->
        when {
            s.loading -> LoadingState(Modifier.padding(top = inner.calculateTopPadding()))
            s.error != null && s.saved == ProfileForm() -> ErrorState(s.error ?: "", onRetry = vm::load, modifier = Modifier.padding(top = inner.calculateTopPadding()))
            else -> Column(
                Modifier.fillMaxSize().padding(top = inner.calculateTopPadding()).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val f = s.form
                SectionTitle("Profile")
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.Center) {
                            Avatar(f.name.ifBlank { s.email }, photoUrl = f.photo.ifBlank { null }, size = 72.dp)
                            if (s.uploading) CircularProgressIndicator(Modifier.size(72.dp), strokeWidth = 3.dp)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            TextButton(
                                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                enabled = !s.uploading,
                            ) {
                                Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (f.photo.isBlank()) "Add photo" else "Change photo")
                            }
                            if (f.photo.isNotBlank()) TextButton(onClick = vm::removePhoto, enabled = !s.uploading) { Text("Remove") }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(f.name, { v -> vm.edit { it.copy(name = v.take(80)) } }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(
                            f.username, { v -> vm.edit { it.copy(username = v.lowercase().filter { c -> c in 'a'..'z' || c in '0'..'9' || c == '_' }.take(40)) } },
                            label = { Text("Username") }, singleLine = true, prefix = { Text("@") },
                            supportingText = { Text("Letters, numbers and _") }, modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            s.email, {}, readOnly = true, enabled = false, label = { Text("Email") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            f.bio, { v -> vm.edit { it.copy(bio = v.take(300)) } }, label = { Text("Bio") }, minLines = 2,
                            supportingText = { Text("Shown on your booking pages") }, modifier = Modifier.fillMaxWidth(),
                        )
                        val zones = (listOf(f.timezone) + COMMON_TIMEZONES).distinct()
                        SelectField("Time zone", zones.map { it to it.replace('_', ' ') }, f.timezone, { v -> vm.edit { it.copy(timezone = v) } }, Modifier.fillMaxWidth())
                        OutlinedTextField(
                            f.whatsapp, { v -> vm.edit { it.copy(whatsapp = com.hyper3dp.glassmeet.util.Format.asciiDigits(v).filter { c -> c in '0'..'9' || c in "+ -()" }.take(24)) } },
                            label = { Text("WhatsApp number") }, singleLine = true, placeholder = { Text("+971 50 123 4567") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
                        )
                        ErrorText(s.formError)
                        if (s.dirty) {
                            Button(onClick = vm::save, enabled = !s.saving, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                                if (s.saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                else Text("Save profile")
                            }
                        }
                        HintText("Your name, photo and time zone are also copied to your event types.")
                    }
                }

                SectionTitle("Scheduling")
                SectionCard(padding = 0.dp) {
                    NavRow("Availability", Icons.Outlined.Schedule, "Weekly hours, days off and holidays", tint = AppTheme.tokens.link, onClick = onOpenAvailability)
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = AppTheme.tokens.border)
                    NavRow("Booking messages", Icons.Outlined.NotificationsActive, "New bookings, requests and cancellations", tint = AppTheme.tokens.link, onClick = onOpenNotifications)
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = AppTheme.tokens.border)
                    NavRow("WhatsApp messages", Icons.Outlined.Forum, "Your number and message templates", tint = AppTheme.tokens.link, onClick = onOpenWhatsApp)
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = AppTheme.tokens.border)
                    NavRow("Calendar connections", Icons.Outlined.EventAvailable, "Google, Outlook and Apple Calendar", tint = AppTheme.tokens.link, onClick = onOpenCalendars)
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = AppTheme.tokens.border)
                    NavRow("Meeting addresses", Icons.Outlined.LocationOn, "Places you meet in person", tint = AppTheme.tokens.link, onClick = onOpenAddresses)
                }

                SectionTitle("Security")
                SectionCard(padding = 0.dp) {
                    NavRow("Change password", Icons.Outlined.Password, "We'll email you a code", tint = AppTheme.tokens.link, onClick = vm::startPasswordChange)
                }

                SectionTitle("About")
                SectionCard(padding = 0.dp) {
                    NavRow("Open GlassMeet on the web", Icons.Outlined.Language, showChevron = false, tint = AppTheme.tokens.link,
                        trailing = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = AppTheme.tokens.textTertiary, modifier = Modifier.size(18.dp)) },
                        onClick = { Intents.open(context, "${Backend.WEB}/dashboard") })
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = AppTheme.tokens.border)
                    NavRow("Privacy Policy", Icons.Outlined.Shield, tint = AppTheme.tokens.link, onClick = { Intents.customTab(context, "${Backend.WEB}/privacy") })
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = AppTheme.tokens.border)
                    NavRow("Terms of Service", Icons.Outlined.Gavel, tint = AppTheme.tokens.link, onClick = { Intents.customTab(context, "${Backend.WEB}/terms") })
                }

                SectionTitle("Account")
                SectionCard(padding = 0.dp) {
                    NavRow("Sign out", Icons.AutoMirrored.Outlined.Logout, showChevron = false, onClick = { confirmSignOut = true })
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = AppTheme.tokens.border)
                    NavRow(
                        "Delete account", Icons.Outlined.Delete, tint = MaterialTheme.colorScheme.error, titleColor = MaterialTheme.colorScheme.error,
                        showChevron = false, onClick = { deleteOpen = true },
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmSignOut) {
        ConfirmDialog(
            "Sign out?", "You can sign back in any time.", "Sign out",
            onConfirm = { confirmSignOut = false; vm.signOut() }, onDismiss = { confirmSignOut = false }, dismissLabel = "Cancel",
        )
    }
    if (deleteOpen) DeleteAccountDialog(s, onDelete = vm::deleteAccount, onDismiss = { if (!s.deleting) deleteOpen = false })
    if (s.passwordStep > 0) PasswordDialog(s, vm)
}

@Composable
private fun DeleteAccountDialog(s: SettingsUiState, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Delete your account?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("This deletes your event types, meetings, hours and settings, and disconnects your calendars. It can't be undone.")
                OutlinedTextField(typed, { typed = it }, label = { Text("Type DELETE to confirm") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ErrorText(s.deleteError)
            }
        },
        confirmButton = {
            TextButton(onClick = onDelete, enabled = typed.trim() == "DELETE" && !s.deleting) {
                if (s.deleting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Delete account", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !s.deleting) { Text("Cancel") } },
    )
}

@Composable
private fun PasswordDialog(s: SettingsUiState, vm: SettingsViewModel) {
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = vm::closePassword,
        title = { Text("Change password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("We sent a code to ${s.email}.")
                OutlinedTextField(
                    code, { code = com.hyper3dp.glassmeet.util.Format.digits(it).take(10) }, label = { Text("Code") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    password, { password = it }, label = { Text("New password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
                )
                ErrorText(s.passwordError)
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.finishPasswordChange(code, password) }, enabled = !s.passwordBusy) {
                if (s.passwordBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = vm::closePassword) { Text("Cancel") } },
    )
}
