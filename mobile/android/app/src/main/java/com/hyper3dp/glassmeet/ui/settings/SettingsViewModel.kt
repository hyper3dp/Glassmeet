package com.hyper3dp.glassmeet.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyper3dp.glassmeet.data.AuthRepository
import com.hyper3dp.glassmeet.data.Profile
import com.hyper3dp.glassmeet.data.ProfileRepository
import com.hyper3dp.glassmeet.data.friendly
import com.hyper3dp.glassmeet.util.Snack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.ZoneId

val COMMON_TIMEZONES = listOf(
    "UTC", "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles", "America/Sao_Paulo",
    "Europe/London", "Europe/Paris", "Europe/Berlin", "Europe/Madrid", "Africa/Lagos", "Africa/Johannesburg",
    "Asia/Dubai", "Asia/Kolkata", "Asia/Singapore", "Asia/Tokyo", "Australia/Sydney", "Pacific/Auckland",
)

data class ProfileForm(
    val name: String = "",
    val username: String = "",
    val bio: String = "",
    val timezone: String = ZoneId.systemDefault().id,
    val whatsapp: String = "",
    val photo: String = "",
) {
    companion object {
        fun of(p: Profile) = ProfileForm(
            name = p.fullName.orEmpty(),
            username = p.username.orEmpty(),
            bio = p.bio.orEmpty(),
            timezone = p.timezone?.takeIf { it.isNotBlank() } ?: ZoneId.systemDefault().id,
            whatsapp = p.whatsappNumber.orEmpty(),
            photo = p.photo.orEmpty(),
        )
    }
}

data class SettingsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val email: String = "",
    val form: ProfileForm = ProfileForm(),
    val saved: ProfileForm = ProfileForm(),
    val saving: Boolean = false,
    val uploading: Boolean = false,
    val formError: String? = null,
    val deleting: Boolean = false,
    val deleteError: String? = null,
    // change password: 0 closed, 1 code sent, 2 code checked
    val passwordStep: Int = 0,
    val passwordBusy: Boolean = false,
    val passwordError: String? = null,
) {
    val dirty get() = form != saved
}

class SettingsViewModel : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = it.saved == ProfileForm() && it.form == ProfileForm(), error = null) }
        viewModelScope.launch {
            try {
                val p = ProfileRepository.load()
                val form = ProfileForm.of(p)
                _state.update { it.copy(loading = false, form = form, saved = form, email = p.email ?: AuthRepository.email.orEmpty()) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.friendly("Your profile didn't load.")) }
            }
        }
    }

    fun edit(change: (ProfileForm) -> ProfileForm) = _state.update { it.copy(form = change(it.form), formError = null) }

    fun save() {
        val f = _state.value.form
        if (_state.value.saving) return
        val digits = f.whatsapp.filter { it.isDigit() }
        if (f.whatsapp.isNotBlank() && digits.length !in 7..15) {
            _state.update { it.copy(formError = "Enter the WhatsApp number with its country code, like +971 50 123 4567.") }
            return
        }
        if (f.username.isNotEmpty() && f.username.length < 3) {
            _state.update { it.copy(formError = "Usernames need at least 3 characters.") }
            return
        }
        _state.update { it.copy(saving = true, formError = null) }
        viewModelScope.launch {
            try {
                ProfileRepository.update(buildJsonObject {
                    put("full_name", f.name.trim())
                    if (f.username.isBlank()) put("username", JsonNull) else put("username", f.username.trim())
                    put("bio", f.bio.trim().ifEmpty { null })
                    put("timezone", f.timezone)
                    put("whatsapp_number", f.whatsapp.trim().ifEmpty { null })
                    if (f.photo.isBlank()) put("profile_photo", JsonNull) else put("profile_photo", f.photo)
                })
                _state.update { it.copy(saving = false, saved = f) }
                Snack.show("Profile saved")
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, formError = e.friendly("Could not save your profile.")) }
            }
        }
    }

    /** Photo Picker result: checked, uploaded to avatars/<uid>/avatar-<time>.<ext>, then saved. */
    fun uploadPhoto(context: Context, uri: Uri) {
        _state.update { it.copy(uploading = true, formError = null) }
        viewModelScope.launch {
            try {
                val resolver = context.contentResolver
                val mime = resolver.getType(uri) ?: "image/jpeg"
                val ext = when (mime) {
                    "image/jpeg", "image/jpg" -> "jpg"
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    "image/gif" -> "gif"
                    else -> throw Exception("Choose a JPG, PNG or WebP image.")
                }
                val bytes = withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { it.readBytes() } }
                    ?: throw Exception("That photo couldn't be read.")
                if (bytes.size > 5 * 1024 * 1024) throw Exception("Choose an image under 5 MB.")
                val url = ProfileRepository.uploadAvatar(bytes, ext, mime)
                ProfileRepository.update(buildJsonObject { put("profile_photo", url) })
                _state.update { it.copy(uploading = false, form = it.form.copy(photo = url), saved = it.saved.copy(photo = url)) }
                Snack.show("Photo updated")
            } catch (e: Exception) {
                _state.update { it.copy(uploading = false, formError = "Upload failed: ${e.friendly("try again.")}") }
            }
        }
    }

    fun removePhoto() {
        edit { it.copy(photo = "") }
        save()
    }

    fun signOut() = viewModelScope.launch { AuthRepository.signOut() }

    fun deleteAccount() {
        _state.update { it.copy(deleting = true, deleteError = null) }
        viewModelScope.launch {
            try {
                ProfileRepository.deleteAccount()
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                _state.update {
                    it.copy(
                        deleting = false,
                        deleteError = if (msg.contains("delete_my_account") || msg.contains("PGRST202")) "Deleting accounts isn't available yet. Try again later."
                        else e.friendly("Your account couldn't be deleted. Try again."),
                    )
                }
            }
        }
    }

    // ── Change password: code by email, then a new password ──

    fun startPasswordChange() {
        val email = _state.value.email
        _state.update { it.copy(passwordBusy = true, passwordError = null) }
        viewModelScope.launch {
            try {
                AuthRepository.sendResetCode(email)
                _state.update { it.copy(passwordBusy = false, passwordStep = 1) }
            } catch (e: Exception) {
                _state.update { it.copy(passwordBusy = false, passwordError = e.friendly("Could not send the code."), passwordStep = 1) }
            }
        }
    }

    fun finishPasswordChange(code: String, password: String) {
        if (password.length < 8) { _state.update { it.copy(passwordError = "Use at least 8 characters.") }; return }
        val digits = code.filter { it.isDigit() }
        if (digits.length !in 6..10) { _state.update { it.copy(passwordError = "Enter the code from the email.") }; return }
        _state.update { it.copy(passwordBusy = true, passwordError = null) }
        viewModelScope.launch {
            try {
                AuthRepository.verifyResetCode(_state.value.email, digits)
                AuthRepository.setPassword(password)
                _state.update { it.copy(passwordBusy = false, passwordStep = 0) }
                Snack.show("Password changed")
            } catch (e: Exception) {
                _state.update { it.copy(passwordBusy = false, passwordError = e.friendly("Could not change the password.")) }
            }
        }
    }

    fun closePassword() = _state.update { it.copy(passwordStep = 0, passwordError = null) }
}
