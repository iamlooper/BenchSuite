package io.github.iamlooper.benchsuite.ui.screens.runner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.iamlooper.benchsuite.R
import io.github.iamlooper.benchsuite.engine.EngineState
import io.github.iamlooper.benchsuite.ui.components.AppHeroCard
import io.github.iamlooper.benchsuite.ui.components.AppMetricChip
import io.github.iamlooper.benchsuite.ui.components.AppScaffold
import io.github.iamlooper.benchsuite.ui.components.AppSectionLabel
import io.github.iamlooper.benchsuite.ui.components.AppToolbarActionButton
import io.github.iamlooper.benchsuite.ui.components.SparklineChart

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RunnerScreen(
    onRunComplete: (runId: String) -> Unit,
    onCancel: () -> Unit,
    viewModel: RunnerViewModel = hiltViewModel(),
) {
    val engineState by viewModel.engineState.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val sparkline by viewModel.sparkline.collectAsStateWithLifecycle()
    var pendingCancellation by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.startRun { runId -> onRunComplete(runId) }
    }
    LaunchedEffect(engineState, pendingCancellation) {
        // CANCELLED is set synchronously by requestCancel; IDLE follows once resetToIdle
        // runs in the startRun finally block. Checking both avoids a Compose frame-boundary
        // window where the screen could briefly display stale cleared progress.
        if (pendingCancellation &&
            (engineState == EngineState.CANCELLED || engineState == EngineState.IDLE)
        ) {
            pendingCancellation = false
            onCancel()
        }
    }

    val overallProgress by animateFloatAsState(
        targetValue = progress.overallFraction,
        animationSpec = tween(durationMillis = 320, easing = LinearEasing),
        label = "overall_progress",
    )
    val benchProgress by animateFloatAsState(
        targetValue = progress.benchmarkFraction,
        animationSpec = tween(durationMillis = 220, easing = LinearEasing),
        label = "bench_progress",
    )

    AppScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.runner_title)) },
                navigationIcon = {
                    AppToolbarActionButton(
                        iconRes = R.drawable.close_24px,
                        contentDescription = stringResource(R.string.cd_cancel_run),
                        onClick = {
                            if (engineState == EngineState.RUNNING) {
                                pendingCancellation = true
                                viewModel.cancelRun()
                            } else {
                                onCancel()
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.24f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppHeroCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                AppMetricChip(text = engineStateLabel(engineState))
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.runner_hero_headline),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.runner_hero_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppMetricChip(text = stringResource(R.string.runner_percent_total, (overallProgress * 100).toInt()))
                    if (progress.totalBenchmarks > 0) {
                        // completedBenchmarks is 0-indexed during PROGRESS records but is
                        // set to totalBenchmarks on the final COMPLETE record, so cap the
                        // display to avoid showing e.g. "36/35" on the last tick.
                        AppMetricChip(text = stringResource(R.string.runner_benchmark_active, minOf(progress.completedBenchmarks + 1, progress.totalBenchmarks), progress.totalBenchmarks))
                    }
                }
            }

            AppHeroCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                AppSectionLabel(stringResource(R.string.runner_section_suite_progress))
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.runner_overall_progress),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                LinearWavyProgressIndicator(
                    progress = { overallProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.runner_current_benchmark),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                LinearWavyProgressIndicator(
                    progress = { benchProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            AppHeroCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                AppSectionLabel(stringResource(R.string.runner_section_live_signal))
                Spacer(Modifier.height(12.dp))
                SparklineChart(
                    samples = sparkline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(192.dp),
                )
            }

            AnimatedVisibility(
                visible = engineState == EngineState.CANCELLED || engineState == EngineState.ERROR,
                enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                        expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                        shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            ) {
                FilledTonalButton(
                    onClick = onCancel,
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Text(stringResource(R.string.runner_back_to_home))
                }
            }
        }
    }
}

@Composable
private fun engineStateLabel(engineState: EngineState): String = when (engineState) {
    EngineState.UNINITIALIZED -> stringResource(R.string.runner_engine_preparing)
    EngineState.IDLE -> stringResource(R.string.runner_engine_ready)
    EngineState.RUNNING -> stringResource(R.string.runner_engine_executing)
    EngineState.COMPLETED -> stringResource(R.string.runner_engine_complete)
    EngineState.CANCELLED -> stringResource(R.string.runner_engine_cancelled)
    EngineState.ERROR -> stringResource(R.string.runner_engine_error)
}
