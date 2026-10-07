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
data object HomeDestination

@Serializable
data object NightsDestination

/** Steuerung; [section] wählt den Reiter (Heizen, Massage oder Abendroutine) beim Öffnen. */
@Serializable
data class ControlDestination(val section: ControlSection = ControlSection.HEAT)

@Keep
@Serializable
enum class ControlSection { HEAT, MASSAGE, ROUTINE }

@Serializable
data object SettingsDestination

@Serializable
data class NightDetailDestination(val nightId: Long)

@Serializable
data object DeveloperDestination

/** Nachtmodus während der Aufzeichnung (ohne untere Leiste). */
@Serializable
data object RecordingDestination

/** Ziele der unteren Navigationsleiste. */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @param:StringRes val label: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
    /** Unterseiten, bei denen dieser Reiter markiert bleibt. */
    val childRoutes: List<KClass<*>> = emptyList(),
) {
    HOME(
        HomeDestination,
        HomeDestination::class,
        R.string.nav_home,
        Icons.Filled.Home,
        Icons.Outlined.Home,
        "nav_home",
        childRoutes = listOf(RecordingDestination::class),
    ),
    NIGHTS(
        NightsDestination,
        NightsDestination::class,
        R.string.nav_nights,
        Icons.Filled.Bedtime,
        Icons.Outlined.Bedtime,
        "nav_nights",
        childRoutes = listOf(NightDetailDestination::class),
    ),
    CONTROL(ControlDestination(), ControlDestination::class, R.string.nav_control, Icons.Filled.Tune, Icons.Outlined.Tune, "nav_control"),
    SETTINGS(
        SettingsDestination,
        SettingsDestination::class,
        R.string.nav_settings,
        Icons.Filled.Settings,
        Icons.Outlined.Settings,
        "nav_settings",
        childRoutes = listOf(DeveloperDestination::class),
    ),
}
