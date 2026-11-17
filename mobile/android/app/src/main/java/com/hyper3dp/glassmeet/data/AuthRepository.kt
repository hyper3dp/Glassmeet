package com.hyper3dp.glassmeet.data

import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.LinkedInOIDC
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Signing in and out, accounts and passwords (web: login.jsx, register.jsx, forgotpassword.jsx). */
object AuthRepository {
    val sessionStatus: StateFlow<SessionStatus> get() = supabase.auth.sessionStatus
    val email: String? get() = supabase.auth.currentUserOrNull()?.email

    enum class SignUpResult { SignedIn, NeedsCode, AlreadyExists }

    class NotConfirmed : Exception("Confirm your email first. We sent you a code.")

    suspend fun signIn(email: String, password: String) {
        try {
            supabase.auth.signInWith(Email) {
                this.email = email.trim()
                this.password = password
            }
        } catch (e: AuthRestException) {
            if (e.error == "email_not_confirmed") throw NotConfirmed()
            throw Exception(authMessage(e))
        }
    }

    /** Opens Google's sign-in in a Custom Tab; the result comes back on glassmeet://auth-callback. */
    suspend fun signInWithGoogle() = supabase.auth.signInWith(Google, redirectUrl = Backend.AUTH_CALLBACK)

    /** Supabase provider "linkedin_oidc". */
    suspend fun signInWithLinkedIn() = supabase.auth.signInWith(LinkedInOIDC, redirectUrl = Backend.AUTH_CALLBACK)

    suspend fun signUp(name: String, email: String, password: String): SignUpResult {
        val user = try {
            supabase.auth.signUpWith(Email, redirectUrl = Backend.AUTH_CALLBACK) {
                this.email = email.trim()
                this.password = password
                data = buildJsonObject { put("full_name", name.trim()) }
            }
        } catch (e: AuthRestException) {
            if (e.error == "user_already_exists" || e.error == "email_exists") return SignUpResult.AlreadyExists
            throw Exception(authMessage(e))
        }
        // an existing, confirmed address comes back as a user with no identities
        if (user != null && user.identities?.isEmpty() == true) return SignUpResult.AlreadyExists
        return if (supabase.auth.currentSessionOrNull() != null) SignUpResult.SignedIn else SignUpResult.NeedsCode
    }

    suspend fun verifySignUp(email: String, code: String) {
        try {
            supabase.auth.verifyEmailOtp(OtpType.Email.SIGNUP, email.trim(), code.trim())
        } catch (e: AuthRestException) {
            throw Exception(codeMessage(e))
        }
    }

    suspend fun resendSignUp(email: String) {
        try {
            supabase.auth.resendEmail(OtpType.Email.SIGNUP, email.trim())
        } catch (e: AuthRestException) {
            throw Exception(authMessage(e))
        }
    }

    suspend fun sendResetCode(email: String) {
        try {
            supabase.auth.resetPasswordForEmail(email.trim())
        } catch (e: AuthRestException) {
            throw Exception(authMessage(e))
        }
    }

    /** Checks the recovery code, which signs the host in so the new password can be set. */
    suspend fun verifyResetCode(email: String, code: String) {
        try {
            supabase.auth.verifyEmailOtp(OtpType.Email.RECOVERY, email.trim(), code.trim())
        } catch (e: AuthRestException) {
            throw Exception(codeMessage(e))
        }
    }

    suspend fun setPassword(password: String) {
        try {
            supabase.auth.updateUser { this.password = password }
        } catch (e: AuthRestException) {
            throw Exception(authMessage(e))
        }
    }

    suspend fun signOut() {
        runCatching { supabase.auth.signOut() }
        ProfileRepository.clear()
        NotificationsRepository.clear()
    }

    private fun authMessage(e: AuthRestException): String = when (e.error) {
        "invalid_credentials" -> "That email and password don't match. Check them and try again."
        "email_not_confirmed" -> "Confirm your email first. Check your inbox (and spam) for the code."
        "weak_password" -> "Choose a stronger password: at least 8 characters."
        "over_email_send_rate_limit", "over_request_rate_limit" -> "Too many tries. Wait a minute, then try again."
        "same_password" -> "Choose a password you haven't used here before."
        "user_not_found" -> "No account uses that email."
        "validation_failed" -> "Check the email address."
        else -> e.errorDescription.ifBlank { "That didn't work. Try again." }
    }

    private fun codeMessage(e: AuthRestException): String = when (e.error) {
        "otp_expired" -> "That code has expired or is wrong. Ask for a new one."
        else -> authMessage(e)
    }
}
