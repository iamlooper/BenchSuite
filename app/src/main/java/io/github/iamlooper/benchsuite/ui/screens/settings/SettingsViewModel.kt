package io.github.iamlooper.benchsuite.ui.screens.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.iamlooper.benchsuite.data.local.snapshotFlow
import io.github.iamlooper.benchsuite.data.local.updateSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    val state: StateFlow<SettingsState> = dataStore.snapshotFlow()
        .map { snap ->
            SettingsState(
                themeMode = when (snap.themeMode) {
                    1    -> ThemeMode.LIGHT
                    2    -> ThemeMode.DARK
                    else -> ThemeMode.FOLLOW_SYSTEM
                },
                useDynamicTheme = snap.useDynamicTheme,
                pureBlackTheme  = snap.pureBlackTheme,
                displayName     = snap.displayName,
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsState())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            dataStore.updateSnapshot { it.copy(themeMode = when (mode) {
                ThemeMode.FOLLOW_SYSTEM -> 0
                ThemeMode.LIGHT         -> 1
                ThemeMode.DARK          -> 2
            }) }
        }
    }

    fun setUseDynamicTheme(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.updateSnapshot { it.copy(useDynamicTheme = enabled) }
        }
    }

    fun setPureBlackTheme(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.updateSnapshot { it.copy(pureBlackTheme = enabled) }
        }
    }

    fun setDisplayName(name: String) {
        viewModelScope.launch {
            dataStore.updateSnapshot { it.copy(displayName = name.trim()) }
        }
    }
}
