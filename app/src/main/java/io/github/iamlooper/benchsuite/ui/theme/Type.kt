package io.github.iamlooper.benchsuite.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Full MD3 type scale.
 *
 * Overall score number uses displayLarge (57sp, Bold), numbers must feel heroic.
 * Category scores use headlineLarge (32sp, SemiBold).
 * Metric values use titleSmall (14sp, Medium, Monospace) for numeric alignment.
 */
val BenchSuiteTypography = Typography(
    // Hero moment, overall score display
    displayLarge = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 57.sp,
        lineHeight   = 64.sp,
        letterSpacing = (-0.6).sp,
    ),
    displayMedium = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Bold,
        fontSize     = 45.sp,
        lineHeight   = 52.sp,
    ),
    displaySmall = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 36.sp,
        lineHeight   = 44.sp,
    ),
    // Category score, prominent but secondary to overall
    headlineLarge = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Bold,
        fontSize     = 32.sp,
        lineHeight   = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 28.sp,
        lineHeight   = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Medium,
        fontSize     = 24.sp,
        lineHeight   = 32.sp,
    ),
    // Benchmark names, section headers
    titleLarge = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 22.sp,
        lineHeight   = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 18.sp,
        lineHeight   = 24.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily   = FontFamily.Monospace,  // monospace for metric values
        fontWeight   = FontWeight.Medium,
        fontSize     = 14.sp,
        lineHeight   = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    // Body text
    bodyLarge = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Normal,
        fontSize     = 16.sp,
        lineHeight   = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Normal,
        fontSize     = 14.sp,
        lineHeight   = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Normal,
        fontSize     = 12.sp,
        lineHeight   = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    // Labels, status bar, chips
    labelLarge = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 14.sp,
        lineHeight   = 20.sp,
        letterSpacing = 0.sp,
    ),
    labelMedium = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Medium,
        fontSize     = 12.sp,
        lineHeight   = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily   = FontFamily.Default,
        fontWeight   = FontWeight.Medium,
        fontSize     = 11.sp,
        lineHeight   = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)
