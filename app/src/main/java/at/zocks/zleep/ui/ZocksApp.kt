package at.zocks.zleep.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import at.zocks.zleep.R
import at.zocks.zleep.ui.control.ControlScreen
import at.zocks.zleep.ui.home.HomeScreen
import at.zocks.zleep.ui.navigation.ControlRoute
import at.zocks.zleep.ui.navigation.ControlSection
import at.zocks.zleep.ui.navigation.HomeRoute
import at.zocks.zleep.ui.navigation.NightsRoute
import at.zocks.zleep.ui.navigation.SettingsRoute
import at.zocks.zleep.ui.navigation.TopLevelDestination
import at.zocks.zleep.ui.nights.NightsScreen
import at.zocks.zleep.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

@Composable
fun ZocksApp(navController: NavHostController = rememberNavController()) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pairFirstMessage = stringResource(R.string.snackbar_pair_first)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { ZocksBottomBar(navController) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    onOpenHeat = { navController.navigateToTopLevel(ControlRoute(ControlSection.HEAT), restoreState = false) },
                    onOpenMassage = { navController.navigateToTopLevel(ControlRoute(ControlSection.MASSAGE), restoreState = false) },
                    onStartNight = { scope.launch { snackbarHostState.showSnackbar(pairFirstMessage) } },
                    onPairSocks = { scope.launch { snackbarHostState.showSnackbar(pairFirstMessage) } },
                )
            }
            composable<NightsRoute> { NightsScreen() }
            composable<ControlRoute> { entry ->
                ControlScreen(
                    initialSection = entry.toRoute<ControlRoute>().section,
                    onPairSocks = { scope.launch { snackbarHostState.showSnackbar(pairFirstMessage) } },
                )
            }
            composable<SettingsRoute> { SettingsScreen() }
        }
    }
}

@Composable
private fun ZocksBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any { it.hasRoute(destination.routeClass) } == true
            NavigationBarItem(
                selected = selected,
                onClick = { navController.navigateToTopLevel(destination.route) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(destination.label)) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.testTag(destination.testTag),
            )
        }
    }
}

/**
 * Wechselt zu einem Ziel der unteren Leiste. [restoreState] = false, wenn ein Schnellzugriff
 * einen bestimmten Reiter öffnen soll und kein gespeicherter Zustand ihn überdecken darf.
 */
private fun NavHostController.navigateToTopLevel(route: Any, restoreState: Boolean = true) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        this.restoreState = restoreState
    }
}
