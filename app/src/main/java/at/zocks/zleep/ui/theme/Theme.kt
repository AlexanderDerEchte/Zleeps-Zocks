package at.zocks.zleep.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

private val DarkScheme = darkColorScheme(
    primary = Sleep300,
    onPrimary = Sleep950,
    primaryContainer = Sleep800,
    onPrimaryContainer = Mist100,
    // Wärme-Orange bewusst nicht als "secondary": Material nutzt secondary für viele neutrale
    // Elemente. Wärme-Farben kommen nur über ZocksThemeExt.colors.heat zum Einsatz.
    secondary = Slate200,
    onSecondary = Slate950,
    secondaryContainer = Slate700,
    onSecondaryContainer = Mist100,
    tertiary = Calm300,
    onTertiary = Calm950,
    tertiaryContainer = Calm800,
    onTertiaryContainer = Mist100,
    error = Error300,
    onError = Error950,
    errorContainer = Error800,
    onErrorContainer = Mist100,
    background = Night900,
    onBackground = Mist100,
    surface = Night900,
    onSurface = Mist100,
    surfaceVariant = Night700,
    onSurfaceVariant = Mist300,
    surfaceDim = Night950,
    surfaceBright = Night650,
    surfaceContainerLowest = Night950,
    surfaceContainerLow = Night850,
    surfaceContainer = Night800,
    surfaceContainerHigh = Night750,
    surfaceContainerHighest = Night700,
    outline = Night500,
    outlineVariant = Night650,
    inverseSurface = Mist100,
    inverseOnSurface = Night900,
    inversePrimary = Sleep500,
)

private val LightScheme = lightColorScheme(
    primary = LightZocksColors.sleep,
    onPrimary = LightZocksColors.onSleep,
    primaryContainer = LightZocksColors.sleepContainer,
    secondary = Ink600,
    onSecondary = Day50,
    secondaryContainer = Day200,
    tertiary = LightZocksColors.massage,
    onTertiary = LightZocksColors.onMassage,
    tertiaryContainer = LightZocksColors.massageContainer,
    background = Day50,
    onBackground = Ink900,
    surface = Day50,
    onSurface = Ink900,
    onSurfaceVariant = Ink600,
    surfaceContainerLow = Day100,
    surfaceContainer = Day100,
    surfaceContainerHigh = Day200,
)

private val ZocksShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(Dimens.CardCorner),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * App-Theme. Dunkel ist Standard, weil die App abends und nachts genutzt wird;
 * dynamische Systemfarben sind bewusst aus, damit Wärme/Schlaf-Akzente eindeutig bleiben.
 */
@Composable
fun ZocksTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalZocksColors provides if (darkTheme) DarkZocksColors else LightZocksColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = ZocksTypography,
            shapes = ZocksShapes,
            content = content,
        )
    }
}

/** Zugriff auf die fachlichen Farben: `ZocksThemeExt.colors.heat`. */
object ZocksThemeExt {
    val colors: ZocksColors
        @Composable
        @ReadOnlyComposable
        get() = LocalZocksColors.current
}
