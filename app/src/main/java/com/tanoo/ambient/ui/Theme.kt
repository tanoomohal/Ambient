package com.tanoo.ambient.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.tanoo.ambient.R

/** Inter stands in for SF Pro, which Apple only licenses for its own platforms. */
val Inter = FontFamily(
    Font(R.font.inter_thin, FontWeight.Thin),
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

/** Colours follow Apple's system palette (label, secondaryLabel, systemGroupedBackground…). */
data class Palette(
    val isDark: Boolean,
    val bgTop: Color,
    val bgBottom: Color,
    val card: Color,
    val cardBorder: Color,
    val fill: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val accent: Color,
    val focusBorder: Color,
    val focusGlow: Color,
    val clockSecond: Color,
)

val DarkPalette = Palette(
    isDark = true,
    bgTop = Color(0xFF0B0C10),
    bgBottom = Color(0xFF15171E),
    card = Color(0xFF1C1D23),
    cardBorder = Color(0x14FFFFFF),
    fill = Color(0xFF2C2C32),
    text = Color(0xFFF5F5F7),
    text2 = Color(0xFFA1A1A6),
    text3 = Color(0xFF6E6E73),
    accent = Color(0xFF0A84FF),
    focusBorder = Color(0xCCFFFFFF),
    focusGlow = Color(0x66000000),
    clockSecond = Color(0xFFFF9F0A),
)

val LightPalette = Palette(
    isDark = false,
    bgTop = Color(0xFFF5F5F7),
    bgBottom = Color(0xFFE6E6EB),
    card = Color(0xFFFFFFFF),
    cardBorder = Color(0x0F000000),
    fill = Color(0xFFE3E3E8),
    text = Color(0xFF1D1D1F),
    text2 = Color(0xFF6E6E73),
    text3 = Color(0xFF86868B),
    accent = Color(0xFF0071E3),
    focusBorder = Color(0xFF1D1D1F),
    focusGlow = Color(0x33000000),
    clockSecond = Color(0xFFFF9500),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

val P: Palette
    @Composable @ReadOnlyComposable get() = LocalPalette.current

@Composable
fun AmbientTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides if (dark) DarkPalette else LightPalette, content = content)
}
