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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.zocks.zleep.ui.components.HeatingBanner
import at.zocks.zleep.ui.components.LocalSnackbarHostState
import at.zocks.zleep.ui.components.UserMessageEffect
import at.zocks.zleep.ui.control.ControlRoute
import at.zocks.zleep.ui.developer.DeveloperRoute
import at.zocks.zleep.ui.home.HomeRoute
import at.zocks.zleep.ui.nightdetail.NightDetailRoute
import at.zocks.zleep.ui.navigation.ControlDestination
import at.zocks.zleep.ui.navigation.ControlSection
import at.zocks.zleep.ui.navigation.DeveloperDestination
import at.zocks.zleep.ui.navigation.HomeDestination
import at.zocks.zleep.ui.navigation.NightDetailDestination
import at.zocks.zleep.ui.navigation.NightsDestination
import at.zocks.zleep.ui.navigation.SettingsDestination
import at.zocks.zleep.ui.navigation.TopLevelDestination
import at.zocks.zleep.ui.nights.NightsRoute
import at.zocks.zleep.ui.settings.SettingsScreen

@Composable
fun ZocksApp(
    navController: NavHostController = rememberNavController(),
    appViewModel: AppViewModel = hiltViewModel(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val appState by appViewModel.uiState.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
    UserMessageEffect(appState.notice, appViewModel::noticeShown)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { HeatingBanner(appState.heatingRemaining, appViewModel::stopHeating) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { ZocksBottomBar(navController) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeDestination,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            composable<HomeDestination> {
                HomeRoute(
                    onOpenHeat = { navController.navigateToTopLevel(ControlDestination(ControlSection.HEAT), restoreState = false) },
                    onOpenMassage = { navController.navigateToTopLevel(ControlDestination(ControlSection.MASSAGE), restoreState = false) },
                    onOpenNight = { id -> navController.navigate(NightDetailDestination(id)) },
                )
            }
            composable<NightsDestination> {
                NightsRoute(onOpenNight = { id -> navController.navigate(NightDetailDestination(id)) })
            }
            composable<NightDetailDestination> {
                NightDetailRoute(onBack = navController::popBackStack)
            }
            composable<ControlDestination> { entry ->
                ControlRoute(initialSection = entry.toRoute<ControlDestination>().section)
            }
            composable<SettingsDestination> {
                SettingsScreen(onOpenDeveloperOptions = { navController.navigate(DeveloperDestination) })
            }
            composable<DeveloperDestination> {
                DeveloperRoute(onBack = navController::popBackStack)
            }
        }
    }
    }
}

@Composable
private fun ZocksBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any { node ->
                node.hasRoute(destination.routeClass) || destination.childRoutes.any { node.hasRoute(it) }
            } == true
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
