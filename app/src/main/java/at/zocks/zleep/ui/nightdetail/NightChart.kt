package at.zocks.zleep.ui.nightdetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import at.zocks.zleep.domain.analysis.NightTimeline
import at.zocks.zleep.domain.analysis.TimelinePoint
import at.zocks.zleep.domain.analysis.TimelineSpan
import at.zocks.zleep.domain.analysis.valueAt
import at.zocks.zleep.domain.model.SleepStage
import at.zocks.zleep.ui.components.stageColor
import at.zocks.zleep.ui.format.currentLocale
import at.zocks.zleep.ui.format.stageLabel
import at.zocks.zleep.ui.theme.ZocksThemeExt
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** Reihenfolge der Bahnen im Hypnogramm, von oben nach unten. */
private val Lanes = listOf(SleepStage.AWAKE, SleepStage.REM, SleepStage.LIGHT, SleepStage.DEEP)

private val EventRowHeight = 6.dp
private val LaneHeight = 20.dp
private val PlotHeight = 120.dp
private val AxisHeight = 22.dp
private val SectionGap = 12.dp

/**
 * Verlauf einer Nacht auf einer gemeinsamen Zeitachse: Wärme- und Massage-Streifen, das
 * Hypnogramm in vier Bahnen und darunter ein Messwert als Linie (nie zwei Skalen auf einmal).
 * Lücken in der Verbindung sind hinterlegt, die Linie ist dort unterbrochen. Antippen oder
 * Wischen wählt einen Zeitpunkt, den der Aufrufer als Ablesezeile anzeigt.
 */
@Composable
fun NightChart(
    timeline: NightTimeline,
    vitalPoints: List<TimelinePoint>?,
    axisLabel: (Double) -> String,
    use24h: Boolean,
    selected: Instant?,
    onSelect: (Instant) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val locale = currentLocale()
    val laneLabels = Lanes.map { stageLabel(it) }
    val colors = ChartColors(
        stages = Lanes.map { stageColor(it) },
        heat = ZocksThemeExt.colors.heat,
        massage = ZocksThemeExt.colors.massage,
        line = ZocksThemeExt.colors.sleep,
        grid = MaterialTheme.colorScheme.outlineVariant,
        gap = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
        crosshair = MaterialTheme.colorScheme.onSurface,
        surface = MaterialTheme.colorScheme.surfaceContainer,
    )

    val values = vitalPoints?.mapNotNull { it.value }.orEmpty()
    val range = remember(vitalPoints) { valueRange(values) }
    val yLabels = range?.let { (lo, hi) -> listOf(hi, (lo + hi) / 2, lo).map(axisLabel) }.orEmpty()

    // Linker Rand für die Beschriftungen – gleich breit für Hypnogramm und Linie.
    val gutterPx = remember(laneLabels, yLabels, labelStyle) {
        (laneLabels + yLabels).maxOf { textMeasurer.measure(it, labelStyle).size.width } + with(density) { 8.dp.toPx() }
    }
    val eventRows = listOf(timeline.heat, timeline.massage).count { it.isNotEmpty() }
    val hasPlot = range != null
    val height = with(density) {
        val events = if (eventRows > 0) EventRowHeight * eventRows + 2.dp * (eventRows - 1) + SectionGap else 0.dp
        events + LaneHeight * Lanes.size + (if (hasPlot) SectionGap + PlotHeight else 0.dp) + AxisHeight
    }

    fun timeAt(x: Float, width: Float): Instant {
        val fraction = ((x - gutterPx) / (width - gutterPx)).coerceIn(0f, 1f)
        val total = Duration.between(timeline.start, timeline.end).toMillis()
        return timeline.start.plusMillis((total * fraction).toLong())
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .testTag("night_chart")
            .semantics { contentDescription = description }
            .pointerInput(timeline) { detectTapGestures { onSelect(timeAt(it.x, size.width.toFloat())) } }
            .pointerInput(timeline) {
                detectHorizontalDragGestures { change, _ -> onSelect(timeAt(change.position.x, size.width.toFloat())) }
            },
    ) {
        val left = gutterPx
        val plotWidth = size.width - left
        val totalMs = Duration.between(timeline.start, timeline.end).toMillis().coerceAtLeast(1)
        fun x(time: Instant) = left + plotWidth * (Duration.between(timeline.start, time).toMillis().toFloat() / totalMs)

        var y = 0f
        // Wärme- und Massage-Streifen
        listOf(timeline.heat to colors.heat, timeline.massage to colors.massage)
            .filter { it.first.isNotEmpty() }
            .forEach { (spans, color) ->
                spans.forEach { span -> drawSpan(span, y, EventRowHeight.toPx(), color, ::x) }
                y += EventRowHeight.toPx() + 2.dp.toPx()
            }
        if (eventRows > 0) y += SectionGap.toPx() - 2.dp.toPx()

        val contentTop = y
        val laneHeight = LaneHeight.toPx()
        val hypnoHeight = laneHeight * Lanes.size
        val plotTop = contentTop + hypnoHeight + SectionGap.toPx()
        val contentBottom = if (hasPlot) plotTop + PlotHeight.toPx() else contentTop + hypnoHeight

        // Verbindungslücken hinterlegen
        timeline.gaps.forEach { gap ->
            drawRect(colors.gap, Offset(x(gap.start), contentTop), Size(max(x(gap.end) - x(gap.start), 1f), contentBottom - contentTop))
        }

        // Hypnogramm: Bahnbeschriftung links, Blöcke in der Phasenfarbe
        Lanes.forEachIndexed { index, stage ->
            val laneTop = contentTop + laneHeight * index
            drawLabel(textMeasurer, laneLabels[index], labelStyle, 0f, laneTop + laneHeight / 2)
            val blockHeight = laneHeight - 6.dp.toPx()
            timeline.stages.filter { it.second == stage }.forEach { (span, _) ->
                drawSpan(span, laneTop + 3.dp.toPx(), blockHeight, colors.stages[index], ::x)
            }
        }

        // Messwert als Linie mit drei feinen Hilfslinien
        if (range != null && vitalPoints != null) {
            val (lo, hi) = range
            val plotHeight = PlotHeight.toPx()
            fun yOf(value: Double) = (plotTop + plotHeight * (1 - (value - lo) / (hi - lo))).toFloat()
            listOf(hi, (lo + hi) / 2, lo).forEachIndexed { i, tick ->
                val ty = yOf(tick)
                drawLine(colors.grid, Offset(left, ty), Offset(size.width, ty), strokeWidth = 1f)
                drawLabel(textMeasurer, yLabels[i], labelStyle, 0f, ty)
            }
            val path = Path()
            var drawing = false
            vitalPoints.forEach { point ->
                val value = point.value
                if (value == null) {
                    drawing = false
                } else if (!drawing) {
                    path.moveTo(x(point.time), yOf(value))
                    drawing = true
                } else {
                    path.lineTo(x(point.time), yOf(value))
                }
            }
            drawPath(path, colors.line, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            selected?.let { time ->
                vitalPoints.valueAt(time)?.let { value ->
                    val center = Offset(x(time), yOf(value))
                    drawCircle(colors.surface, radius = 6.dp.toPx(), center = center)
                    drawCircle(colors.line, radius = 4.dp.toPx(), center = center)
                }
            }
        }

        // Zeitachse: volle Stunden, so dicht wie die Beschriftung es zulässt
        val axisTop = contentBottom + 4.dp.toPx()
        val formatter = DateTimeFormatter.ofPattern(if (use24h) "HH" else "h a", locale)
        val zone = ZoneId.systemDefault()
        val sample = textMeasurer.measure(formatter.format(timeline.start.atZone(zone)), labelStyle).size.width
        val pxPerHour = plotWidth * 3_600_000f / totalMs
        val stepHours = max(1, ceil((sample + 12.dp.toPx()) / pxPerHour).toInt())
        var tick = timeline.start.atZone(zone).truncatedTo(ChronoUnit.HOURS).plusHours(1)
        while (!tick.toInstant().isAfter(timeline.end)) {
            if (tick.hour % stepHours == 0) {
                val tx = x(tick.toInstant())
                drawLine(colors.grid, Offset(tx, contentBottom), Offset(tx, axisTop), strokeWidth = 1f)
                val layout = textMeasurer.measure(formatter.format(tick), labelStyle)
                val lx = (tx - layout.size.width / 2).coerceIn(left, size.width - layout.size.width)
                drawText(layout, topLeft = Offset(lx, axisTop))
            }
            tick = tick.plusHours(1)
        }

        // Ablese-Linie über Hypnogramm und Messwert
        selected?.let { time ->
            val sx = x(time)
            drawLine(colors.crosshair, Offset(sx, contentTop), Offset(sx, contentBottom), strokeWidth = 1.dp.toPx())
        }
    }
}

private data class ChartColors(
    val stages: List<Color>,
    val heat: Color,
    val massage: Color,
    val line: Color,
    val grid: Color,
    val gap: Color,
    val crosshair: Color,
    val surface: Color,
)

private fun DrawScope.drawSpan(span: TimelineSpan, top: Float, height: Float, color: Color, x: (Instant) -> Float) {
    val start = x(span.start)
    val width = max(x(span.end) - start, 2f)
    drawRoundRect(color, Offset(start, top), Size(width, height), CornerRadius(2.dp.toPx()))
}

/** Beschriftung links, vertikal mittig auf [centerY]. */
private fun DrawScope.drawLabel(measurer: TextMeasurer, text: String, style: TextStyle, x: Float, centerY: Float) {
    val layout = measurer.measure(text, style)
    drawText(layout, topLeft = Offset(x, centerY - layout.size.height / 2f))
}

/** Wertebereich mit etwas Luft, auf ganze Zahlen gerundet; `null` ohne Werte. */
private fun valueRange(values: List<Double>): Pair<Double, Double>? {
    if (values.isEmpty()) return null
    val lowest = values.min()
    val highest = values.max()
    val pad = max((highest - lowest) * 0.1, 1.0)
    return floor(lowest - pad) to ceil(highest + pad)
}
