package io.github.iamlooper.benchsuite.ui.screens.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.iamlooper.benchsuite.R
import io.github.iamlooper.benchsuite.data.local.LocalRunEntity
import io.github.iamlooper.benchsuite.data.model.StabilityRating
import io.github.iamlooper.benchsuite.engine.EngineState
import io.github.iamlooper.benchsuite.ui.components.AppHeroCard
import io.github.iamlooper.benchsuite.ui.components.AppMetricChip
import io.github.iamlooper.benchsuite.ui.components.AppScaffold
import io.github.iamlooper.benchsuite.ui.components.AppToolbarActionButton
import io.github.iamlooper.benchsuite.ui.components.AppSectionLabel
import io.github.iamlooper.benchsuite.ui.components.ScoreDisplay
import io.github.iamlooper.benchsuite.ui.components.StabilityBadge
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    onStartRun: () -> Unit,
    onViewRun: (runId: String) -> Unit,
    onLeaderboard: () -> Unit,
    onAbout: () -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val runs by viewModel.runs.collectAsStateWithLifecycle()
    val engineState by viewModel.engineState.collectAsStateWithLifecycle()

    val fabEnabled = engineState == EngineState.IDLE
    val fabScale by animateFloatAsState(
        targetValue = if (fabEnabled) 1f else 0.9f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "home_fab_scale",
    )

    // Confirmation dialog state
    var showClearAllDialog by rememberSaveable { mutableStateOf(false) }
    var runIdToDelete by rememberSaveable { mutableStateOf<String?>(null) }

    AppScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    AppToolbarActionButton(
                        iconRes = R.drawable.settings_24px,
                        contentDescription = stringResource(R.string.cd_settings),
                        onClick = onSettings,
                    )
                    AppToolbarActionButton(
                        iconRes = R.drawable.info_24px,
                        contentDescription = stringResource(R.string.cd_about),
                        onClick = onAbout,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (fabEnabled) onStartRun() },
                modifier = Modifier.scale(fabScale),
                shape = MaterialTheme.shapes.extraLarge,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.play_arrow_24px),
                        contentDescription = null,
                    )
                },
                text = {
                    Text(
                        text = when (engineState) {
                            EngineState.UNINITIALIZED -> stringResource(R.string.home_fab_preparing_engine)
                            EngineState.RUNNING -> stringResource(R.string.home_fab_benchmark_running)
                            else -> stringResource(R.string.home_fab_run_benchmark)
                        },
                    )
                },
            )
        },
    ) { innerPadding ->
        val latestRun = runs.firstOrNull()

        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = 112.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hero") {
                AppHeroCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    AppMetricChip(text = engineStateLabel(engineState))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.home_hero_headline),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.home_hero_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppMetricChip(text = stringResource(R.string.home_local_runs_count, runs.size))
                        latestRun?.overallScore?.let { score ->
                            AppMetricChip(
                                text = stringResource(R.string.home_score_chip, NumberFormat.getNumberInstance(Locale.getDefault()).format(score.roundToInt())),
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.86f),
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
            }

            item(key = "explore_label") {
                AppSectionLabel(stringResource(R.string.home_section_explore))
            }

            item(key = "destinations") {
                HomeDestinationCard(
                    label = stringResource(R.string.home_leaderboard_label),
                    title = stringResource(R.string.home_leaderboard_title),
                    body = stringResource(R.string.home_leaderboard_body),
                    iconRes = R.drawable.list_24px,
                    accentColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = onLeaderboard,
                )
            }

            if (runs.isEmpty()) {
                item(key = "empty") {
                    AppHeroCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        AppSectionLabel(stringResource(R.string.home_section_start_here))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.home_empty_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (fabEnabled) {
                                stringResource(R.string.home_empty_body_ready)
                            } else {
                                stringResource(R.string.home_empty_body_preparing)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                item(key = "history_label") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppSectionLabel(stringResource(R.string.home_section_recent_runs))
                        TextButton(onClick = { showClearAllDialog = true }) {
                            Text(
                                text = stringResource(R.string.home_clear_all_runs),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                items(runs, key = { it.id }) { run ->
                    RunHistoryCard(
                        run = run,
                        onClick = { onViewRun(run.id) },
                        onDelete = { runIdToDelete = run.id },
                    )
                }
            }
        }
    }

    // Delete single run confirmation
    if (runIdToDelete != null) {
        AlertDialog(
            onDismissRequest = { runIdToDelete = null },
            title = { Text(stringResource(R.string.home_delete_run_confirm_title)) },
            text = { Text(stringResource(R.string.home_delete_run_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    runIdToDelete?.let { viewModel.deleteRun(it) }
                    runIdToDelete = null
                }) {
                    Text(
                        text = stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { runIdToDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    // Clear all confirmation
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text(stringResource(R.string.home_clear_all_confirm_title)) },
            text = { Text(stringResource(R.string.home_clear_all_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllRuns()
                    showClearAllDialog = false
                }) {
                    Text(
                        text = stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun HomeDestinationCard(
    label: String,
    title: String,
    body: String,
    iconRes: Int,
    accentColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = accentColor.copy(alpha = 0.86f),
        ),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = contentColor.copy(alpha = 0.1f),
                contentColor = contentColor,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label.uppercase(Locale.ROOT),
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = contentColor,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor.copy(alpha = 0.84f),
                )
            }
        }
    }
}

@Composable
private fun RunHistoryCard(
    run: LocalRunEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val dateText = remember(run.completedAt) {
        SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(run.completedAt))
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${run.deviceBrand} ${run.deviceModel}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                ScoreDisplay(
                    score = run.overallScore,
                    style = MaterialTheme.typography.headlineLarge,
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    run.stabilityRating?.let {
                        StabilityBadge(
                            rating = StabilityRating.fromString(it),
                        )
                    }
                    if (run.isUploaded) {
                        AppMetricChip(
                            text = stringResource(R.string.common_published),
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        painter = painterResource(R.drawable.close_24px),
                        contentDescription = stringResource(R.string.cd_delete_run),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun engineStateLabel(engineState: EngineState): String = when (engineState) {
    EngineState.UNINITIALIZED -> stringResource(R.string.home_engine_preparing)
    EngineState.IDLE -> stringResource(R.string.home_engine_ready)
    EngineState.RUNNING -> stringResource(R.string.home_engine_running)
    EngineState.COMPLETED -> stringResource(R.string.home_engine_complete)
    EngineState.CANCELLED -> stringResource(R.string.home_engine_cancelled)
    EngineState.ERROR -> stringResource(R.string.home_engine_error)
}
