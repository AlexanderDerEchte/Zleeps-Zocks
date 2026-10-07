package at.zocks.zleep.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.zocks.zleep.domain.model.SleepStage
import at.zocks.zleep.ui.components.stageColor

/** Reihenfolge der Phasen in Balken und Legenden: vom tiefsten Schlaf bis wach. */
val StageOrder = listOf(SleepStage.DEEP, SleepStage.LIGHT, SleepStage.REM, SleepStage.AWAKE)

/**
 * Gestapelter Balken der Schlafphasen. Zwischen den Abschnitten bleibt eine 2-dp-Lücke,
 * die Enden sind abgerundet. Ohne [contentDescription] ist der Balken rein dekorativ
 * (die Legende daneben trägt die Werte).
 */
@Composable
fun StageBar(
    stageMinutes: Map<SleepStage, Int>,
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
    contentDescription: String? = null,
) {
    val colors = StageOrder.associateWith { stageColor(it) }
    val total = stageMinutes.values.sum()
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .clearAndSetSemantics { if (contentDescription != null) this.contentDescription = contentDescription },
    ) {
        if (total <= 0) return@Canvas
        val gap = 2.dp.toPx()
        val radius = CornerRadius(4.dp.toPx())
        val visible = StageOrder.filter { (stageMinutes[it] ?: 0) > 0 }
        val usable = size.width - gap * (visible.size - 1)
        val outline = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, radius))
        }
        clipPath(outline) {
            var x = 0f
            visible.forEach { stage ->
                val width = usable * (stageMinutes[stage] ?: 0) / total
                drawRect(colors.getValue(stage), topLeft = Offset(x, 0f), size = Size(width, size.height))
                x += width + gap
            }
        }
    }
}
