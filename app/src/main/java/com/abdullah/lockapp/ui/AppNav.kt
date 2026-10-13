package com.abdullah.lockapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.abdullah.lockapp.AppContainer
import com.abdullah.lockapp.ui.apps.AppDetailScreen
import com.abdullah.lockapp.ui.apps.AppsScreen
import com.abdullah.lockapp.ui.home.HomeScreen
import com.abdullah.lockapp.ui.onboarding.OnboardingScreen
import com.abdullah.lockapp.ui.settings.AboutScreen
import com.abdullah.lockapp.ui.settings.SettingsScreen
import com.abdullah.lockapp.ui.stats.StatsScreen
import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable object AppsRoute
@Serializable object StatsRoute
@Serializable object SettingsRoute
@Serializable object AboutRoute
@Serializable data class AppDetailRoute(val packageName: String)

private data class Tab(val route: Any, val label: String, val icon: ImageVector)

@Composable
fun LockAppRoot(c: AppContainer) {
    val armed by c.settings.armed.collectAsStateWithLifecycle(initialValue = null)
    when (armed) {
        null -> Box(Modifier.fillMaxSize())
        false -> OnboardingScreen(c)
        true -> MainScaffold(c)
    }
}

@Composable
private fun MainScaffold(c: AppContainer) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val tabs = remember {
        listOf(
            Tab(HomeRoute, "Home", AppIcons.Home),
            Tab(AppsRoute, "Apps", AppIcons.Apps),
            Tab(StatsRoute, "Stats", AppIcons.Stats),
            Tab(SettingsRoute, "Settings", AppIcons.Settings),
        )
    }
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination

    fun openTab(route: Any) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { openTab(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = HomeRoute, modifier = Modifier.padding(padding)) {
            composable<HomeRoute> {
                HomeScreen(
                    c = c,
                    snackbar = snackbar,
                    onOpenApp = { nav.navigate(AppDetailRoute(it)) },
                    onOpenApps = { openTab(AppsRoute) },
                )
            }
            composable<AppsRoute> { AppsScreen(c, onOpenApp = { nav.navigate(AppDetailRoute(it)) }) }
            composable<AppDetailRoute> { backStackEntry ->
                AppDetailScreen(
                    c = c,
                    packageName = backStackEntry.toRoute<AppDetailRoute>().packageName,
                    snackbar = snackbar,
                    onBack = { nav.popBackStack() },
                )
            }
            composable<StatsRoute> { StatsScreen(c) }
            composable<SettingsRoute> { SettingsScreen(c, snackbar, onOpenAbout = { nav.navigate(AboutRoute) }) }
            composable<AboutRoute> { AboutScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
