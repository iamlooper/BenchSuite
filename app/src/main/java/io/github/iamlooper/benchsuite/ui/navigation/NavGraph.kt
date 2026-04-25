package io.github.iamlooper.benchsuite.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.iamlooper.benchsuite.ui.screens.home.HomeScreen
import io.github.iamlooper.benchsuite.ui.screens.leaderboard.LeaderboardCategoryDetailScreen
import io.github.iamlooper.benchsuite.ui.screens.leaderboard.LeaderboardRunDetailScreen
import io.github.iamlooper.benchsuite.ui.screens.leaderboard.LeaderboardScreen
import io.github.iamlooper.benchsuite.ui.screens.results.CategoryDetailScreen
import io.github.iamlooper.benchsuite.ui.screens.results.ResultsScreen
import io.github.iamlooper.benchsuite.ui.screens.runner.RunnerScreen
import io.github.iamlooper.benchsuite.ui.screens.about.AboutScreen
import io.github.iamlooper.benchsuite.ui.screens.settings.SettingsScreen

/** Sealed navigation destinations. Keeps route strings in one place and avoids magic strings. */
sealed class NavDestination(val route: String) {
    data object Home        : NavDestination("home")
    data object Runner      : NavDestination("runner")
    data object Leaderboard : NavDestination("leaderboard")
    data object Settings    : NavDestination("settings")
    data object About       : NavDestination("about")

    /** Results for a specific completed run (by local UUID). */
    data object Results : NavDestination("results/{runId}") {
        fun createRoute(runId: String) = "results/$runId"
    }

    /** Category detail within a results view. */
    data object CategoryDetail : NavDestination("results/{runId}/category/{categoryId}") {
        fun createRoute(runId: String, categoryId: String) = "results/$runId/category/$categoryId"
    }

    /** Detail view for a single leaderboard run. */
    data object LeaderboardRunDetail : NavDestination("leaderboard_run_detail/{runId}") {
        fun createRoute(runId: String) = "leaderboard_run_detail/$runId"
    }

    /** Category detail within a published leaderboard run. */
    data object LeaderboardCategoryDetail : NavDestination("leaderboard_run_detail/{runId}/category/{categoryId}") {
        fun createRoute(runId: String, categoryId: String) = "leaderboard_run_detail/$runId/category/$categoryId"
    }
}

/**
 * Root nav host for BenchSuite.
 *
 * Home is the only root destination. Every other screen is a child flow so navigation stays
 * purely drill-down and "Up" always follows the user's path.
 *
 * About is accessible from the Home top bar (info icon) alongside Settings.
 *
 * @param navController  The single-activity nav controller.
 * @param modifier       Applied to the NavHost.
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val navigateBack: () -> Unit = {
        navController.popBackStackSafely()
    }

    NavHost(
        navController    = navController,
        startDestination = NavDestination.Home.route,
        modifier         = modifier,
    ) {
        composable(NavDestination.Home.route) {
            HomeScreen(
                onStartRun = {
                    navController.navigate(NavDestination.Runner.route) {
                        launchSingleTop = true
                    }
                },
                onViewRun = { runId ->
                    navController.navigate(NavDestination.Results.createRoute(runId)) {
                        launchSingleTop = true
                    }
                },
                onLeaderboard = {
                    navController.navigate(NavDestination.Leaderboard.route) {
                        launchSingleTop = true
                    }
                },
                onAbout = {
                    navController.navigate(NavDestination.About.route) {
                        launchSingleTop = true
                    }
                },
                onSettings = {
                    navController.navigate(NavDestination.Settings.route) {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(NavDestination.Leaderboard.route) {
            LeaderboardScreen(
                onBack = navigateBack,
                onRunClick = { runId ->
                    navController.navigate(NavDestination.LeaderboardRunDetail.createRoute(runId)) {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(
            route     = NavDestination.LeaderboardRunDetail.route,
            arguments = listOf(navArgument("runId") { type = NavType.StringType }),
        ) { backStack ->
            val runId = backStack.arguments?.getString("runId") ?: return@composable
            LeaderboardRunDetailScreen(
                runId = runId,
                onCategoryClick = { categoryId ->
                    navController.navigate(NavDestination.LeaderboardCategoryDetail.createRoute(runId, categoryId)) {
                        launchSingleTop = true
                    }
                },
                onBack = navigateBack,
            )
        }

        composable(
            route = NavDestination.LeaderboardCategoryDetail.route,
            arguments = listOf(
                navArgument("runId") { type = NavType.StringType },
                navArgument("categoryId") { type = NavType.StringType },
            ),
        ) { backStack ->
            val categoryId = backStack.arguments?.getString("categoryId") ?: return@composable
            LeaderboardCategoryDetailScreen(
                categoryId = categoryId,
                onBack = navigateBack,
            )
        }

        composable(NavDestination.Settings.route) {
            SettingsScreen(
                onBack = navigateBack,
            )
        }

        composable(NavDestination.About.route) {
            AboutScreen(
                onBack = navigateBack,
            )
        }

        composable(NavDestination.Runner.route) {
            RunnerScreen(
                onRunComplete = { runId ->
                    navController.navigate(NavDestination.Results.createRoute(runId)) {
                        popUpTo(NavDestination.Runner.route) { inclusive = true }
                    }
                },
                onCancel = navigateBack,
            )
        }

        composable(
            route     = NavDestination.Results.route,
            arguments = listOf(navArgument("runId") { type = NavType.StringType }),
        ) { backStack ->
            val runId = backStack.arguments?.getString("runId") ?: return@composable
            ResultsScreen(
                runId = runId,
                onCategoryClick = { categoryId ->
                    navController.navigate(NavDestination.CategoryDetail.createRoute(runId, categoryId)) {
                        launchSingleTop = true
                    }
                },
                onBack = navigateBack,
            )
        }

        composable(
            route     = NavDestination.CategoryDetail.route,
            arguments = listOf(
                navArgument("runId")      { type = NavType.StringType },
                navArgument("categoryId") { type = NavType.StringType },
            ),
        ) { backStack ->
            val runId      = backStack.arguments?.getString("runId")      ?: return@composable
            val categoryId = backStack.arguments?.getString("categoryId") ?: return@composable
            CategoryDetailScreen(
                runId      = runId,
                categoryId = categoryId,
                onBack     = navigateBack,
            )
        }
    }
}

private fun NavHostController.popBackStackSafely(): Boolean {
    val currentEntry = currentBackStackEntry ?: return false

    if (currentEntry.lifecycle.currentState != Lifecycle.State.RESUMED) {
        return false
    }

    if (currentDestination?.id == graph.findStartDestination().id) {
        return false
    }

    return popBackStack()
}
