package com.hyper3dp.glassmeet.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Opens links, the share sheet and the clipboard. */
object Intents {
    fun open(context: Context, url: String) = open(context, Uri.parse(url))

    fun open(context: Context, uri: Uri) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Snack.show("No app on this phone can open that link.") }
    }

    /** In-app browser tab (Custom Tabs), for sign-in style pages that return to the app. */
    fun customTab(context: Context, url: String) {
        runCatching { CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url)) }
            .onFailure { open(context, url) }
    }

    /** The system share sheet (it includes WhatsApp when installed). */
    fun share(context: Context, text: String, title: String? = null) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (title != null) putExtra(Intent.EXTRA_TITLE, title)
        }
        runCatching { context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun copy(context: Context, text: String, label: String = "GlassMeet", toast: String? = "Copied") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        // Android 13+ shows its own confirmation
        if (toast != null && android.os.Build.VERSION.SDK_INT < 33) Snack.show(toast)
    }

    fun email(context: Context, to: String?, subject: String, body: String) {
        val uri = Uri.parse("mailto:" + Uri.encode(to.orEmpty()))
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Snack.show("No email app is set up on this phone.") }
    }

    fun dial(context: Context, phone: String) = open(context, Uri.parse("tel:" + Uri.encode(phone)))
}

/** App-wide snackbar messages ("Link copied", "Saved"). The main scaffold shows them. */
object Snack {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    fun show(text: String) { _messages.tryEmit(text) }
}
