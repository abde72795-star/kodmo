/*
 * Adapted from Kotori (https://github.com/tailolicon/kotori), Apache-2.0.
 */
package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Kotori-inspired dark violet surfaces with pink primary and cyan/teal
 * secondary accents. The same palette is used regardless of system light mode.
 */
internal object KotoriColorScheme : BaseColorScheme() {
    private val scheme = darkColorScheme(
        primary = Color(0xFFEC4899),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF4A1B34),
        onPrimaryContainer = Color(0xFFF9A8D4),
        inversePrimary = Color(0xFFF472B6),
        secondary = Color(0xFF38BDF8),
        onSecondary = Color(0xFF07131C),
        secondaryContainer = Color(0xFF17364A),
        onSecondaryContainer = Color(0xFFBAE6FD),
        tertiary = Color(0xFF5EEAD4),
        onTertiary = Color(0xFF0B1512),
        tertiaryContainer = Color(0xFF14534E),
        onTertiaryContainer = Color(0xFF99F6E4),
        background = Color(0xFF14101F),
        onBackground = Color(0xFFF3EFFB),
        surface = Color(0xFF14101F),
        onSurface = Color(0xFFF3EFFB),
        surfaceVariant = Color(0xFF201C2E),
        onSurfaceVariant = Color(0xFFA79FC0),
        surfaceTint = Color(0xFFEC4899),
        inverseSurface = Color(0xFF2E2843),
        inverseOnSurface = Color(0xFFF3EFFB),
        outline = Color(0xFF6E6590),
        outlineVariant = Color(0xFF35304A),
        error = Color(0xFFFB7185),
        onError = Color(0xFF1B0A10),
        errorContainer = Color(0xFF551D2A),
        onErrorContainer = Color(0xFFFECDD3),
        surfaceContainerLowest = Color(0xFF161222),
        surfaceContainerLow = Color(0xFF181326),
        surfaceContainer = Color(0xFF1B1629),
        surfaceContainerHigh = Color(0xFF221D36),
        surfaceContainerHighest = Color(0xFF27213C),
    )

    override val darkScheme = scheme
    override val lightScheme = scheme
}
