package io.github.iamlooper.benchsuite.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object SettingsKeys {
    val THEME_MODE         = intPreferencesKey("theme_mode")         // 0=follow, 1=light, 2=dark
    val USE_DYNAMIC_THEME  = booleanPreferencesKey("use_dynamic")
    val PURE_BLACK         = booleanPreferencesKey("pure_black")
    val DISPLAY_NAME       = stringPreferencesKey("display_name")
}

data class SettingsSnapshot(
    val themeMode: Int           = 0,
    val useDynamicTheme: Boolean = true,
    val pureBlackTheme: Boolean  = false,
    val displayName: String      = "",
)

fun DataStore<Preferences>.snapshotFlow(): Flow<SettingsSnapshot> = data.map { prefs ->
    SettingsSnapshot(
        themeMode       = prefs[SettingsKeys.THEME_MODE] ?: 0,
        useDynamicTheme = prefs[SettingsKeys.USE_DYNAMIC_THEME] ?: true,
        pureBlackTheme  = prefs[SettingsKeys.PURE_BLACK] ?: false,
        displayName     = prefs[SettingsKeys.DISPLAY_NAME] ?: "",
    )
}

suspend fun DataStore<Preferences>.updateSnapshot(transform: (SettingsSnapshot) -> SettingsSnapshot) {
    edit { prefs ->
        val current = SettingsSnapshot(
            themeMode       = prefs[SettingsKeys.THEME_MODE] ?: 0,
            useDynamicTheme = prefs[SettingsKeys.USE_DYNAMIC_THEME] ?: true,
            pureBlackTheme  = prefs[SettingsKeys.PURE_BLACK] ?: false,
            displayName     = prefs[SettingsKeys.DISPLAY_NAME] ?: "",
        )
        val updated = transform(current)
        prefs[SettingsKeys.THEME_MODE]        = updated.themeMode
        prefs[SettingsKeys.USE_DYNAMIC_THEME] = updated.useDynamicTheme
        prefs[SettingsKeys.PURE_BLACK]        = updated.pureBlackTheme
        prefs[SettingsKeys.DISPLAY_NAME]      = updated.displayName
    }
}
