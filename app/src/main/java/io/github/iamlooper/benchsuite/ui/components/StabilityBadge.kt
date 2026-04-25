package io.github.iamlooper.benchsuite.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.iamlooper.benchsuite.data.model.StabilityRating

/**
 * Pill badge showing the run stability rating.
 *
 * @param rating   [StabilityRating] enum value, or null to show nothing.
 * @param modifier Modifier.
 */
@Composable
fun StabilityBadge(
    rating: StabilityRating?,
    modifier: Modifier = Modifier,
) {
    if (rating == null) return
    val (containerColor, contentColor) = when (rating) {
        StabilityRating.EXCELLENT -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        StabilityRating.GOOD      -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        StabilityRating.FAIR      -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        StabilityRating.UNSTABLE  -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        color    = containerColor,
        shape    = CircleShape,
        border   = BorderStroke(1.dp, contentColor.copy(alpha = 0.18f)),
        modifier = modifier,
    ) {
        Text(
            text     = rating.label,
            style    = MaterialTheme.typography.labelLarge,
            color    = contentColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
