package at.zocks.zleep.ui.nights

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import at.zocks.zleep.R
import at.zocks.zleep.domain.analysis.Insight
import at.zocks.zleep.domain.analysis.InsightFactor
import at.zocks.zleep.domain.analysis.InsightMetric
import at.zocks.zleep.domain.analysis.RegularityRating
import at.zocks.zleep.domain.analysis.TrendPeriod
import at.zocks.zleep.domain.analysis.TrendPoint
import at.zocks.zleep.ui.components.BigSegmentedChoice
import at.zocks.zleep.ui.components.EmptyState
import at.zocks.zleep.ui.components.ZocksCard
import at.zocks.zleep.ui.format.currentLocale
import at.zocks.zleep.ui.format.formatBpm
import at.zocks.zleep.ui.format.formatDecimal
import at.zocks.zleep.ui.format.formatDuration
import at.zocks.zleep.ui.format.formatLocalTime
import at.zocks.zleep.ui.format.tagLabel
import at.zocks.zleep.ui.theme.Dimens
import at.zocks.zleep.ui.theme.ZocksThemeExt
import java.text.DecimalFormatSymbols
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Inhalt der Trend-Ansicht als Elemente der Nächte-Liste. */
fun LazyListScope.trendItems(state: NightsUiState.Content, onEvent: (NightsEvent) -> Unit) {
    item {
        BigSegmentedChoice(
            options = TrendPeriod.entries,
            selected = state.trendPeriod,
            label = { periodLabel(it) },
            onSelect = { onEvent(NightsEvent.SelectPeriod(it)) },
            testTagPrefix = "trend_period",
        )
    }
    item { TrendStatsCard(state) }
    if (state.trend.nights == 0) {
        item {
            ZocksCard {
                EmptyState(
                    icon = Icons.Outlined.Insights,
                    title = stringResource(R.string.nights_view_trends),
                    body = stringResource(R.string.trend_no_data),
                )
            }
        }
    } else {
        item { TrendChartsCard(state) }
    }
    item { InsightsCard(state) }
}

@Composable
private fun periodLabel(period: TrendPeriod): String = stringResource(
    when (period) {
        TrendPeriod.WEEK -> R.string.trend_week
        TrendPeriod.MONTH -> R.string.trend_month
        TrendPeriod.YEAR -> R.string.trend_year
    },
)

@Composable
private fun TrendStatsCard(state: NightsUiState.Content) {
    val trend = state.trend
    val locale = currentLocale()
    val notAvailable = stringResource(R.string.value_not_available)
    ZocksCard(modifier = Modifier.testTag("trend_stats")) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceL)) {
                StatTile(stringResource(R.string.trend_avg_score), trend.averageScore?.toString() ?: notAvailable, Modifier.weight(1f))
                StatTile(
                    stringResource(R.string.trend_avg_sleep),
                    trend.averageSleepMinutes?.let { formatDuration(Duration.ofMinutes(it.toLong())) } ?: notAvailable,
                    Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceL)) {
                StatTile(stringResource(R.string.trend_avg_resting_hr), formatBpm(trend.averageRestingHeartRate), Modifier.weight(1f))
                val regularity = trend.regularity
                StatTile(
                    label = stringResource(R.string.trend_regularity),
                    value = regularity?.let { regularityLabel(it.rating) } ?: notAvailable,
                    detail = regularity?.let {
                        stringResource(
                            R.string.trend_regularity_detail,
                            formatLocalTime(it.averageSleepOnset, state.use24HourClock, locale),
                            formatDuration(Duration.ofMinutes(it.sleepOnsetSpreadMinutes.toLong())),
                            formatLocalTime(it.averageWake, state.use24HourClock, locale),
                            formatDuration(Duration.ofMinutes(it.wakeSpreadMinutes.toLong())),
                        )
                    } ?: stringResource(R.string.trend_regularity_missing),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("trend_regularity"),
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, detail: String? = null) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge)
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun regularityLabel(rating: RegularityRating): String = stringResource(
    when (rating) {
        RegularityRating.VERY_REGULAR -> R.string.trend_regularity_very
        RegularityRating.FAIRLY_REGULAR -> R.string.trend_regularity_fairly
        RegularityRating.IRREGULAR -> R.string.trend_regularity_irregular
    },
)

@Composable
private fun TrendChartsCard(state: NightsUiState.Content) {
    val trend = state.trend
    val locale = currentLocale()
    var showTable by rememberSaveable { mutableStateOf(false) }
    val points = trend.points
    val labelFormat = DateTimeFormatter.ofPattern(
        DateFormat.getBestDateTimePattern(
            locale,
            when (trend.period) {
                TrendPeriod.WEEK -> "EEE"
                TrendPeriod.MONTH -> "dM"
                TrendPeriod.YEAR -> "MMM"
            },
        ),
        locale,
    )
    val xLabel: (Int) -> String = { index -> points.getOrNull(index)?.let { labelFormat.format(it.date) }.orEmpty() }
    val labelSpacing = when (trend.period) {
        TrendPeriod.WEEK -> 1
        TrendPeriod.MONTH -> 7
        TrendPeriod.YEAR -> 2
    }
    // Beschriftungen nicht an den Rand setzen, sonst wird das letzte Datum abgeschnitten.
    val labelOffset = when (trend.period) {
        TrendPeriod.WEEK -> 0
        TrendPeriod.MONTH -> 3
        TrendPeriod.YEAR -> 1
    }
    val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator.toString()
    val periodName = periodLabel(trend.period)
    val sleepColor = ZocksThemeExt.colors.sleep

    ZocksCard(modifier = Modifier.testTag("trend_charts")) {
        val scoreTitle = stringResource(R.string.trend_chart_score)
        ChartTitle(scoreTitle)
        TrendColumnChart(
            bars = points.mapIndexedNotNull { i, p -> p.score?.let { TrendBar(i, it.toDouble()) } },
            count = points.size,
            color = sleepColor,
            xLabel = xLabel,
            yLabel = { it.roundToInt().toString() },
            markerDecimals = 0,
            markerSuffix = "",
            decimalSeparator = separator,
            description = stringResource(R.string.trend_chart_description, scoreTitle, periodName),
            maxY = 100.0,
            yStep = 25.0,
            labelSpacing = labelSpacing,
            labelOffset = labelOffset,
        )

        val sleepTitle = stringResource(R.string.trend_chart_sleep)
        val goalHours = state.sleepGoal.toMinutes() / 60.0
        ChartTitle(sleepTitle, Modifier.padding(top = Dimens.SpaceXl))
        TrendColumnChart(
            bars = points.mapIndexedNotNull { i, p -> p.totalSleepMinutes?.let { TrendBar(i, it / 60.0) } },
            count = points.size,
            color = sleepColor,
            xLabel = xLabel,
            yLabel = { formatDecimal(it, 0, locale) },
            markerDecimals = 1,
            markerSuffix = " h",
            decimalSeparator = separator,
            description = stringResource(R.string.trend_chart_description, sleepTitle, periodName),
            maxY = maxOf(10.0, (points.mapNotNull { it.totalSleepMinutes }.maxOrNull() ?: 0) / 60.0 + 1).let { 2 * ceil(it / 2) },
            goal = goalHours,
            goalLabel = stringResource(R.string.trend_goal_line, formatDuration(state.sleepGoal)),
            yStep = 2.0,
            labelSpacing = labelSpacing,
            labelOffset = labelOffset,
        )

        TextButton(onClick = { showTable = !showTable }, modifier = Modifier.padding(top = Dimens.SpaceS).testTag("trend_table_toggle")) {
            Text(stringResource(if (showTable) R.string.chart_hide_table else R.string.chart_show_table))
        }
        if (showTable) TrendTable(points.filter { it.nights > 0 }, trend.period, locale)
    }
}

@Composable
private fun ChartTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = Dimens.SpaceS),
    )
}

@Composable
private fun TrendTable(points: List<TrendPoint>, period: TrendPeriod, locale: Locale) {
    val format = DateTimeFormatter.ofPattern(
        DateFormat.getBestDateTimePattern(locale, if (period == TrendPeriod.YEAR) "MMMMyyyy" else "EEEdMMM"),
        locale,
    )
    val dash = stringResource(R.string.table_dash)
    val cell = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
    Column(Modifier.testTag("trend_table"), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
        Row {
            listOf(R.string.trend_table_date, R.string.trend_table_score, R.string.trend_table_sleep).forEachIndexed { i, header ->
                Text(
                    stringResource(header),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(if (i == 0) 2f else 1f),
                )
            }
        }
        points.forEach { point ->
            Row(Modifier.semantics(mergeDescendants = true) {}) {
                Text(format.format(point.date), style = cell, modifier = Modifier.weight(2f))
                Text(point.score?.toString() ?: dash, style = cell, modifier = Modifier.weight(1f))
                Text(
                    point.totalSleepMinutes?.let { formatDuration(Duration.ofMinutes(it.toLong())) } ?: dash,
                    style = cell,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun InsightsCard(state: NightsUiState.Content) {
    ZocksCard(title = stringResource(R.string.insights_title), modifier = Modifier.testTag("insights")) {
        when {
            !state.enoughForInsights -> Column(Modifier.testTag("insights_not_enough")) {
                Text(stringResource(R.string.insights_not_enough_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.insights_not_enough_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.insights.isEmpty() -> Text(
                stringResource(R.string.insights_none),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL)) {
                state.insights.forEach { InsightRow(it) }
            }
        }
        if (state.enoughForInsights) {
            Text(
                stringResource(R.string.insights_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.SpaceL),
            )
        }
    }
}

@Composable
private fun InsightRow(insight: Insight) {
    val factor = insight.factor
    val icon = when (factor) {
        InsightFactor.Heat -> Icons.Outlined.Whatshot
        InsightFactor.Massage -> Icons.Outlined.Spa
        is InsightFactor.WithTag -> Icons.AutoMirrored.Outlined.Label
    }
    val title = when (factor) {
        InsightFactor.Heat -> stringResource(R.string.insight_factor_heat)
        InsightFactor.Massage -> stringResource(R.string.insight_factor_massage)
        is InsightFactor.WithTag -> tagLabel(factor.tag)
    }
    Row(
        Modifier
            .semantics(mergeDescendants = true) {}
            .testTag("insight_card"),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 2.dp, end = Dimens.SpaceM)
                .size(22.dp),
        )
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(insightText(insight), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(
                    R.string.insight_basis,
                    pluralStringResource(R.plurals.insight_nights_with, insight.nightsWith, insight.nightsWith),
                    pluralStringResource(R.plurals.insight_nights_without, insight.nightsWithout, insight.nightsWithout),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun insightText(insight: Insight): String {
    val diff = abs(insight.difference)
    val higher = insight.difference > 0
    val points = diff.roundToInt()
    val minutes = formatDuration(Duration.ofMinutes(points.toLong()))
    return when (insight.metric) {
        InsightMetric.SCORE ->
            pluralStringResource(if (higher) R.plurals.insight_score_higher else R.plurals.insight_score_lower, points, points)
        InsightMetric.SLEEP_LATENCY_MINUTES ->
            stringResource(if (higher) R.string.insight_latency_longer else R.string.insight_latency_shorter, minutes)
        InsightMetric.TOTAL_SLEEP_MINUTES ->
            stringResource(if (higher) R.string.insight_sleep_longer else R.string.insight_sleep_shorter, minutes)
        InsightMetric.RESTORATIVE_PERCENT ->
            pluralStringResource(
                if (higher) R.plurals.insight_restorative_higher else R.plurals.insight_restorative_lower,
                points,
                points,
            )
    }
}
