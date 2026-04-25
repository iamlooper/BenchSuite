package io.github.iamlooper.benchsuite.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Fallback color schemes when dynamic color is unavailable

private val DarkColorScheme = darkColorScheme(
    primary            = GlacierBlue,
    onPrimary          = GlacierBlueOn,
    primaryContainer   = GlacierBlueContainer,
    secondary          = MintGreen,
    onSecondary        = MintGreenOn,
    secondaryContainer = MintGreenContainer,
    tertiary           = WarmAmber,
    onTertiary         = WarmAmberOn,
    tertiaryContainer  = WarmAmberContainer,
    background         = Midnight,
    surface            = MidnightSurface,
    surfaceVariant     = MidnightRaised,
    onBackground       = DarkOnSurface,
    onSurface          = DarkOnSurface,
    onSurfaceVariant   = DarkOnSurfaceVariant,
    outline            = DarkOutline,
    outlineVariant     = DarkOutlineVariant,
    error              = CoralError,
)

private val LightColorScheme = lightColorScheme(
    primary            = DaybreakBlue,
    onPrimary          = DaybreakBlueOn,
    primaryContainer   = DaybreakBlueContainer,
    secondary          = SageGreen,
    onSecondary        = SageGreenOn,
    secondaryContainer = SageGreenContainer,
    tertiary           = SunlitAmber,
    onTertiary         = SunlitAmberOn,
    tertiaryContainer  = SunlitAmberContainer,
    background         = LightBackground,
    surface            = LightSurface,
    surfaceVariant     = LightSurfaceVariant,
    onBackground       = LightOnSurface,
    onSurface          = LightOnSurface,
    onSurfaceVariant   = LightOnSurfaceVariant,
    outline            = LightOutline,
    outlineVariant     = LightOutlineVariant,
)

/**
 * BenchSuite Material Design 3 Expressive theme.
 *
 * Strategy:
 *   1. Use dynamic color on Android 12+ when enabled.
 *   2. Use the app's fallback color tokens everywhere else.
 *   3. Optionally force pure black surfaces in dark mode.
 */
@Composable
fun BenchSuiteTheme(
    darkTheme: Boolean    = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    pureBlack: Boolean    = false,
    content: @Composable () -> Unit,
) {
    var colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    if (pureBlack && darkTheme) {
        colorScheme = colorScheme.copy(
            background = Color.Black,
            surface    = Color.Black,
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = BenchSuiteTypography,
        shapes      = BenchSuiteShapes,
        content     = content,
    )
}
