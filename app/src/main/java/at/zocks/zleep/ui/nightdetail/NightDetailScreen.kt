package at.zocks.zleep.ui.nightdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.zocks.zleep.R
import at.zocks.zleep.domain.model.NightEventType
import at.zocks.zleep.domain.model.NightSource
import at.zocks.zleep.domain.model.SleepStage
import at.zocks.zleep.ui.components.DetailTopBar
import at.zocks.zleep.ui.components.EmptyState
import at.zocks.zleep.ui.components.ErrorState
import at.zocks.zleep.ui.components.InfoBadge
import at.zocks.zleep.ui.components.LoadingState
import at.zocks.zleep.ui.components.ScreenColumn
import at.zocks.zleep.ui.components.ZocksCard
import at.zocks.zleep.ui.components.stageColor
import at.zocks.zleep.ui.format.currentLocale
import at.zocks.zleep.ui.format.eventLabel
import at.zocks.zleep.ui.format.formatBpm
import at.zocks.zleep.ui.format.formatDuration
import at.zocks.zleep.ui.format.formatMs
import at.zocks.zleep.ui.format.formatNightDate
import at.zocks.zleep.ui.format.formatPercent
import at.zocks.zleep.ui.format.formatTemperature
import at.zocks.zleep.ui.format.formatTime
import at.zocks.zleep.ui.format.stageLabel
import at.zocks.zleep.ui.format.tagLabel
import at.zocks.zleep.ui.theme.Dimens
import at.zocks.zleep.ui.theme.MetricTextStyle
import at.zocks.zleep.ui.theme.ZocksThemeExt
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@Composable
fun NightDetailRoute(onBack: () -> Unit, viewModel: NightDetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NightDetailScreen(state, onBack, onRetry = viewModel::retry)
}

@Composable
fun NightDetailScreen(state: NightDetailUiState, onBack: () -> Unit, onRetry: () -> Unit) {
    val locale = currentLocale()
    val zone = ZoneId.systemDefault()
    val title = (state as? NightDetailUiState.Content)?.let { formatNightDate(it.data.night.nightOf(zone), locale) }
        ?: stringResource(R.string.night_detail_title)

    Scaffold(
        topBar = { DetailTopBar(title, onBack) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        modifier = Modifier.testTag("screen_night_detail"),
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (state) {
                NightDetailUiState.Loading -> LoadingState()
                NightDetailUiState.Error -> ErrorState(stringResource(R.string.error_generic), onRetry)
                NightDetailUiState.NotFound -> EmptyState(
                    icon = Icons.Outlined.NightsStay,
                    title = stringResource(R.string.night_detail_title),
                    body = stringResource(R.string.night_not_found),
                )
                is NightDetailUiState.Content -> NightDetailContent(state)
            }
        }
    }
}

@Composable
private fun NightDetailContent(state: NightDetailUiState.Content) {
    val locale = currentLocale()
    val zone = ZoneId.systemDefault()
    val night = state.data.night
    val stats = state.statistics
    fun time(instant: Instant?) =
        instant?.let { formatTime(it, state.use24HourClock, locale, zone) }

    ScreenColumn(title = null) {
        ZocksCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.night_sleep_duration),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (night.source == NightSource.DEMO) InfoBadge(stringResource(R.string.badge_demo))
            }
            Text(
                text = stats.sleepWindow?.let { formatDuration(it) } ?: stringResource(R.string.value_not_available),
                style = MetricTextStyle,
                color = ZocksThemeExt.colors.sleep,
                modifier = Modifier.testTag("night_duration"),
            )
            Row(Modifier.padding(top = Dimens.SpaceM), horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXl)) {
                KeyValue(stringResource(R.string.night_sleep_onset), time(night.sleepOnset) ?: stringResource(R.string.value_not_available))
                KeyValue(stringResource(R.string.night_final_wake), time(night.finalWake) ?: stringResource(R.string.value_not_available))
            }
        }

        ZocksCard(title = stringResource(R.string.night_section_stages)) {
            if (stats.stageMinutes.isEmpty()) {
                Text(stringResource(R.string.value_not_available), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                    listOf(SleepStage.DEEP, SleepStage.LIGHT, SleepStage.REM, SleepStage.AWAKE).forEach { stage ->
                        StageRow(stage, Duration.ofMinutes((stats.stageMinutes[stage] ?: 0).toLong()), stats.stageShare(stage))
                    }
                }
            }
        }

        ZocksCard(title = stringResource(R.string.night_section_measurements)) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                MeasurementRow(stringResource(R.string.night_avg_heart_rate), formatBpm(stats.avgHeartRateBpm))
                MeasurementRow(stringResource(R.string.night_avg_hrv), formatMs(stats.avgHrvRmssdMs))
                MeasurementRow(stringResource(R.string.night_avg_spo2), formatPercent(stats.avgSpo2Percent))
                MeasurementRow(
                    stringResource(R.string.night_avg_skin_temperature),
                    formatTemperature(stats.avgSkinTemperatureC, state.temperatureUnit),
                )
                if (!stats.gapDuration.isZero) {
                    Text(
                        stringResource(R.string.night_gaps, formatDuration(stats.gapDuration)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        ZocksCard(title = stringResource(R.string.night_section_events)) {
            val events = state.data.events
            if (events.isEmpty()) {
                Text(stringResource(R.string.night_no_events), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                    events.forEach { event ->
                        val color = when (event.type) {
                            NightEventType.HEAT -> ZocksThemeExt.colors.heat
                            NightEventType.MASSAGE -> ZocksThemeExt.colors.massage
                            NightEventType.SAFETY_SHUTOFF -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        }
                        val range = event.end?.let { end -> stringResource(R.string.time_range, time(event.start)!!, time(end)!!) }
                            ?: time(event.start)!!
                        LegendRow(color, eventLabel(event.type), range)
                    }
                }
            }
        }

        ZocksCard(title = stringResource(R.string.night_section_tags)) {
            Text(
                if (night.tags.isEmpty()) stringResource(R.string.night_no_tags) else night.tags.map { tagLabel(it) }.joinToString(", "),
                style = MaterialTheme.typography.bodyLarge,
            )
            night.note?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun KeyValue(label: String, value: String) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun StageRow(stage: SleepStage, duration: Duration, share: Int?) {
    val label = stageLabel(stage)
    LegendRow(
        color = stageColor(stage),
        label = if (share != null) stringResource(R.string.night_stage_share, label, share) else label,
        value = formatDuration(duration),
    )
}

@Composable
private fun MeasurementRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun LegendRow(color: Color, label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Spacer(Modifier.width(Dimens.SpaceM))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
