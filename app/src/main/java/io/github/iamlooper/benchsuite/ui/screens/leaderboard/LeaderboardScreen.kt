package io.github.iamlooper.benchsuite.ui.screens.leaderboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.annotation.DrawableRes
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.iamlooper.benchsuite.R
import io.github.iamlooper.benchsuite.data.model.LeaderboardEntry
import io.github.iamlooper.benchsuite.ui.components.AppHeroCard
import io.github.iamlooper.benchsuite.ui.components.AppMetricChip
import io.github.iamlooper.benchsuite.ui.components.AppScaffold
import io.github.iamlooper.benchsuite.ui.components.AppSectionLabel
import io.github.iamlooper.benchsuite.ui.components.AppToolbarActionButton
import io.github.iamlooper.benchsuite.ui.components.ScoreDisplay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LeaderboardScreen(
    onBack: () -> Unit,
    onRunClick: (runId: String) -> Unit,
    viewModel: LeaderboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AppScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.leaderboard_title)) },
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
            LeaderboardUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            }

            LeaderboardUiState.Error -> {
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
                            AppSectionLabel(stringResource(R.string.leaderboard_title))
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.leaderboard_empty_title),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.leaderboard_empty_body),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = viewModel::refresh,
                                shape = MaterialTheme.shapes.extraLarge,
                            ) {
                                Text(stringResource(R.string.leaderboard_retry))
                            }
                        }
                    }
                }
            }

            is LeaderboardUiState.Success -> {
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                ) {
                    LeaderboardFilterBar(
                        myRunsOnly           = state.myRunsOnly,
                        selectedAppVersion   = state.selectedAppVersion,
                        availableAppVersions = state.availableAppVersions,
                        onToggleMyRuns       = viewModel::toggleMyRunsFilter,
                        onSelectAppVersion   = viewModel::setAppVersionFilter,
                        modifier             = Modifier.fillMaxWidth(),
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(key = "hero") {
                            AppHeroCard(
                                modifier = Modifier.fillMaxWidth(),
                                accentColor = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                AppMetricChip(text = stringResource(R.string.leaderboard_published_runs_count, state.entries.size))
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.leaderboard_global_title),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.leaderboard_global_body),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        item(key = "trim_notice") {
                            LeaderboardInfoCard(
                                iconRes = R.drawable.info_24px,
                                message = stringResource(R.string.leaderboard_run_trim_notice),
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }

                        // Show cross-version warning whenever scores from multiple versions may be
                        // present - either when viewing all versions at once (null), or when the
                        // selected version differs from the user's installed version.
                        if (state.selectedAppVersion == null || state.selectedAppVersion != state.userAppVersion) {
                            item(key = "version_warning") {
                                LeaderboardInfoCard(
                                    iconRes = R.drawable.info_24px,
                                    message = stringResource(R.string.leaderboard_version_comparison_warning),
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }

                        if (state.entries.isEmpty()) {
                            item(key = "my_runs_empty") {
                                AppHeroCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    accentColor = MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    AppSectionLabel(stringResource(R.string.leaderboard_section_published_devices))
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = stringResource(R.string.leaderboard_my_runs_empty_title),
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.leaderboard_my_runs_empty_body),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        } else {
                            item(key = "label") {
                                AppSectionLabel(stringResource(R.string.leaderboard_section_published_devices))
                            }

                            itemsIndexed(state.entries, key = { _, entry -> entry.runId }) { index, entry ->
                                LeaderboardEntryCard(
                                    rank = index + 1,
                                    entry = entry,
                                    onClick = { onRunClick(entry.runId) },
                                )
                            }
                        }

                        // Pagination
                        if (state.hasMore) {
                            item(key = "load_more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (state.isLoadingMore) {
                                        LoadingIndicator()
                                    } else {
                                        OutlinedButton(
                                            onClick = viewModel::loadMore,
                                            shape = MaterialTheme.shapes.extraLarge,
                                        ) {
                                            Text(stringResource(R.string.leaderboard_load_more))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardFilterBar(
    myRunsOnly: Boolean,
    selectedAppVersion: String?,
    availableAppVersions: List<String>,
    onToggleMyRuns: () -> Unit,
    onSelectAppVersion: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var versionMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // My runs filter
        FilterChip(
            selected = myRunsOnly,
            onClick = onToggleMyRuns,
            label = { Text(stringResource(R.string.leaderboard_filter_my_runs)) },
            leadingIcon = if (myRunsOnly) {
                {
                    Icon(
                        painter = painterResource(R.drawable.check_24px),
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                }
            } else {
                {
                    Icon(
                        painter = painterResource(R.drawable.person_24px),
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                }
            },
        )

        // App version filter - only render when server has returned at least one version
        if (availableAppVersions.isNotEmpty()) {
            Box {
                FilterChip(
                    selected = selectedAppVersion != null,
                    onClick = { versionMenuExpanded = true },
                    label = {
                        Text(
                            text = selectedAppVersion
                                ?: stringResource(R.string.leaderboard_filter_version_all),
                        )
                    },
                    leadingIcon = if (selectedAppVersion != null) {
                        {
                            Icon(
                                painter = painterResource(R.drawable.check_24px),
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        }
                    } else null,
                    trailingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.unfold_more_24px),
                            contentDescription = stringResource(R.string.leaderboard_filter_version_cd),
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                )
                DropdownMenu(
                    expanded = versionMenuExpanded,
                    onDismissRequest = { versionMenuExpanded = false },
                ) {
                    // "All versions" option clears the filter
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.leaderboard_filter_version_all)) },
                        leadingIcon = if (selectedAppVersion == null) {
                            {
                                Icon(
                                    painter = painterResource(R.drawable.check_24px),
                                    contentDescription = null,
                                )
                            }
                        } else null,
                        onClick = {
                            versionMenuExpanded = false
                            onSelectAppVersion(null)
                        },
                    )
                    availableAppVersions.forEach { version ->
                        DropdownMenuItem(
                            text = { Text(version) },
                            leadingIcon = if (version == selectedAppVersion) {
                                {
                                    Icon(
                                        painter = painterResource(R.drawable.check_24px),
                                        contentDescription = null,
                                    )
                                }
                            } else null,
                            onClick = {
                                versionMenuExpanded = false
                                onSelectAppVersion(version)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardEntryCard(
    rank: Int,
    entry: LeaderboardEntry,
    onClick: () -> Unit,
) {
    val (accentContainer, accentContent) = when (rank) {
        1 -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        2 -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        3 -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.84f) to MaterialTheme.colorScheme.onSurfaceVariant
    }

    val deviceLine = buildString {
        append(entry.brand)
        append(' ')
        append(entry.model)
        if (entry.soc.isNotBlank()) {
            append(" · ")
            append(entry.soc)
        }
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        border = BorderStroke(
            1.dp,
            accentContainer.copy(alpha = 0.24f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AppMetricChip(
                text = stringResource(R.string.leaderboard_rank, rank),
                containerColor = accentContainer,
                contentColor = accentContent,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.displayName.ifBlank { stringResource(R.string.common_anonymous) },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = deviceLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            ScoreDisplay(
                score = entry.overallScore,
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}

@Composable
private fun LeaderboardInfoCard(
    @DrawableRes iconRes: Int,
    message: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
