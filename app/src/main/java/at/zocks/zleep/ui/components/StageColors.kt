package at.zocks.zleep.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import at.zocks.zleep.domain.model.SleepStage
import at.zocks.zleep.ui.theme.ZocksThemeExt

@Composable
@ReadOnlyComposable
fun stageColor(stage: SleepStage): Color = when (stage) {
    SleepStage.AWAKE -> ZocksThemeExt.colors.stageAwake
    SleepStage.LIGHT -> ZocksThemeExt.colors.stageLight
    SleepStage.DEEP -> ZocksThemeExt.colors.stageDeep
    SleepStage.REM -> ZocksThemeExt.colors.stageRem
}
