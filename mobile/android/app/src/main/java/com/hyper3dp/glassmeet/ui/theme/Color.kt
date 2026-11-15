package com.hyper3dp.glassmeet.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** The design tokens of mobile/SPEC.md, by the same names as Figma and Theme.swift. */
@Immutable
data class Tokens(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val primary: Color,
    val onPrimary: Color,
    val primarySoft: Color,
    val link: Color,
    val border: Color,
    val brandTeal: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val whatsappBubble: Color,
    val isDark: Boolean,
)

val LightTokens = Tokens(
    background = Color(0xFFF6F9F9),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFEEF4F4),
    text = Color(0xFF344248),
    textSecondary = Color(0xFF55686D),
    textTertiary = Color(0xFF66787C),
    primary = Color(0xFF2B7A75),
    onPrimary = Color(0xFFFFFFFF),
    primarySoft = Color(0xFFE3F1F0),
    link = Color(0xFF24706B),
    border = Color(0xFFD6E2E1),
    brandTeal = Color(0xFF66ACA9),
    success = Color(0xFF0A8A4C),
    warning = Color(0xFFB25E00),
    danger = Color(0xFFC8372D),
    whatsappBubble = Color(0xFFD9FDD3),
    isDark = false,
)

val DarkTokens = Tokens(
    background = Color(0xFF0F1516),
    surface = Color(0xFF162022),
    surfaceRaised = Color(0xFF1D2A2C),
    text = Color(0xFFE6EEEE),
    textSecondary = Color(0xFFA0B4B4),
    textTertiary = Color(0xFF7F9495),
    primary = Color(0xFF66ACA9),
    onPrimary = Color(0xFF14201F),
    primarySoft = Color(0xFF1B3332),
    link = Color(0xFF7FC4C0),
    border = Color(0xFF2A3A3C),
    brandTeal = Color(0xFF66ACA9),
    success = Color(0xFF3FB97A),
    warning = Color(0xFFE3A044),
    danger = Color(0xFFFF7B72),
    whatsappBubble = Color(0xFF144D37),
    isDark = true,
)

/** An event type's colour ("#2b7a75"), or null when it has none or it doesn't parse. */
fun parseHexColor(value: String?): Color? {
    val hex = value?.trim()?.removePrefix("#") ?: return null
    return when (hex.length) {
        6 -> hex.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
        3 -> hex.map { "$it$it" }.joinToString("").toLongOrNull(16)?.let { Color(0xFF000000 or it) }
        else -> null
    }
}
