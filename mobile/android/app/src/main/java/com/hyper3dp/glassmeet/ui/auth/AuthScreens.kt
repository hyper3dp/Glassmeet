@file:OptIn(ExperimentalMaterial3Api::class)

package com.hyper3dp.glassmeet.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hyper3dp.glassmeet.R
import com.hyper3dp.glassmeet.ui.components.ErrorText
import com.hyper3dp.glassmeet.ui.components.HintText
import com.hyper3dp.glassmeet.ui.theme.AppTheme

// ── Shared layout ──

@Composable
private fun AuthPage(title: String, subtitle: String?, onBack: (() -> Unit)?, showMark: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = {},
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 440.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (showMark) {
                    Spacer(Modifier.height(24.dp))
                    // the brand kit's mark, used as supplied and tinted brand teal
                    Image(
                        painterResource(R.drawable.mark), contentDescription = "GlassMeet",
                        colorFilter = ColorFilter.tint(AppTheme.tokens.brandTeal),
                        modifier = Modifier.size(72.dp).align(Alignment.CenterHorizontally),
                    )
                }
                Text(
                    title, style = MaterialTheme.typography.headlineMedium, textAlign = if (showMark) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth().semantics { heading() },
                )
                if (subtitle != null) {
                    Text(
                        subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge,
                        textAlign = if (showMark) TextAlign.Center else TextAlign.Start, modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(4.dp))
                content()
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun EmailField(value: String, onChange: (String) -> Unit, imeAction: ImeAction = ImeAction.Next, onDone: () -> Unit = {}) {
    OutlinedTextField(
        value, onChange, label = { Text("Email") }, singleLine = true,
        placeholder = { Text("name@example.com", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }, onGo = { onDone() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String = "Password", imeAction: ImeAction = ImeAction.Go, onGo: () -> Unit = {}) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = if (visible) "Hide password" else "Show password")
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onGo = { onGo() }, onDone = { onGo() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CodeField(value: String, onChange: (String) -> Unit, onGo: () -> Unit) {
    OutlinedTextField(
        value, { onChange(com.hyper3dp.glassmeet.util.Format.digits(it).take(10)) }, label = { Text("Code") }, singleLine = true,
        textStyle = MaterialTheme.typography.headlineSmall,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PrimaryButton(text: String, busy: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled && !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

// ── Sign in ──

@Composable
fun SignInScreen(onCreateAccount: () -> Unit, onForgotPassword: () -> Unit, onVerify: (String) -> Unit) {
    val vm: AuthViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val submit = { vm.signIn(email, password, onVerify) }

    AuthPage("Sign in to GlassMeet", "Schedule meetings. Stay on WhatsApp.", onBack = null, showMark = true) {
        EmailField(email, { email = it; vm.clearError() })
        PasswordField(password, { password = it; vm.clearError() }, onGo = submit)
        ErrorText(state.error)
        PrimaryButton("Sign in", busy = state.busy && state.busyProvider == null, enabled = !state.busy, onClick = submit)
        TextButton(onClick = onForgotPassword, modifier = Modifier.align(Alignment.End)) { Text("Forgot password?") }

        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f))
            Text("or", modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.weight(1f))
        }

        ProviderButton("Continue with Google", R.drawable.ic_google, busy = state.busyProvider == "google", enabled = !state.busy, onClick = vm::google)
        ProviderButton("Continue with LinkedIn", R.drawable.ic_linkedin, busy = state.busyProvider == "linkedin", enabled = !state.busy, onClick = vm::linkedIn)

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("New to GlassMeet?", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onCreateAccount) { Text("Create an account") }
        }
    }
}

@Composable
private fun ProviderButton(text: String, logo: Int, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled && !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            // the provider's own logo, in its own colours
            Image(painterResource(logo), contentDescription = null, modifier = Modifier.size(20.dp), alpha = if (enabled) 1f else 0.45f)
            Spacer(Modifier.width(12.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// ── Create account ──

@Composable
fun CreateAccountScreen(onBack: () -> Unit, onVerify: (String) -> Unit) {
    val vm: AuthViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val submit = { vm.signUp(name, email, password, onVerify) }

    AuthPage("Create your account", "Free to start. We'll email you a code to confirm your address.", onBack = onBack, showMark = false) {
        OutlinedTextField(
            name, { name = it; vm.clearError() }, label = { Text("Your name") }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth(),
        )
        EmailField(email, { email = it; vm.clearError() })
        PasswordField(password, { password = it; vm.clearError() }, onGo = submit)
        HintText("At least 8 characters.")
        ErrorText(state.error)
        PrimaryButton("Create account", busy = state.busy, onClick = submit)
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("I already have an account") }
    }
}

// ── Verify code ──

@Composable
fun VerifyCodeScreen(email: String, onBack: () -> Unit) {
    val vm: AuthViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var code by rememberSaveable { mutableStateOf("") }
    val submit = { vm.verify(email, code) }

    AuthPage("Check your email", "Enter the code we sent to $email.", onBack = onBack, showMark = false) {
        CodeField(code, { code = it; vm.clearError() }, onGo = submit)
        ErrorText(state.error)
        state.notice?.let { Text(it, color = AppTheme.tokens.success, style = MaterialTheme.typography.bodyMedium) }
        PrimaryButton("Verify", busy = state.busy, onClick = submit)
        TextButton(onClick = { vm.resend(email) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Send a new code") }
        HintText("No email? Check your spam folder, or go back and check the address.")
    }
}

// ── Reset password ──

@Composable
fun ResetPasswordScreen(onBack: () -> Unit) {
    val vm: AuthViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    val back = {
        when (state.resetStep) {
            0 -> onBack()
            1 -> vm.backToEmail()
            else -> { vm.cancelReset(); onBack() }
        }
    }

    when (state.resetStep) {
        0 -> AuthPage("Reset your password", "Enter your email and we'll send you a code.", onBack = back, showMark = false) {
            EmailField(email, { email = it; vm.clearError() }, imeAction = ImeAction.Done, onDone = { vm.sendResetCode(email) })
            ErrorText(state.error)
            PrimaryButton("Send code", busy = state.busy) { vm.sendResetCode(email) }
        }
        1 -> AuthPage("Enter the code", state.notice ?: "We sent a code to $email.", onBack = back, showMark = false) {
            CodeField(code, { code = it; vm.clearError() }, onGo = { vm.verifyResetCode(email, code) })
            ErrorText(state.error)
            PrimaryButton("Continue", busy = state.busy) { vm.verifyResetCode(email, code) }
            TextButton(onClick = { vm.sendResetCode(email) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Send a new code") }
        }
        else -> AuthPage("Choose a new password", "You'll use it to sign in from now on.", onBack = back, showMark = false) {
            PasswordField(password, { password = it; vm.clearError() }, label = "New password", imeAction = ImeAction.Next)
            PasswordField(confirm, { confirm = it; vm.clearError() }, label = "Repeat new password", onGo = { vm.setNewPassword(password, confirm) })
            ErrorText(state.error)
            PrimaryButton("Save password", busy = state.busy) { vm.setNewPassword(password, confirm) }
        }
    }
}
