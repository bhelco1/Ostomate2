package com.ostomate.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.ostomate.app.data.settings.SettingsRepository
import com.ostomate.app.platform.DeepLinkBus
import com.ostomate.app.ui.calendar.CalendarScreen
import com.ostomate.app.ui.history.HistoryScreen
import com.ostomate.app.ui.home.HomeScreen
import com.ostomate.app.ui.onboarding.OnboardingScreen
import com.ostomate.app.ui.settings.ManageSuppliesScreen
import com.ostomate.app.ui.settings.PrivacyPolicyScreen
import com.ostomate.app.ui.settings.QrLabelsScreen
import com.ostomate.app.ui.settings.ReorderWarningsScreen
import com.ostomate.app.ui.settings.SettingsScreen
import com.ostomate.app.ui.stats.StatsScreen
import com.ostomate.app.resources.Res
import com.ostomate.app.resources.deeplink_logged
import com.ostomate.app.resources.deeplink_unrecognized
import com.ostomate.app.resources.nav_calendar
import com.ostomate.app.resources.nav_home
import com.ostomate.app.resources.nav_settings
import com.ostomate.app.resources.nav_stats
import com.ostomate.app.ui.theme.OstomateTheme
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Serializable object HomeDestination

@Serializable object CalendarDestination

@Serializable object StatsDestination

@Serializable object SettingsDestination

@Serializable data class HistoryDestination(val supplyId: Long)

@Serializable object ManageSuppliesDestination

@Serializable object ReorderWarningsDestination

@Serializable object PrivacyPolicyDestination

@Serializable object QrLabelsDestination

@Composable
fun App() {
    OstomateTheme {
        Surface(Modifier.fillMaxSize()) {
            val settingsRepo = koinInject<SettingsRepository>()
            val settings by settingsRepo.settings.collectAsState(initial = null)

            // null = loading; show nothing until we know the onboarding state
            val resolved = settings ?: return@Surface

            if (!resolved.onboardingDone) {
                OnboardingScreen(onDone = { /* settings update triggers recompose */ })
            } else {
                MainApp()
            }
        }
    }
}

@Composable
private fun MainApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }
    val loggedMsg = stringResource(Res.string.deeplink_logged)
    val unrecognizedMsg = stringResource(Res.string.deeplink_unrecognized)

    LaunchedEffect(Unit) {
        DeepLinkBus.events.collect { supplyName ->
            val message =
                if (supplyName != null) {
                    loggedMsg.format(supplyName)
                } else {
                    unrecognizedMsg
                }
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text(stringResource(Res.string.nav_home)) },
                    selected = currentDestination?.hasRoute<HomeDestination>() == true,
                    onClick = {
                        navController.navigate(HomeDestination) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                    label = { Text(stringResource(Res.string.nav_calendar)) },
                    selected = currentDestination?.hasRoute<CalendarDestination>() == true,
                    onClick = {
                        navController.navigate(CalendarDestination) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                    label = { Text(stringResource(Res.string.nav_stats)) },
                    selected = currentDestination?.hasRoute<StatsDestination>() == true,
                    onClick = {
                        navController.navigate(StatsDestination) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text(stringResource(Res.string.nav_settings)) },
                    selected = currentDestination?.hasRoute<SettingsDestination>() == true,
                    onClick = {
                        navController.navigate(SettingsDestination) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeDestination,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            composable<HomeDestination> {
                HomeScreen(
                    onNavigateToHistory = { supplyId ->
                        navController.navigate(HistoryDestination(supplyId))
                    },
                )
            }
            composable<CalendarDestination> { CalendarScreen() }
            composable<StatsDestination> { StatsScreen() }
            composable<SettingsDestination> {
                SettingsScreen(
                    onNavigateToManageSupplies = {
                        navController.navigate(ManageSuppliesDestination)
                    },
                    onNavigateToReorderWarnings = {
                        navController.navigate(ReorderWarningsDestination)
                    },
                    onNavigateToPrivacyPolicy = {
                        navController.navigate(PrivacyPolicyDestination)
                    },
                    onNavigateToQrLabels = {
                        navController.navigate(QrLabelsDestination)
                    },
                )
            }
            composable<ManageSuppliesDestination> {
                ManageSuppliesScreen(onBack = { navController.navigateUp() })
            }
            composable<ReorderWarningsDestination> {
                ReorderWarningsScreen(onBack = { navController.navigateUp() })
            }
            composable<PrivacyPolicyDestination> {
                PrivacyPolicyScreen(onBack = { navController.navigateUp() })
            }
            composable<QrLabelsDestination> {
                QrLabelsScreen(onBack = { navController.navigateUp() })
            }
            composable<HistoryDestination> { backStackEntry ->
                val dest: HistoryDestination = backStackEntry.toRoute()
                HistoryScreen(
                    supplyId = dest.supplyId,
                    onBack = { navController.navigateUp() },
                )
            }
        }
    }
}
