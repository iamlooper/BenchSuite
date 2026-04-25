package io.github.iamlooper.benchsuite.ui.screens.settings

import androidx.compose.runtime.Immutable

enum class ThemeMode { FOLLOW_SYSTEM, LIGHT, DARK }

@Immutable
data class SettingsState(
    val themeMode: ThemeMode        = ThemeMode.FOLLOW_SYSTEM,
    val useDynamicTheme: Boolean    = true,
    val pureBlackTheme: Boolean     = false,
    val displayName: String         = "",
)
