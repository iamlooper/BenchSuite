package io.github.iamlooper.benchsuite.ui.theme

import androidx.compose.ui.graphics.Color

// Fallback material tokens
// Dynamic color still wins on Android 12+, but these values define the app theme elsewhere.

val Midnight        = Color(0xFF191114)
val MidnightSurface = Color(0xFF191114)
val MidnightRaised  = Color(0xFF514347)

val GlacierBlue          = Color(0xFFFFB0CD)
val GlacierBlueOn        = Color(0xFF531D36)
val GlacierBlueContainer = Color(0xFF6E334D)

val MintGreen          = Color(0xFFE1BDC8)
val MintGreenOn        = Color(0xFF412932)
val MintGreenContainer = Color(0xFF5A3F49)

val WarmAmber          = Color(0xFFF0BC96)
val WarmAmberOn        = Color(0xFF49290E)
val WarmAmberContainer = Color(0xFF633E22)

val DaybreakBlue          = Color(0xFF8A4A64)
val DaybreakBlueOn        = Color(0xFFFFFFFF)
val DaybreakBlueContainer = Color(0xFFFFD9E4)

val SageGreen          = Color(0xFF735760)
val SageGreenOn        = Color(0xFFFFFFFF)
val SageGreenContainer = Color(0xFFFFD9E4)

val SunlitAmber          = Color(0xFF7E5637)
val SunlitAmberOn        = Color(0xFFFFFFFF)
val SunlitAmberContainer = Color(0xFFFFDCC4)

val LightBackground      = Color(0xFFFFF8F8)
val LightSurface         = Color(0xFFFFF8F8)
val LightSurfaceVariant  = Color(0xFFF2DEE2)
val LightOnSurface       = Color(0xFF21191C)
val LightOnSurfaceVariant = Color(0xFF514347)
val LightOutline         = Color(0xFF837377)
val LightOutlineVariant  = Color(0xFFD5C2C7)

val DarkOnSurface        = Color(0xFFEEDFE2)
val DarkOnSurfaceVariant = Color(0xFFD5C2C7)
val DarkOutline          = Color(0xFF9D8C91)
val DarkOutlineVariant   = Color(0xFF514347)

val CoralError   = Color(0xFFFFB4AB)
val SuccessGreen = Color(0xFF70E2A9)

// Score tier colors
/** Score < 500 (well below median) */
val ScoreTierRed     = Color(0xFFFF847C)
/** Score 500–999 (below median) */
val ScoreTierAmber   = Color(0xFFFFC15E)
/** Score 1000–1499 (above median) */
val ScoreTierGreen   = Color(0xFF6BE3A9)
/** Score ≥ 1500 (exceptional) */
val ScoreTierBlue    = Color(0xFF7FDBFF)

/** Returns the appropriate score tier color for [score]. */
fun scoreTierColor(score: Double?): Color = when {
    score == null      -> ScoreTierBlue   // indeterminate/bootstrap, show blue
    score < 500.0      -> ScoreTierRed
    score < 1000.0     -> ScoreTierAmber
    score < 1500.0     -> ScoreTierGreen
    else               -> ScoreTierBlue
}
