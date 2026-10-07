package at.zocks.zleep.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import at.zocks.zleep.R
import at.zocks.zleep.domain.analysis.ScoreComponent
import at.zocks.zleep.domain.analysis.ScorePart
import at.zocks.zleep.domain.analysis.SleepScore
import at.zocks.zleep.domain.model.NightSummary
import at.zocks.zleep.ui.format.formatDuration
import at.zocks.zleep.ui.format.scoreComponentLabel
import at.zocks.zleep.ui.theme.Dimens
import at.zocks.zleep.ui.theme.HeroNumberStyle
import at.zocks.zleep.ui.theme.ZocksThemeExt
import java.time.Duration
import kotlin.math.roundToInt

/** Große Score-Zahl mit „von 100“. */
@Composable
fun ScoreHero(score: Int, modifier: Modifier = Modifier) {
    val description = pluralStringResource(R.plurals.score_description, score, score)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            stringResource(R.string.score_value, score),
            style = HeroNumberStyle,
            color = ZocksThemeExt.colors.sleep,
        )
        Text(
            stringResource(R.string.score_of_100),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Dimens.SpaceS, bottom = Dimens.SpaceS),
        )
    }
}

/** Die fünf Teile des Scores mit Punkten, einem schmalen Balken und dem zugrunde liegenden Wert. */
@Composable
fun ScoreBreakdown(score: SleepScore, summary: NightSummary, goal: Duration, modifier: Modifier = Modifier) {
    Column(modifier.testTag("score_breakdown"), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
        score.parts.forEach { part -> ScorePartRow(part, scorePartDetail(part.component, summary, goal)) }
    }
}

@Composable
private fun ScorePartRow(part: ScorePart, detail: String?) {
    val label = scoreComponentLabel(part.component)
    val points = part.points
    val description = if (points != null) {
        pluralStringResource(R.plurals.score_part_description, part.maxPoints, label, points, part.maxPoints, detail.orEmpty())
    } else {
        stringResource(R.string.score_part_missing_description, label)
    }
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                if (points != null) stringResource(R.string.score_points, points, part.maxPoints) else stringResource(R.string.value_not_available),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Meter(fraction = points?.let { it.toFloat() / part.maxPoints } ?: 0f, modifier = Modifier.padding(vertical = Dimens.SpaceXs))
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Schmaler Füllbalken (0..1) mit abgerundeten Enden auf einer zurückhaltenden Spur. */
@Composable
fun Meter(fraction: Float, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = ZocksThemeExt.colors.sleep
    Canvas(modifier.fillMaxWidth().height(6.dp)) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = radius)
        val width = size.width * fraction.coerceIn(0f, 1f)
        if (width > 0f) drawRoundRect(fill, size = Size(width.coerceAtLeast(size.height), size.height), cornerRadius = radius)
    }
}

@Composable
private fun scorePartDetail(component: ScoreComponent, summary: NightSummary, goal: Duration): String? = when (component) {
    ScoreComponent.DURATION -> summary.totalSleep?.let {
        stringResource(R.string.score_detail_duration, formatDuration(it), formatDuration(goal))
    }
    ScoreComponent.EFFICIENCY -> summary.efficiency?.let {
        stringResource(R.string.score_detail_efficiency, (it * 100).roundToInt())
    }
    ScoreComponent.RESTORATIVE -> summary.restorativeShare?.let {
        stringResource(R.string.score_detail_restorative, (it * 100).roundToInt())
    }
    ScoreComponent.LATENCY -> summary.sleepLatency?.let { stringResource(R.string.score_detail_latency, formatDuration(it)) }
    ScoreComponent.CONTINUITY -> summary.wakeAfterOnset?.let {
        stringResource(R.string.score_detail_continuity, formatDuration(it))
    }
}
