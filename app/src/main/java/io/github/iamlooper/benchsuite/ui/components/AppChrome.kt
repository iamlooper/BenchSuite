package io.github.iamlooper.benchsuite.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    AppBackdrop(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = topBar,
            floatingActionButton = floatingActionButton,
            snackbarHost = snackbarHost,
            content = content,
        )
    }
}

@Composable
fun AppBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier.drawWithCache {
            val baseBrush = Brush.verticalGradient(
                colors = listOf(
                    colors.surface,
                    colors.background,
                ),
            )
            onDrawBehind {
                drawRect(baseBrush)
            }
        },
        content = content,
    )
}

@Composable
fun AppHeroCard(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primaryContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.24f)),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            content = content,
        )
    }
}

@Composable
fun AppSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(Locale.ROOT),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

@Composable
fun AppMetricChip(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes leadingIconRes: Int? = null,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.82f),
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    onClick: (() -> Unit)? = null,
) {
    val chipContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIconRes != null) {
                Icon(
                    painter = painterResource(leadingIconRes),
                    contentDescription = null,
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = CircleShape,
            color = containerColor,
            contentColor = contentColor,
            border = BorderStroke(1.dp, contentColor.copy(alpha = 0.12f)),
            content = chipContent,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = CircleShape,
            color = containerColor,
            contentColor = contentColor,
            border = BorderStroke(1.dp, contentColor.copy(alpha = 0.12f)),
            content = chipContent,
        )
    }
}

@Composable
fun AppToolbarActionButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
        )
    }
}
