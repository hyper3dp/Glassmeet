package com.hyper3dp.glassmeet

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

// The web app's host tokens mapped onto a Material 3 colour scheme, light and dark.
private val Light = lightColorScheme(
    primary = Color(0xFF2B7A75), onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F1F0), onPrimaryContainer = Color(0xFF24706B),
    secondary = Color(0xFF24706B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3F1F0), onSecondaryContainer = Color(0xFF24706B),
    background = Color(0xFFF6F9F9), onBackground = Color(0xFF344248),
    surface = Color(0xFFF6F9F9), onSurface = Color(0xFF344248),
    surfaceVariant = Color(0xFFEEF4F4), onSurfaceVariant = Color(0xFF55686D),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFEEF4F4), surfaceContainerHighest = Color(0xFFE6EEEE),
    outline = Color(0xFFA8C2C0), outlineVariant = Color(0xFFD6E2E1),
    error = Color(0xFFC8372D),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF66ACA9), onPrimary = Color(0xFF14201F),
    primaryContainer = Color(0xFF1B3332), onPrimaryContainer = Color(0xFF7FC4C0),
    secondary = Color(0xFF7FC4C0), onSecondary = Color(0xFF14201F),
    secondaryContainer = Color(0xFF1B3332), onSecondaryContainer = Color(0xFF7FC4C0),
    background = Color(0xFF0F1516), onBackground = Color(0xFFE6EEEE),
    surface = Color(0xFF0F1516), onSurface = Color(0xFFE6EEEE),
    surfaceVariant = Color(0xFF1D2A2C), onSurfaceVariant = Color(0xFFA0B4B4),
    surfaceContainerLowest = Color(0xFF0F1516), surfaceContainerLow = Color(0xFF162022),
    surfaceContainer = Color(0xFF162022), surfaceContainerHigh = Color(0xFF1D2A2C), surfaceContainerHighest = Color(0xFF243335),
    outline = Color(0xFF3E5153), outlineVariant = Color(0xFF2A3A3C),
    error = Color(0xFFFF7B72),
)

/** The logo mark's colour; never used for text */
val BrandTeal = Color(0xFF66ACA9)

@Composable
fun GlassMeetTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

@Composable
fun bubbleColor() = if (isSystemInDarkTheme()) Color(0xFF144D37) else Color(0xFFD9FDD3)

@Composable
fun warningColor() = if (isSystemInDarkTheme()) Color(0xFFE3A044) else Color(0xFFB25E00)

@Composable
fun successColor() = if (isSystemInDarkTheme()) Color(0xFF3FB97A) else Color(0xFF0A8A4C)

// ── Dates ──

object Fmt {
    private val zone get() = ZoneId.systemDefault()
    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val short = DateTimeFormatter.ofPattern("EEE, MMM d")
    private val full = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)

    fun time(i: Instant): String = time.format(i.atZone(zone))
    fun date(i: Instant): String = full.format(i.atZone(zone))
    fun day(i: Instant): String {
        val days = ChronoUnit.DAYS.between(Instant.now().atZone(zone).toLocalDate(), i.atZone(zone).toLocalDate())
        return when (days) {
            0L -> "Today"
            1L -> "Tomorrow"
            -1L -> "Yesterday"
            else -> short.format(i.atZone(zone))
        }
    }
    fun whenText(i: Instant) = "${day(i)} at ${time(i)}"

    /** "09:00:00" → "9:00 AM" */
    fun clock(value: String): String = time.format(LocalTime.parse(value.take(5)))

    fun initials(name: String) =
        name.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("").ifEmpty { "?" }
}
