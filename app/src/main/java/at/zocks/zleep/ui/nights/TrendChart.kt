package at.zocks.zleep.ui.nights

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent

/** Ein Balken im Trend-Diagramm: Position (0 … [TrendColumnChart] `count` − 1) und Wert. */
data class TrendBar(val x: Int, val value: Double)

/**
 * Säulendiagramm für Trends (Vico). Eine Reihe, eine Achse; fehlende Tage bleiben leer.
 * Hilfslinien durchgezogen und zurückhaltend, Säulen oben 4 dp abgerundet, optional eine
 * beschriftete Ziellinie. Antippen zeigt den Wert.
 */
@Composable
fun TrendColumnChart(
    bars: List<TrendBar>,
    count: Int,
    color: Color,
    xLabel: (Int) -> String,
    yLabel: (Double) -> String,
    markerDecimals: Int,
    markerSuffix: String,
    decimalSeparator: String,
    description: String,
    modifier: Modifier = Modifier,
    maxY: Double? = null,
    goal: Double? = null,
    goalLabel: String? = null,
    labelSpacing: Int = 1,
    labelOffset: Int = 0,
    yStep: Double = 1.0,
) {
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisStyle = TextStyle(color = textColor, fontSize = 12.sp)
    val columnWidth = when {
        count <= 7 -> 18.dp
        count <= 12 -> 14.dp
        else -> 6.dp
    }
    val model = remember(bars) {
        CartesianChartModel(ColumnCartesianLayerModel.build { series(bars.map { it.x }, bars.map { it.value }) })
    }
    val goalLine = goal?.let { value ->
        HorizontalLine(
            y = { value },
            line = rememberLineComponent(Fill(MaterialTheme.colorScheme.onSurface), 1.dp),
            labelComponent = rememberTextComponent(style = axisStyle, padding = Insets(4.dp, 2.dp)),
            label = { goalLabel.orEmpty() },
        )
    }
    val chart = rememberCartesianChart(
        rememberColumnCartesianLayer(
            columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                rememberLineComponent(Fill(color), columnWidth, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
            ),
            rangeProvider = remember(count, maxY) {
                CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = (count - 1).toDouble(), minY = 0.0, maxY = maxY)
            },
        ),
        startAxis = VerticalAxis.rememberStart(
            line = null,
            tick = null,
            label = rememberAxisLabelComponent(style = axisStyle),
            guideline = rememberAxisGuidelineComponent(fill = Fill(gridColor), shape = RectangleShape),
            valueFormatter = remember(yLabel) { CartesianValueFormatter { _, value, _ -> yLabel(value) } },
            itemPlacer = remember(yStep) { VerticalAxis.ItemPlacer.step(step = { yStep }) },
        ),
        bottomAxis = HorizontalAxis.rememberBottom(
            line = rememberAxisLineComponent(fill = Fill(gridColor)),
            tick = null,
            guideline = null,
            label = rememberAxisLabelComponent(style = axisStyle),
            valueFormatter = remember(xLabel) { CartesianValueFormatter { _, value, _ -> xLabel(value.toInt()) } },
            itemPlacer = remember(labelSpacing, labelOffset) {
                HorizontalAxis.ItemPlacer.aligned(spacing = { labelSpacing }, offset = { labelOffset })
            },
        ),
        marker = rememberDefaultCartesianMarker(
            label = rememberTextComponent(
                style = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                padding = Insets(8.dp, 4.dp),
                background = rememberShapeComponent(
                    Fill(MaterialTheme.colorScheme.surfaceContainerHighest),
                    RoundedCornerShape(4.dp),
                ),
            ),
            valueFormatter = remember(markerDecimals, markerSuffix, decimalSeparator) {
                DefaultCartesianMarker.ValueFormatter.default(
                    decimalCount = markerDecimals,
                    decimalSeparator = decimalSeparator,
                    suffix = markerSuffix,
                    colorCode = false,
                )
            },
        ),
        decorations = listOfNotNull(goalLine),
        getXStep = { _, _, _ -> 1.0 },
    )
    CartesianChartHost(
        chart = chart,
        model = model,
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .testTag("trend_chart")
            .clearAndSetSemantics { contentDescription = description },
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
    )
}
