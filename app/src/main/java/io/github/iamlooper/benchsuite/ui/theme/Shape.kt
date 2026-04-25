package io.github.iamlooper.benchsuite.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * MD3 Expressive shape scale favours stronger silhouette contrast and roomier curves.
 *
 * Cards and hero containers use larger radii so the shell feels more sculpted than stock MD3.
 */
val BenchSuiteShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(20.dp),
    large      = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)
