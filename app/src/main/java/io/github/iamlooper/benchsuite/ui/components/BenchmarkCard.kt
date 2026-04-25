package io.github.iamlooper.benchsuite.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.iamlooper.benchsuite.R
import io.github.iamlooper.benchsuite.data.model.BenchmarkResult
import io.github.iamlooper.benchsuite.ui.theme.scoreTierColor
import java.text.DecimalFormat

/**
 * Card displaying a single benchmark result with animated score bar.
 *
 * @param result   Benchmark result to display.
 * @param onClick  Optional click handler for navigation to detail.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BenchmarkCard(
    result: BenchmarkResult,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scoreProgress by animateFloatAsState(
        targetValue = ((result.score ?: 0.0) / 1500.0).coerceIn(0.0, 1.0).toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "score_progress_${result.id}",
    )

    val cardContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text     = result.displayName,
                        style    = MaterialTheme.typography.titleLarge,
                        color    = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    val fmt = DecimalFormat("#,##0.##")

                    // Metric value with direction indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AppMetricChip(
                            text = if (result.metricP50 <= 0.0) {
                                stringResource(R.string.benchmark_metric_not_available)
                            } else {
                                stringResource(R.string.benchmark_metric_p50_format, fmt.format(result.metricP50), result.unit.label)
                            },
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val higherIsBetter = result.unit.higherIsBetter
                        Icon(
                            painter = painterResource(
                                if (higherIsBetter) R.drawable.arrow_upward_24px else R.drawable.arrow_downward_24px,
                            ),
                            contentDescription = stringResource(
                                if (higherIsBetter) R.string.higher_is_better else R.string.lower_is_better,
                            ),
                            modifier = Modifier.size(16.dp),
                            tint = if (higherIsBetter) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            },
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                result.score?.let { score ->
                    ScoreDisplay(
                        score = score,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }

            if (result.score != null) {
                Spacer(Modifier.height(12.dp))
                LinearWavyProgressIndicator(
                    progress     = { scoreProgress },
                    modifier     = Modifier.fillMaxWidth().height(8.dp),
                    color        = scoreTierColor(result.score),
                    trackColor   = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                )
            }
        }
    }

    if (onClick != null) {
        Card(
            onClick   = onClick,
            modifier  = modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 8.dp),
            colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)),
            shape     = MaterialTheme.shapes.large,
            border    = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)),
            content   = { cardContent() },
        )
    } else {
        Card(
            modifier  = modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)),
            shape     = MaterialTheme.shapes.large,
            border    = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)),
            content   = { cardContent() },
        )
    }
}
