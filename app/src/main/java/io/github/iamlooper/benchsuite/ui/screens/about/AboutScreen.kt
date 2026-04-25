package io.github.iamlooper.benchsuite.ui.screens.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.iamlooper.benchsuite.BuildConfig
import io.github.iamlooper.benchsuite.R
import io.github.iamlooper.benchsuite.ui.components.AppHeroCard
import io.github.iamlooper.benchsuite.ui.components.AppMetricChip
import io.github.iamlooper.benchsuite.ui.components.AppScaffold
import io.github.iamlooper.benchsuite.ui.components.AppSectionLabel
import io.github.iamlooper.benchsuite.ui.components.AppToolbarActionButton

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
) {
    AppScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
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
        val uriHandler = LocalUriHandler.current

        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hero") {
                AppHeroCard(accentColor = MaterialTheme.colorScheme.tertiaryContainer) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppMetricChip(text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME))
                        AppMetricChip(
                            text = stringResource(R.string.about_source_code),
                            onClick = { uriHandler.openUri("https://github.com/iamlooper/BenchSuite") },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.about_hero_headline),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.about_hero_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "what") {
                AppHeroCard(accentColor = MaterialTheme.colorScheme.primaryContainer) {
                    AppSectionLabel(stringResource(R.string.about_section_measures))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.about_measures_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "privacy") {
                AppHeroCard(accentColor = MaterialTheme.colorScheme.secondaryContainer) {
                    AppSectionLabel(stringResource(R.string.about_section_privacy))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.about_privacy_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "stack") {
                AppHeroCard(accentColor = MaterialTheme.colorScheme.surfaceVariant) {
                    AppSectionLabel(stringResource(R.string.about_section_built_with))
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppMetricChip(text = stringResource(R.string.about_chip_kotlin))
                        AppMetricChip(text = stringResource(R.string.about_chip_compose))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppMetricChip(text = stringResource(R.string.about_chip_rust))
                        AppMetricChip(text = stringResource(R.string.about_chip_supabase))
                    }
                }
            }
        }
    }
}
