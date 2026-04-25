package io.github.iamlooper.benchsuite.ui.screens.results

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.iamlooper.benchsuite.R
import io.github.iamlooper.benchsuite.data.model.CategoryScore
import io.github.iamlooper.benchsuite.data.model.DeviceInfo
import io.github.iamlooper.benchsuite.ui.components.AppHeroCard
import io.github.iamlooper.benchsuite.ui.components.AppMetricChip
import io.github.iamlooper.benchsuite.ui.components.AppScaffold
import io.github.iamlooper.benchsuite.ui.components.AppSectionLabel
import io.github.iamlooper.benchsuite.ui.components.AppToolbarActionButton
import io.github.iamlooper.benchsuite.ui.components.ScoreDisplay
import io.github.iamlooper.benchsuite.ui.components.StabilityBadge
import io.github.iamlooper.benchsuite.ui.components.UploadSheet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ResultsScreen(
    runId: String,
    onCategoryClick: (categoryId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: ResultsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uploadState by viewModel.uploadState.collectAsStateWithLifecycle()
    val displayName by viewModel.displayName.collectAsStateWithLifecycle()
    var showUploadSheet by rememberSaveable(runId) { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uploadSuccessMessage = stringResource(R.string.upload_success)

    LaunchedEffect(runId) { viewModel.loadRun(runId) }
    LaunchedEffect(uploadState) {
        if (uploadState is UploadState.Success || uploadState is UploadState.Failure) {
            // Animate sheet closed before showing any snackbar
            sheetState.hide()
            showUploadSheet = false

            when (uploadState) {
                is UploadState.Success -> snackbarHostState.showSnackbar(uploadSuccessMessage)
                is UploadState.Failure -> {
                    val msg = (uploadState as UploadState.Failure).message ?: "Unknown error"
                    snackbarHostState.showSnackbar(msg)
                }
                else -> Unit
            }
        }
    }

    AppScaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.results_title)) },
                navigationIcon = {
                    AppToolbarActionButton(
                        iconRes = R.drawable.arrow_back_24px,
                        contentDescription = stringResource(R.string.cd_back),
                        onClick = onBack,
                    )
                },
                actions = {
                    val canUpload = (uiState as? ResultsUiState.Success)?.run?.isUploaded == false
                    if (canUpload) {
                        AppToolbarActionButton(
                            iconRes = R.drawable.upload_24px,
                            contentDescription = stringResource(R.string.cd_upload_to_leaderboard),
                            onClick = {
                                showUploadSheet = true
                            },
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.92f),
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { innerPadding ->
        when (val state = uiState) {
            ResultsUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            }

            ResultsUiState.Error -> {
                LazyColumn(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "error") {
                        AppHeroCard(
                            modifier = Modifier.fillMaxWidth(),
                            accentColor = MaterialTheme.colorScheme.errorContainer,
                        ) {
                            AppSectionLabel(stringResource(R.string.results_section_error))
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.common_run_not_found_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.results_run_not_found_body),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            is ResultsUiState.Success -> {
                val run = state.run

                LazyColumn(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "hero") {
                        AppHeroCard(
                            modifier = Modifier.fillMaxWidth(),
                            accentColor = if (run.isUploaded) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer
                            },
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppMetricChip(
                                    text = if (run.isUploaded) stringResource(R.string.common_published) else stringResource(R.string.results_local_only),
                                    containerColor = if (run.isUploaded) {
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
                                    } else {
                                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f)
                                    },
                                    contentColor = if (run.isUploaded) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    },
                                )
                                if (run.deviceSoc.isNotBlank()) {
                                    AppMetricChip(text = run.deviceSoc)
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "${run.deviceBrand} ${run.deviceModel}",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(12.dp))
                            ScoreDisplay(
                                score = run.overallScore,
                                style = MaterialTheme.typography.displayMedium,
                            )
                            Spacer(Modifier.height(12.dp))
                            StabilityBadge(rating = run.stabilityRating)
                        }
                    }

                    // Device section
                    item(key = "device_label") {
                        AppSectionLabel(stringResource(R.string.common_section_device))
                    }

                    item(key = "device") {
                        ResultsDetailCard(
                            device = state.device,
                        )
                    }

                    // Metadata section
                    item(key = "metadata_label") {
                        AppSectionLabel(stringResource(R.string.common_section_run_info))
                    }

                    item(key = "metadata") {
                        ResultsMetadataCard(
                            appVersion = run.appVersion,
                            startedAt = run.startedAt,
                            completedAt = run.completedAt,
                        )
                    }

                    item(key = "categories_label") {
                        AppSectionLabel(stringResource(R.string.common_section_category_details))
                    }

                    items(run.categories, key = { it.category.id }) { categoryScore ->
                        CategoryScoreCard(
                            catScore = categoryScore,
                            onClick = { onCategoryClick(categoryScore.category.id) },
                        )
                    }

                }

                if (showUploadSheet) {
                    UploadSheet(
                        onSubmit = { name ->
                            viewModel.uploadRun(name)
                            viewModel.saveDisplayName(name)
                        },
                        onDismiss = { showUploadSheet = false },
                        isUploading = uploadState is UploadState.Uploading,
                        initialDisplayName = displayName,
                        sheetState = sheetState,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryScoreCard(
    catScore: CategoryScore,
    onClick: () -> Unit,
) {
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
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = catScore.category.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                AppMetricChip(
                    text = stringResource(R.string.results_benchmark_count, catScore.benchmarks.size),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.results_view),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ResultsDetailCard(
    device: DeviceInfo,
) {
    val ramText = remember(device.ramBytes) { formatRamBytes(device.ramBytes) }
    val yesText = stringResource(R.string.common_yes)
    val noText = stringResource(R.string.common_no)
    val batteryText = remember(device.batteryLevel) {
        if (device.batteryLevel < 0) "N/A" else "${device.batteryLevel}%"
    }
    val chargingText = remember(device.batteryLevel, device.isCharging) {
        when {
            device.batteryLevel < 0 -> "N/A"
            device.isCharging -> yesText
            else -> noText
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${device.brand} ${device.model}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            ResultsDetailRow(
                label = stringResource(R.string.common_detail_soc),
                value = device.soc.ifBlank { "N/A" },
            )
            ResultsDetailRow(
                label = stringResource(R.string.common_detail_abi),
                value = device.abi.ifBlank { "N/A" },
            )
            ResultsDetailRow(
                label = stringResource(R.string.common_detail_cpu_cores),
                value = device.cpuCores.toString(),
            )
            ResultsDetailRow(
                label = stringResource(R.string.common_detail_ram),
                value = ramText,
            )
            ResultsDetailRow(
                label = stringResource(R.string.common_detail_android_api),
                value = device.androidApi.toString(),
            )
            ResultsDetailRow(
                label = stringResource(R.string.common_detail_battery),
                value = batteryText,
            )
            ResultsDetailRow(
                label = stringResource(R.string.common_detail_charging),
                value = chargingText,
            )
        }
    }
}

@Composable
private fun ResultsMetadataCard(
    appVersion: String,
    startedAt: Long,
    completedAt: Long,
) {
    val startedAtText = remember(startedAt) {
        formatLocalTimestamp(startedAt)
    }
    val completedAtText = remember(completedAt) {
        formatLocalTimestamp(completedAt)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ResultsDetailRow(
                label = stringResource(R.string.common_detail_app_version),
                value = appVersion,
            )
            ResultsDetailRow(
                label = stringResource(R.string.common_detail_started),
                value = startedAtText,
            )
            ResultsDetailRow(
                label = stringResource(R.string.common_detail_completed),
                value = completedAtText,
            )
        }
    }
}

@Composable
private fun ResultsDetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatRamBytes(bytes: Long): String {
    if (bytes <= 0) return "N/A"
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 1.0) {
        String.format(Locale.US, "%.1f GB", gb)
    } else {
        val mb = bytes / (1024.0 * 1024.0)
        String.format(Locale.US, "%.0f MB", mb)
    }
}

private fun formatLocalTimestamp(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return "N/A"
    return SimpleDateFormat("MMM d, yyyy HH:mm:ss", Locale.getDefault()).format(Date(timestampMillis))
}
