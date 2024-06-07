package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colors for Default theme
 * M3 colors generated by Material Theme Builder (https://goo.gle/material-theme-builder-web)
 *
 * Key colors:
 * Primary #2979FF
 * Secondary #2979FF
 * Tertiary #47A84A
 * Neutral #919094
 */
internal object TachiyomiColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFB0C6FF),
        onPrimary = Color(0xFF002D6E),
        primaryContainer = Color(0xFF00429B),
        onPrimaryContainer = Color(0xFFD9E2FF),
        inversePrimary = Color(0xFF0058CA),
        secondary = Color(0xFFB0C6FF), // Unread badge
        onSecondary = Color(0xFF002D6E), // Unread badge text
        secondaryContainer = Color(0xFF00429B), // Navigation bar selector pill & pro
        onSecondaryContainer = Color(0xFFD9E2FF), // Navigation bar selector icon
        tertiary = Color(0xFF7ADC77), // Downloaded badge
        onTertiary = Color(0xFF003909), // Downloaded badge text
        tertiaryContainer = Color(0xFF005312),
        onTertiaryContainer = Color(0xFF95F990),
        background = Color(0xFF1B1B1F),
        onBackground = Color(0xFFE3E2E6),
        surface = Color(0xFF1B1B1F),
        onSurface = Color(0xFFE3E2E6),
        surfaceVariant = Color(0xFF211F26), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFFC5C6D0),
        surfaceTint = Color(0xFFB0C6FF),
        inverseSurface = Color(0xFFE3E2E6),
        inverseOnSurface = Color(0xFF1B1B1F),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF8F9099),
        outlineVariant = Color(0xFF44464F),
        surfaceContainerLowest = Color(0xFF1A181D),
        surfaceContainerLow = Color(0xFF1E1C22),
        surfaceContainer = Color(0xFF211F26), // Navigation bar background
        surfaceContainerHigh = Color(0xFF292730),
        surfaceContainerHighest = Color(0xFF302E38),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF0058CA),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD9E2FF),
        onPrimaryContainer = Color(0xFF001945),
        inversePrimary = Color(0xFFB0C6FF),
        secondary = Color(0xFF0058CA), // Unread badge
        onSecondary = Color(0xFFFFFFFF), // Unread badge text
        secondaryContainer = Color(0xFFD9E2FF), // Navigation bar selector pill & progress indicator (remaining)
        onSecondaryContainer = Color(0xFF001945), // Navigation bar selector icon
        tertiary = Color(0xFF006E1B), // Downloaded badge
        onTertiary = Color(0xFFFFFFFF), // Downloaded badge text
        tertiaryContainer = Color(0xFF95F990),
        onTertiaryContainer = Color(0xFF002203),
        background = Color(0xFFFEFBFF),
        onBackground = Color(0xFF1B1B1F),
        surface = Color(0xFFFEFBFF),
        onSurface = Color(0xFF1B1B1F),
        surfaceVariant = Color(0xFFF3EDF7), // Navigation bar background (ThemePrefWidget)
        onSurfaceVariant = Color(0xFF44464F),
        surfaceTint = Color(0xFF0058CA),
        inverseSurface = Color(0xFF303034),
        inverseOnSurface = Color(0xFFF2F0F4),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = Color(0xFF757780),
        outlineVariant = Color(0xFFC5C6D0),
        surfaceContainerLowest = Color(0xFFF5F1F8),
        surfaceContainerLow = Color(0xFFF7F2FA),
        surfaceContainer = Color(0xFFF3EDF7), // Navigation bar background
        surfaceContainerHigh = Color(0xFFFCF7FF),
        surfaceContainerHighest = Color(0xFFFCF7FF),
    )
}