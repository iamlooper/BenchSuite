package io.github.iamlooper.benchsuite.ui.screens.leaderboard

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import io.github.iamlooper.benchsuite.data.model.LeaderboardEntry
import io.github.iamlooper.benchsuite.data.model.StabilityRating
import io.github.iamlooper.benchsuite.ui.components.AppHeroCard
import io.github.iamlooper.benchsuite.ui.components.AppMetricChip
import io.github.iamlooper.benchsuite.ui.components.AppScaffold
import io.github.iamlooper.benchsuite.ui.components.AppSectionLabel
import io.github.iamlooper.benchsuite.ui.components.AppToolbarActionButton
import io.github.iamlooper.benchsuite.ui.components.ScoreDisplay
import io.github.iamlooper.benchsuite.ui.components.StabilityBadge
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LeaderboardRunDetailScreen(
    runId: String,
    onCategoryClick: (categoryId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: LeaderboardRunDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AppScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.leaderboard_run_detail_title)) },
                navigationIcon = {
                    AppToolbarActionButton(
                        iconRes = R.drawable.arrow_back_24px,
                        contentDescription = stringResource(R.string.cd_back),
                        onClick = onBack,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { innerPadding ->
        when (val state = uiState) {
            LeaderboardRunDetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            }

            LeaderboardRunDetailUiState.Error -> {
                LazyColumn(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "not_found") {
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
                                text = stringResource(R.string.leaderboard_run_detail_not_found_body),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            is LeaderboardRunDetailUiState.Success -> {
                LeaderboardRunDetailContent(
                    entry = state.entry,
                    onCategoryClick = onCategoryClick,
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun LeaderboardRunDetailContent(
    entry: LeaderboardEntry,
    onCategoryClick: (categoryId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ramText = remember(entry.ramBytes) { formatRamBytes(entry.ramBytes) }
    val startedAtText = remember(entry.startedAt) { formatIsoTimestamp(entry.startedAt) }
    val completedAtText = remember(entry.completedAt) { formatIsoTimestamp(entry.completedAt) }
    val yesText = stringResource(R.string.common_yes)
    val noText = stringResource(R.string.common_no)
    val batteryText = remember(entry.batteryLevel) {
        if (entry.batteryLevel < 0) "N/A" else "${entry.batteryLevel}%"
    }
    val chargingText = remember(entry.batteryLevel, entry.isCharging) {
        when {
            entry.batteryLevel < 0 -> "N/A"
            entry.isCharging -> yesText
            else -> noText
        }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {

        // Score hero
        item(key = "hero") {
            AppHeroCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                AppMetricChip(
                    text = stringResource(R.string.leaderboard_rank, entry.rank),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = entry.displayName.ifBlank { stringResource(R.string.common_anonymous) },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                ScoreDisplay(
                    score = entry.overallScore,
                    style = MaterialTheme.typography.displayMedium,
                )
                entry.stabilityRating?.let { rating ->
                    Spacer(Modifier.height(12.dp))
                    StabilityBadge(rating = StabilityRating.fromString(rating))
                }
            }
        }

        // Device section
        item(key = "device_label") {
            AppSectionLabel(stringResource(R.string.common_section_device))
        }

        item(key = "device") {
            DetailCard {
                DetailRow(
                    label = "${entry.brand} ${entry.model}",
                    value = null,
                    isTitle = true,
                )
                Spacer(Modifier.height(12.dp))
                DetailRow(
                    label = stringResource(R.string.common_detail_soc),
                    value = entry.soc.ifBlank { "N/A" },
                )
                DetailRow(
                    label = stringResource(R.string.common_detail_abi),
                    value = entry.abi.ifBlank { "N/A" },
                )
                DetailRow(
                    label = stringResource(R.string.common_detail_cpu_cores),
                    value = entry.cpuCores.toString(),
                )
                DetailRow(
                    label = stringResource(R.string.common_detail_ram),
                    value = ramText,
                )
                DetailRow(
                    label = stringResource(R.string.common_detail_android_api),
                    value = entry.androidApi.toString(),
                )
                DetailRow(
                    label = stringResource(R.string.common_detail_battery),
                    value = batteryText,
                )
                DetailRow(
                    label = stringResource(R.string.common_detail_charging),
                    value = chargingText,
                )
            }
        }

        // Metadata section
        item(key = "metadata_label") {
            AppSectionLabel(stringResource(R.string.common_section_run_info))
        }

        item(key = "metadata") {
            DetailCard {
                DetailRow(
                    label = stringResource(R.string.common_detail_app_version),
                    value = entry.appVersion,
                )
                DetailRow(
                    label = stringResource(R.string.common_detail_started),
                    value = startedAtText ?: "N/A",
                )
                DetailRow(
                    label = stringResource(R.string.common_detail_completed),
                    value = completedAtText ?: "N/A",
                )
            }
        }

        if (entry.categories.isNotEmpty()) {
            item(key = "categories_label") {
                AppSectionLabel(stringResource(R.string.common_section_category_details))
            }

            items(entry.categories, key = { it.category.id }) { categoryScore ->
                PublishedCategoryCard(
                    categoryScore = categoryScore,
                    onClick = { onCategoryClick(categoryScore.category.id) },
                )
            }
        }
    }
}

@Composable
private fun PublishedCategoryCard(
    categoryScore: CategoryScore,
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
                    text = categoryScore.category.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                AppMetricChip(
                    text = stringResource(R.string.results_benchmark_count, categoryScore.benchmarks.size),
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
private fun DetailCard(
    content: @Composable () -> Unit,
) {
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
            content()
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String?,
    isTitle: Boolean = false,
) {
    if (isTitle) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }

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
            text = value ?: "N/A",
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

private fun formatIsoTimestamp(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return try {
        // Instant.parse() only accepts Z-suffixed strings; Supabase returns offset-aware
        // timestamps (e.g. 2026-04-25T14:28:17.436+05:00), so parse via OffsetDateTime first.
        val instant = OffsetDateTime.parse(iso).toInstant()
        val date = Date.from(instant)
        SimpleDateFormat("MMM d, yyyy HH:mm:ss", Locale.getDefault()).format(date)
    } catch (_: Exception) {
        iso
    }
}
