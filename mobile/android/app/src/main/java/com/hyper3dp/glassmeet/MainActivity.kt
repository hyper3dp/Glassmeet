package com.hyper3dp.glassmeet

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hyper3dp.glassmeet.data.supabase
import com.hyper3dp.glassmeet.ui.navigation.GlassMeetApp
import com.hyper3dp.glassmeet.ui.navigation.NavRequests
import com.hyper3dp.glassmeet.ui.theme.GlassMeetTheme
import io.github.jan.supabase.auth.handleDeeplinks

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent { GlassMeetTheme { GlassMeetApp() } }
    }

    // launchMode singleTop: the OAuth and calendar callbacks arrive here while the app is open
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent == null) return
        val data = intent.data ?: return
        // https://glassmeet.run.place/... links (booking pages, meeting links in emails)
        if (data.scheme == "https" && data.host == "glassmeet.run.place") {
            val parts = data.pathSegments
            if (parts.firstOrNull() == "book" && parts.size >= 2) NavRequests.openLink.value = "book:${parts[1]}"
            else NavRequests.openLink.value = data.path + (data.query?.let { "?$it" } ?: "")
            return
        }
        if (data.scheme != "glassmeet") return
        when (data.host) {
            // glassmeet://auth-callback: Supabase finishes the Google / LinkedIn sign-in
            "auth-callback" -> supabase.handleDeeplinks(intent)
            // glassmeet://calendar-connections?google=finish&code=..&state=.. (or ?connect=finish&provider=microsoft..)
            "calendar-connections" -> NavRequests.calendarCallback.value = data
            // glassmeet://book/<event type id>
            "book" -> data.pathSegments.firstOrNull()?.let { NavRequests.openLink.value = "book:$it" }
        }
    }
}
