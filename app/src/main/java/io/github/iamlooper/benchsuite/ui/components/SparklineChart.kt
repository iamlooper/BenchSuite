package io.github.iamlooper.benchsuite.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.iamlooper.benchsuite.R

/**
 * MD3 Expressive rolling sparkline chart showing live benchmark metric values.
 *
 * Plots up to 200 most-recent samples left-to-right, auto-scaling Y to the
 * visible range. When [samples] has fewer than 2 values, shows an animated
 * "Measuring…" placeholder state.
 *
 * The chart uses a thicker expressive line stroke with a rich gradient fill,
 * subtle guide lines using outlineVariant, and an animated pulsing indicator
 * dot on the latest value, all consistent with the app's MD3 Expressive
 * visual language.
 *
 * @param samples   Up to 200 most-recent metric values.
 * @param modifier  Modifier.
 * @param lineColor Stroke color (defaults to primary).
 * @param fillColor Area fill below the line (defaults to primary at 0.18 alpha).
 */
@Composable
fun SparklineChart(
    samples: List<Double>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
) {
    val colors = MaterialTheme.colorScheme
    val containerShape = MaterialTheme.shapes.medium
    val guideColor = colors.outlineVariant.copy(alpha = 0.10f)
    val dotFillColor = colors.primaryContainer
    val dotBorderColor = lineColor
    val density = LocalDensity.current

    // Pulsing ring animation for the latest-value dot
    val pulseTransition = rememberInfiniteTransition(label = "sparkline_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_scale",
    )
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.38f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )

    // Shimmer for empty state
    val shimmerTransition = rememberInfiniteTransition(label = "sparkline_shimmer")
    val shimmerAlpha by shimmerTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmer_alpha",
    )

    Box(
        modifier = modifier
            .clip(containerShape)
            .background(colors.surfaceContainerLowest.copy(alpha = 0.5f))
            .border(
                width = 1.dp,
                color = colors.primaryContainer.copy(alpha = 0.22f),
                shape = containerShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Non-finite values passed as sparkline samples cause LinearGradient.nativeCreate to
        // throw IllegalArgumentException. Filter defensively so yPos() coordinates are always valid.
        val finiteSamples = samples.filter { it.isFinite() }

        if (finiteSamples.size < 2) {
            // Empty state: animated "Measuring…" placeholder
            Text(
                text = stringResource(R.string.sparkline_measuring),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant.copy(alpha = shimmerAlpha),
            )
        } else {
            val lineStrokeWidthPx = with(density) { 2.5.dp.toPx() }
            val dotRadiusPx = with(density) { 5.dp.toPx() }
            val dotBorderWidthPx = with(density) { 2.dp.toPx() }
            val pulseBaseRadiusPx = with(density) { 8.dp.toPx() }

            Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                val w = size.width
                val h = size.height
                val insetH = h * 0.06f // vertical inset for breathing room

                // Subtle horizontal guide lines (2 lines at 1/3 and 2/3)
                for (index in 1..2) {
                    val y = h * index / 3f
                    drawLine(
                        color = guideColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = with(density) { 0.5.dp.toPx() },
                    )
                }

                val minVal = finiteSamples.min()
                val maxVal = finiteSamples.max()
                val range = (maxVal - minVal).coerceAtLeast(1.0)
                val n = finiteSamples.size

                fun xPos(i: Int) = i.toFloat() / (n - 1) * w
                fun yPos(v: Double) =
                    insetH + (h - 2 * insetH) * (1f - ((v - minVal) / range).toFloat())

                // Gradient fill area
                val fillPath = Path().apply {
                    moveTo(xPos(0), h)
                    for (i in finiteSamples.indices) lineTo(xPos(i), yPos(finiteSamples[i]))
                    lineTo(xPos(n - 1), h)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(fillColor, fillColor.copy(alpha = 0.04f), Color.Transparent),
                        startY = finiteSamples.minOf { yPos(it) },
                        endY = h,
                    ),
                )

                // Main line with expressive stroke
                val linePath = Path().apply {
                    moveTo(xPos(0), yPos(finiteSamples[0]))
                    for (i in 1 until n) lineTo(xPos(i), yPos(finiteSamples[i]))
                }
                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(width = lineStrokeWidthPx, cap = StrokeCap.Round),
                )

                // Current value dot with pulsing ring
                val lastPoint = Offset(xPos(n - 1), yPos(finiteSamples.last()))

                // Pulsing outer ring
                drawCircle(
                    color = dotBorderColor.copy(alpha = pulseAlpha),
                    radius = pulseBaseRadiusPx * pulseScale,
                    center = lastPoint,
                    style = Stroke(width = with(density) { 1.5.dp.toPx() }),
                )

                // Dot fill (primaryContainer)
                drawCircle(
                    color = dotFillColor,
                    radius = dotRadiusPx,
                    center = lastPoint,
                )

                // Dot border (primary)
                drawCircle(
                    color = dotBorderColor,
                    radius = dotRadiusPx,
                    center = lastPoint,
                    style = Stroke(width = dotBorderWidthPx),
                )
            }
        }
    }
}
