package at.zocks.zleep.ui.navigation

import androidx.annotation.Keep
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import at.zocks.zleep.R
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable
data object HomeRoute

@Serializable
data object NightsRoute

/** Steuerung; [section] wählt den Reiter (Heizen oder Massage) beim Öffnen. */
@Serializable
data class ControlRoute(val section: ControlSection = ControlSection.HEAT)

@Keep
@Serializable
enum class ControlSection { HEAT, MASSAGE }

@Serializable
data object SettingsRoute

/** Ziele der unteren Navigationsleiste. */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @param:StringRes val label: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
) {
    HOME(HomeRoute, HomeRoute::class, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
    NIGHTS(NightsRoute, NightsRoute::class, R.string.nav_nights, Icons.Filled.Bedtime, Icons.Outlined.Bedtime, "nav_nights"),
    CONTROL(ControlRoute(), ControlRoute::class, R.string.nav_control, Icons.Filled.Tune, Icons.Outlined.Tune, "nav_control"),
    SETTINGS(
        SettingsRoute,
        SettingsRoute::class,
        R.string.nav_settings,
        Icons.Filled.Settings,
        Icons.Outlined.Settings,
        "nav_settings",
    ),
}
