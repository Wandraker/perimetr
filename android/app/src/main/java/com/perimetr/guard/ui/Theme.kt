package com.perimetr.guard.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

object P {
    val Bg = Color(0xFF0B0F17)
    val Surface = Color(0xFF131A26)
    val Surface2 = Color(0xFF1B2535)
    val Line = Color(0xFF26324A)
    val Fg = Color(0xFFE8EEF7)
    val Muted = Color(0xFF8A99B3)
    val Faint = Color(0xFF5C6B84)
    val Accent = Color(0xFF2FE6C4)
    val AccentDim = Color(0xFF16413C)
    val Ok = Color(0xFF3DDC84)
    val Warn = Color(0xFFFFB84D)
    val Crit = Color(0xFFFF5E6C)
    val CritDim = Color(0xFF3A1620)
}

val Mono = FontFamily.Monospace

@Composable
fun PerimetrTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = P.Accent,
            onPrimary = P.Bg,
            primaryContainer = P.AccentDim,
            onPrimaryContainer = P.Accent,
            background = P.Bg,
            onBackground = P.Fg,
            surface = P.Surface,
            onSurface = P.Fg,
            surfaceVariant = P.Surface2,
            onSurfaceVariant = P.Muted,
            outline = P.Line,
            error = P.Crit,
        ),
        content = content,
    )
}
