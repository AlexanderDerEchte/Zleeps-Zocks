package at.zocks.zleep.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Fachliche Farben, die Material 3 nicht abdeckt. */
@Immutable
data class ZocksColors(
    val heat: Color,
    val onHeat: Color,
    val heatContainer: Color,
    val sleep: Color,
    val onSleep: Color,
    val sleepContainer: Color,
    val massage: Color,
    val onMassage: Color,
    val massageContainer: Color,
    val stageAwake: Color,
    val stageRem: Color,
    val stageLight: Color,
    val stageDeep: Color,
)

internal val DarkZocksColors = ZocksColors(
    heat = Heat300,
    onHeat = Heat950,
    heatContainer = Heat800,
    sleep = Sleep300,
    onSleep = Sleep950,
    sleepContainer = Sleep800,
    massage = Calm300,
    onMassage = Calm950,
    massageContainer = Calm800,
    stageAwake = StageAwake,
    stageRem = StageRem,
    stageLight = StageLight,
    stageDeep = StageDeep,
)

internal val LightZocksColors = ZocksColors(
    heat = Color(0xFFB8470F),
    onHeat = Color.White,
    heatContainer = Color(0xFFFFDBCB),
    sleep = Color(0xFF2155B8),
    onSleep = Color.White,
    sleepContainer = Color(0xFFD8E3FF),
    massage = Color(0xFF5B48A8),
    onMassage = Color.White,
    massageContainer = Color(0xFFE7DEFF),
    stageAwake = Color(0xFFE0682E),
    stageRem = Color(0xFF7B61D9),
    stageLight = Color(0xFF4C8DFF),
    stageDeep = Color(0xFF1C3A8F),
)

val LocalZocksColors = staticCompositionLocalOf { DarkZocksColors }
