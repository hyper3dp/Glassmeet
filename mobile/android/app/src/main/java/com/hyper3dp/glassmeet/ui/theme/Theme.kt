package com.hyper3dp.glassmeet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LocalTokens = staticCompositionLocalOf { LightTokens }

/** The SPEC tokens, for what Material's colour roles don't name (success, warning, link, ...). */
object AppTheme {
    val tokens: Tokens
        @Composable @ReadOnlyComposable get() = LocalTokens.current
}

/** Material shape scale from SPEC.md: small 8, medium 12, large 16, extraLarge 28. */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * The SPEC tokens mapped onto Material 3 roles. Dynamic colour stays off so the brand holds.
 * Page = background (and surface, so top app bars sit on the page); cards = surfaceContainerLow;
 * fields, chips and tiles = surfaceContainerHighest.
 */
private fun scheme(t: Tokens): ColorScheme {
    val base = if (t.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = t.primary,
        onPrimary = t.onPrimary,
        primaryContainer = t.primarySoft,
        onPrimaryContainer = t.link,
        inversePrimary = t.brandTeal,
        secondary = t.primary,
        onSecondary = t.onPrimary,
        secondaryContainer = t.primarySoft,
        onSecondaryContainer = t.link,
        tertiary = t.warning,
        onTertiary = if (t.isDark) Color(0xFF1F1405) else Color.White,
        tertiaryContainer = if (t.isDark) Color(0xFF3A2A12) else Color(0xFFFFF1DF),
        onTertiaryContainer = t.warning,
        background = t.background,
        onBackground = t.text,
        surface = t.background,
        onSurface = t.text,
        surfaceVariant = t.surfaceRaised,
        onSurfaceVariant = t.textSecondary,
        surfaceTint = t.primary,
        surfaceBright = t.surface,
        surfaceDim = t.background,
        surfaceContainerLowest = t.surface,
        surfaceContainerLow = t.surface,
        surfaceContainer = t.surface,
        surfaceContainerHigh = t.surface,
        surfaceContainerHighest = t.surfaceRaised,
        inverseSurface = t.text,
        inverseOnSurface = t.background,
        outline = t.textTertiary,
        outlineVariant = t.border,
        error = t.danger,
        onError = if (t.isDark) Color(0xFF2A0E0B) else Color.White,
        errorContainer = if (t.isDark) Color(0xFF3B1714) else Color(0xFFFCE8E6),
        onErrorContainer = t.danger,
        scrim = Color.Black,
    )
}

@Composable
fun GlassMeetTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val tokens = if (darkTheme) DarkTokens else LightTokens
    CompositionLocalProvider(LocalTokens provides tokens) {
        MaterialTheme(
            colorScheme = scheme(tokens),
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
