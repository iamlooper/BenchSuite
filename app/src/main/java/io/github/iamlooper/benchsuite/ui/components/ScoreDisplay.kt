package io.github.iamlooper.benchsuite.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import io.github.iamlooper.benchsuite.ui.theme.scoreTierColor
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Animated score number that counts up from 0 to [score] using a spring animation.
 * If [score] is null, displays "N/A" (pending population scoring).
 *
 * Animation: 1200ms duration, spring damping 0.7 (low-bouncy hero moment).
 *
 * @param score       Target score value; null shows pending state.
 * @param modifier    Modifier.
 * @param style       Text style; defaults to displayLarge (57sp, Bold).
 * @param color       Text color; defaults to [scoreTierColor] for [score].
 * @param pendingText Displayed when [score] is null (leaderboard bootstrap phase).
 */
@Composable
fun ScoreDisplay(
    score: Double?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displayLarge,
    color: Color = scoreTierColor(score),
    pendingText: String = "N/A",
) {
    if (score == null) {
        Text(
            text     = pendingText,
            style    = style,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }

    var hasAnimated by rememberSaveable(score) { mutableStateOf(false) }
    val animated = remember(score) { Animatable(if (hasAnimated) score.toFloat() else 0f) }
    LaunchedEffect(score) {
        if (!hasAnimated) {
            animated.animateTo(
                targetValue = score.toFloat(),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness    = Spring.StiffnessLow,
                ),
            )
            hasAnimated = true
        }
    }

    val displayValue = animated.value.roundToInt()
    val formatted    = NumberFormat.getNumberInstance(Locale.getDefault()).format(displayValue)

    Text(
        text     = formatted,
        style    = style,
        color    = color,
        modifier = modifier,
    )
}
