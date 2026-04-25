package io.github.iamlooper.benchsuite.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.iamlooper.benchsuite.R
import io.github.iamlooper.benchsuite.ui.components.AppScaffold
import io.github.iamlooper.benchsuite.ui.components.AppSectionLabel
import io.github.iamlooper.benchsuite.ui.components.AppToolbarActionButton

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    var showNameDialog  by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            showThemeDialog = false
            showNameDialog = false
        }
    }

    val isDarkActive = state.themeMode == ThemeMode.DARK ||
            (state.themeMode == ThemeMode.FOLLOW_SYSTEM && isSystemInDarkTheme())
    val isDynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    AppScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "leaderboard_label") {
                AppSectionLabel(stringResource(R.string.settings_section_leaderboard))
            }
            item(key = "display_name") {
                val anonymousFallback = stringResource(R.string.common_anonymous)
                SettingClickableCard(
                    iconRes = R.drawable.account_box_24px,
                    title = stringResource(R.string.settings_display_name_title),
                    subtitle = state.displayName.ifBlank { anonymousFallback },
                    onClick = { showNameDialog = true },
                )
            }

            item(key = "appearance_label") {
                AppSectionLabel(stringResource(R.string.settings_section_appearance))
            }
            item(key = "theme") {
                SettingClickableCard(
                    iconRes = R.drawable.palette_24px,
                    title = stringResource(R.string.settings_theme_title),
                    subtitle = themeModeDisplayName(state.themeMode),
                    onClick = { showThemeDialog = true },
                )
            }
            item(key = "dynamic_color") {
                SettingSwitchCard(
                    iconRes = R.drawable.colors_24px,
                    title = stringResource(R.string.settings_dynamic_colors_title),
                    subtitle = if (isDynamicSupported) stringResource(R.string.settings_dynamic_colors_subtitle)
                               else stringResource(R.string.settings_dynamic_colors_unsupported),
                    checked = state.useDynamicTheme,
                    onCheckedChange = viewModel::setUseDynamicTheme,
                    enabled = isDynamicSupported,
                )
            }
            item(key = "pure_black") {
                SettingSwitchCard(
                    iconRes = R.drawable.contrast_24px,
                    title = stringResource(R.string.settings_pure_black_title),
                    subtitle = stringResource(R.string.settings_pure_black_subtitle),
                    checked = state.pureBlackTheme,
                    onCheckedChange = viewModel::setPureBlackTheme,
                    enabled = isDarkActive,
                )
            }

        }
    }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentMode = state.themeMode,
            onDismiss   = { showThemeDialog = false },
            onSelect    = { mode ->
                viewModel.setThemeMode(mode)
                showThemeDialog = false
            },
        )
    }

    if (showNameDialog) {
        DisplayNameDialog(
            currentName = state.displayName,
            onDismiss   = { showNameDialog = false },
            onSave      = { name ->
                viewModel.setDisplayName(name)
                showNameDialog = false
            },
        )
    }
}

// Setting card composables

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingSwitchCard(
    iconRes: Int,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surfaceContainerLow
                             else MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                thumbContent = {
                    if (checked) {
                        Icon(
                            painter = painterResource(R.drawable.check_24px),
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.close_24px),
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun SettingClickableCard(
    iconRes: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 8.dp,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                painter = painterResource(R.drawable.chevron_right_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// Dialog composables

@Composable
private fun ThemeSelectionDialog(
    currentMode: ThemeMode,
    onDismiss: () -> Unit,
    onSelect: (ThemeMode) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.settings_theme_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    Card(
                        onClick = { onSelect(mode) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = if (mode == currentMode)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 8.dp),
                        border = BorderStroke(
                            1.dp,
                            if (mode == currentMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = themeModeDisplayName(mode),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (mode == currentMode) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (mode == currentMode)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurface,
                            )
                            if (mode == currentMode) {
                                RadioButton(selected = true, onClick = null)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun DisplayNameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.settings_display_name_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_display_name_dialog_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 32) name = it },
                    placeholder = { Text(stringResource(R.string.common_anonymous)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave(name) }),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    supportingText = { Text(stringResource(R.string.settings_name_char_count, name.length)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

// Display name helpers

@Composable
private fun themeModeDisplayName(mode: ThemeMode): String = when (mode) {
    ThemeMode.FOLLOW_SYSTEM -> stringResource(R.string.settings_theme_follow_system)
    ThemeMode.LIGHT         -> stringResource(R.string.settings_theme_light)
    ThemeMode.DARK          -> stringResource(R.string.settings_theme_dark)
}
