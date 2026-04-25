package io.github.iamlooper.benchsuite.ui.screens.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.iamlooper.benchsuite.R
import io.github.iamlooper.benchsuite.data.model.Category
import io.github.iamlooper.benchsuite.ui.components.AppHeroCard
import io.github.iamlooper.benchsuite.ui.components.AppMetricChip
import io.github.iamlooper.benchsuite.ui.components.AppScaffold
import io.github.iamlooper.benchsuite.ui.components.AppSectionLabel
import io.github.iamlooper.benchsuite.ui.components.AppToolbarActionButton
import io.github.iamlooper.benchsuite.ui.components.BenchmarkCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CategoryDetailScreen(
    runId: String,
    categoryId: String,
    onBack: () -> Unit,
    viewModel: ResultsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val category = Category.fromStringOrNull(categoryId)

    LaunchedEffect(runId, categoryId) {
        if (category != null) {
            viewModel.loadRun(runId)
        }
    }

    AppScaffold(
        topBar = {
            TopAppBar(
                title = { Text(category?.displayName ?: stringResource(R.string.category_detail_fallback_title)) },
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
        if (category == null) {
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
                            text = stringResource(R.string.category_detail_not_found_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.category_detail_not_found_body),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            return@AppScaffold
        }

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
                                text = stringResource(R.string.category_detail_no_data_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.category_detail_no_data_body),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            is ResultsUiState.Success -> {
                val benchmarks = state.run.categories
                    .firstOrNull { it.category == category }
                    ?.benchmarks
                    .orEmpty()

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
                            accentColor = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            AppMetricChip(text = stringResource(R.string.results_benchmark_count, benchmarks.size))
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = category.displayName,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = category.description,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (benchmarks.isEmpty()) {
                        item(key = "empty") {
                            AppHeroCard(
                                modifier = Modifier.fillMaxWidth(),
                                accentColor = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                AppSectionLabel(stringResource(R.string.category_detail_section_empty))
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.category_detail_empty_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.category_detail_empty_body),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        item(key = "direction_legend") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 0.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.arrow_upward_24px),
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = stringResource(R.string.higher_is_better),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.arrow_downward_24px),
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.tertiary,
                                    )
                                    Text(
                                        text = stringResource(R.string.lower_is_better),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        items(benchmarks, key = { it.id }) { result ->
                            BenchmarkCard(
                                result = result,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}
