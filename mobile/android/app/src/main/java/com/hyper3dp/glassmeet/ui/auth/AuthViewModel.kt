package com.hyper3dp.glassmeet.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyper3dp.glassmeet.data.AuthRepository
import com.hyper3dp.glassmeet.data.friendly
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** While a password reset is between "code checked" and "new password saved", stay on the auth screens. */
object AuthFlow {
    val recovering = MutableStateFlow(false)
}

data class AuthUiState(
    val busy: Boolean = false,
    val busyProvider: String? = null,
    val error: String? = null,
    val notice: String? = null,
    /** Reset password: 0 = email, 1 = code, 2 = new password */
    val resetStep: Int = 0,
)

class AuthViewModel : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private fun perform(provider: String? = null, work: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, busyProvider = provider, error = null, notice = null) }
        viewModelScope.launch {
            try {
                work()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.friendly("That didn't work. Try again.")) }
            } finally {
                _state.update { it.copy(busy = false, busyProvider = null) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun signIn(email: String, password: String, onNotConfirmed: (String) -> Unit) {
        if (email.isBlank() || password.isEmpty()) { _state.update { it.copy(error = "Enter your email and password.") }; return }
        perform {
            try {
                AuthRepository.signIn(email, password)
            } catch (e: AuthRepository.NotConfirmed) {
                runCatching { AuthRepository.resendSignUp(email) }
                onNotConfirmed(email.trim())
            }
        }
    }

    fun google() = perform("google") { AuthRepository.signInWithGoogle() }
    fun linkedIn() = perform("linkedin") { AuthRepository.signInWithLinkedIn() }

    fun signUp(name: String, email: String, password: String, onVerify: (String) -> Unit) {
        when {
            name.isBlank() -> { _state.update { it.copy(error = "Enter your name.") }; return }
            !email.contains("@") -> { _state.update { it.copy(error = "Enter a valid email address.") }; return }
            password.length < 8 -> { _state.update { it.copy(error = "Use at least 8 characters for your password.") }; return }
        }
        perform {
            when (AuthRepository.signUp(name, email, password)) {
                AuthRepository.SignUpResult.SignedIn -> Unit // the session switches the app over
                AuthRepository.SignUpResult.NeedsCode -> onVerify(email.trim())
                AuthRepository.SignUpResult.AlreadyExists ->
                    _state.update { it.copy(error = "An account already uses this email. Sign in instead, or reset your password.") }
            }
        }
    }

    fun verify(email: String, code: String) {
        val digits = code.filter { it.isDigit() }
        if (digits.length !in 6..10) { _state.update { it.copy(error = "Enter the code from the email (6 to 10 digits).") }; return }
        perform { AuthRepository.verifySignUp(email, digits) }
    }

    fun resend(email: String) = perform {
        AuthRepository.resendSignUp(email)
        _state.update { it.copy(notice = "We sent a new code to $email.") }
    }

    // ── Reset password: email -> code -> new password ──

    fun sendResetCode(email: String) {
        if (!email.contains("@")) { _state.update { it.copy(error = "Enter the email you signed up with.") }; return }
        perform {
            AuthRepository.sendResetCode(email)
            _state.update { it.copy(resetStep = 1, notice = "If an account uses $email, a code is on its way.") }
        }
    }

    fun verifyResetCode(email: String, code: String) {
        val digits = code.filter { it.isDigit() }
        if (digits.length !in 6..10) { _state.update { it.copy(error = "Enter the code from the email (6 to 10 digits).") }; return }
        AuthFlow.recovering.value = true
        perform {
            try {
                AuthRepository.verifyResetCode(email, digits)
                _state.update { it.copy(resetStep = 2, notice = null) }
            } catch (e: Exception) {
                AuthFlow.recovering.value = false
                throw e
            }
        }
    }

    fun setNewPassword(password: String, confirm: String) {
        when {
            password.length < 8 -> { _state.update { it.copy(error = "Use at least 8 characters.") }; return }
            password != confirm -> { _state.update { it.copy(error = "The passwords don't match.") }; return }
        }
        perform {
            AuthRepository.setPassword(password)
            // signed in with the new password: let the app open
            AuthFlow.recovering.value = false
        }
    }

    fun cancelReset() {
        if (AuthFlow.recovering.value) {
            AuthFlow.recovering.value = false
            viewModelScope.launch { AuthRepository.signOut() }
        }
    }

    fun backToEmail() = _state.update { it.copy(resetStep = 0, error = null, notice = null) }
}
