package io.github.iamlooper.benchsuite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import io.github.iamlooper.benchsuite.ui.navigation.NavGraph
import io.github.iamlooper.benchsuite.ui.screens.settings.SettingsViewModel
import io.github.iamlooper.benchsuite.ui.screens.settings.ThemeMode
import io.github.iamlooper.benchsuite.ui.theme.BenchSuiteTheme

/**
 * Single-activity entry point for BenchSuite.
 *
 * Hosts a single drill-down navigation flow rooted at Home.
 * Observes [SettingsViewModel] to apply the user's theme preferences.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings by settingsViewModel.state.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()

            val isDark = when (settings.themeMode) {
                ThemeMode.DARK          -> true
                ThemeMode.LIGHT         -> false
                ThemeMode.FOLLOW_SYSTEM -> systemDark
            }

            BenchSuiteTheme(
                darkTheme    = isDark,
                dynamicColor = settings.useDynamicTheme,
                pureBlack    = settings.pureBlackTheme,
            ) {
                val navController = rememberNavController()
                NavGraph(
                    navController = navController,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
