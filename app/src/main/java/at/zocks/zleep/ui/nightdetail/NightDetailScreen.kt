package at.zocks.zleep.ui.nightdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import at.zocks.zleep.domain.model.TemperatureUnit
import at.zocks.zleep.ui.components.BigSegmentedChoice
import at.zocks.zleep.ui.components.DetailTopBar
import at.zocks.zleep.ui.components.EmptyState
import at.zocks.zleep.ui.components.ErrorState
import at.zocks.zleep.ui.components.InfoBadge
import at.zocks.zleep.ui.components.LoadingState
import at.zocks.zleep.ui.components.ScoreBreakdown
import at.zocks.zleep.ui.components.ScoreHero
import at.zocks.zleep.ui.components.ScreenColumn
import at.zocks.zleep.ui.components.UserMessageEffect
import at.zocks.zleep.ui.components.ZocksCard
import at.zocks.zleep.ui.components.charts.ChartLegend
import at.zocks.zleep.ui.components.charts.LegendEntry
import at.zocks.zleep.ui.components.charts.StageBar
import at.zocks.zleep.ui.components.charts.StageOrder
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
import at.zocks.zleep.ui.format.scoreComponentLabel
import at.zocks.zleep.ui.format.stageLabel
import at.zocks.zleep.ui.format.tagLabel
import at.zocks.zleep.ui.theme.Dimens
import at.zocks.zleep.ui.theme.ZocksThemeExt
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun NightDetailRoute(onBack: () -> Unit, viewModel: NightDetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    UserMessageEffect((state as? NightDetailUiState.Content)?.userMessage) { viewModel.onEvent(NightDetailEvent.MessageShown) }
    NightDetailScreen(state, onBack, onRetry = viewModel::retry, onEvent = viewModel::onEvent)
}

@Composable
fun NightDetailScreen(
    state: NightDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onEvent: (NightDetailEvent) -> Unit = {},
) {
    val locale = currentLocale()
    val zone = ZoneId.systemDefault()
    val title = (state as? NightDetailUiState.Content)?.let { formatNightDate(it.night.nightOf(zone), locale) }
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
                is NightDetailUiState.Content -> NightDetailContent(state, onEvent)
            }
        }
    }
}

/** Welche Zeit gerade per Uhr-Dialog korrigiert wird. */
private enum class EditField { ONSET, WAKE }

@Composable
private fun NightDetailContent(state: NightDetailUiState.Content, onEvent: (NightDetailEvent) -> Unit) {
    var editing by rememberSaveable { mutableStateOf<EditField?>(null) }
    ScreenColumn(title = null) {
        OverviewCard(state, onEditOnset = { editing = EditField.ONSET }, onEditWake = { editing = EditField.WAKE }, onEvent)
        if (state.score != null) {
            ZocksCard(title = stringResource(R.string.score_breakdown)) {
                ScoreBreakdown(state.score, state.summary, state.sleepGoal)
                Text(
                    stringResource(R.string.score_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimens.SpaceL),
                )
            }
        }
        MetricsCard(state)
        StagesCard(state)
        TimelineCard(state)
        AveragesCard(state)
        EventsCard(state)
        TagsCard(state, onEvent)
    }
    editing?.let { field -> TimeDialog(state, field, onDismiss = { editing = null }, onEvent) }
}

@Composable
private fun OverviewCard(
    state: NightDetailUiState.Content,
    onEditOnset: () -> Unit,
    onEditWake: () -> Unit,
    onEvent: (NightDetailEvent) -> Unit,
) {
    val night = state.night
    val locale = currentLocale()
    val zone = ZoneId.systemDefault()
    fun time(instant: Instant?) = instant?.let { formatTime(it, state.use24HourClock, locale, zone) }
    val notAvailable = stringResource(R.string.value_not_available)

    ZocksCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.score_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (night.source == NightSource.DEMO) InfoBadge(stringResource(R.string.badge_demo))
        }
        if (state.score != null) {
            ScoreHero(state.score.value, Modifier.testTag("night_score"))
            state.score.weakestPart?.takeIf { it.points != null && it.points < it.maxPoints }?.let { part ->
                Text(
                    stringResource(R.string.score_lever, scoreComponentLabel(part.component)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text(stringResource(R.string.score_not_available), style = MaterialTheme.typography.bodyLarge)
        }

        Row(Modifier.padding(top = Dimens.SpaceL), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.night_sleep_duration),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = state.summary.totalSleep?.let { formatDuration(it) } ?: notAvailable,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.testTag("night_duration"),
                )
            }
            Text(
                stringResource(R.string.metric_time_in_bed, formatDuration(state.summary.timeInBed)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.padding(top = Dimens.SpaceM), horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceL)) {
            EditableTime(
                label = stringResource(R.string.night_sleep_onset),
                value = time(night.sleepOnset) ?: notAvailable,
                editLabel = stringResource(R.string.night_edit_onset),
                enabled = !night.isRecording,
                onEdit = onEditOnset,
                modifier = Modifier
                    .weight(1f)
                    .testTag("edit_onset"),
            )
            EditableTime(
                label = stringResource(R.string.night_final_wake),
                value = time(night.finalWake) ?: notAvailable,
                editLabel = stringResource(R.string.night_edit_wake),
                enabled = !night.isRecording,
                onEdit = onEditWake,
                modifier = Modifier
                    .weight(1f)
                    .testTag("edit_wake"),
            )
        }
        if (night.sleepWindowManual) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InfoBadge(stringResource(R.string.night_window_manual), modifier = Modifier.weight(1f, fill = false))
                TextButton(onClick = { onEvent(NightDetailEvent.ResetWindow) }, modifier = Modifier.testTag("reset_window")) {
                    Text(stringResource(R.string.night_window_reset))
                }
            }
        }
    }
}

@Composable
private fun MetricsCard(state: NightDetailUiState.Content) {
    val summary = state.summary
    val notAvailable = stringResource(R.string.value_not_available)
    ZocksCard(title = stringResource(R.string.night_section_metrics), modifier = Modifier.testTag("night_metrics")) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceL)) {
                MetricTile(
                    label = stringResource(R.string.metric_latency),
                    value = summary.sleepLatency?.let { formatDuration(it) } ?: notAvailable,
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    label = stringResource(R.string.metric_efficiency),
                    value = formatPercent(summary.efficiency?.times(100)),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceL)) {
                MetricTile(
                    label = stringResource(R.string.metric_awakenings),
                    value = summary.awakenings?.toString() ?: notAvailable,
                    detail = summary.wakeAfterOnset?.let { stringResource(R.string.metric_awakenings_detail, formatDuration(it)) },
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    label = stringResource(R.string.metric_resting_hr),
                    value = formatBpm(summary.restingHeartRateBpm),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier, detail: String? = null) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge)
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StagesCard(state: NightDetailUiState.Content) {
    val summary = state.summary
    ZocksCard(title = stringResource(R.string.night_section_stages)) {
        if (summary.stageMinutes.isEmpty()) {
            Text(stringResource(R.string.value_not_available), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            StageBar(summary.stageMinutes, Modifier.padding(bottom = Dimens.SpaceL))
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                StageOrder.forEach { stage ->
                    StageRow(stage, Duration.ofMinutes((summary.stageMinutes[stage] ?: 0).toLong()), summary.stageShare(stage))
                }
            }
        }
    }
}

@Composable
private fun TimelineCard(state: NightDetailUiState.Content) {
    val timeline = state.timeline
    var vital by rememberSaveable { mutableStateOf(VitalSeries.HEART_RATE) }
    var selected by remember(timeline) { mutableStateOf<Instant?>(null) }
    var showTable by rememberSaveable { mutableStateOf(false) }
    val locale = currentLocale()
    val unit = state.temperatureUnit

    ZocksCard(title = stringResource(R.string.night_section_timeline)) {
        if (timeline.hasVitals) {
            BigSegmentedChoice(
                options = VitalSeries.entries,
                selected = vital,
                label = { vitalShortLabel(it) },
                onSelect = { vital = it },
                testTagPrefix = "chart_vital",
                modifier = Modifier.padding(bottom = Dimens.SpaceM),
            )
        }
        val points = when (vital) {
            VitalSeries.HEART_RATE -> timeline.heartRate
            VitalSeries.HRV -> timeline.hrv
            VitalSeries.SKIN_TEMPERATURE -> timeline.skinTemperature.map { it.copy(value = it.value?.let { c -> toUnit(c, unit) }) }
        }.takeIf { series -> series.any { it.value != null } }

        // Ablesezeile: immer gleich hoch, damit das Diagramm beim Antippen nicht springt.
        val reading = selected?.let { timeline.valuesAt(it) }
        Text(
            text = if (reading != null) {
                stringResource(
                    R.string.chart_readout,
                    formatTime(reading.time, state.use24HourClock, locale),
                    reading.stage?.let { stageLabel(it) } ?: stringResource(R.string.table_dash),
                    vitalValue(vital, reading.heartRate, reading.hrv, reading.skinTemperature, unit),
                )
            } else {
                stringResource(R.string.chart_hint)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (reading != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier
                .padding(bottom = Dimens.SpaceS)
                .testTag("chart_readout"),
        )
        NightChart(
            timeline = timeline,
            vitalPoints = points,
            axisLabel = { value -> axisValue(vital, value, locale) },
            use24h = state.use24HourClock,
            selected = selected,
            onSelect = { selected = it },
            description = stringResource(R.string.chart_description, vitalLabel(vital)),
        )
        if (!timeline.hasVitals) {
            Text(
                stringResource(R.string.chart_no_vitals),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.SpaceS),
            )
        }
        val legend = StageOrder.map { LegendEntry(stageColor(it), stageLabel(it)) } +
            listOfNotNull(
                LegendEntry(ZocksThemeExt.colors.heat, stringResource(R.string.chart_legend_heat)).takeIf { timeline.heat.isNotEmpty() },
                LegendEntry(ZocksThemeExt.colors.massage, stringResource(R.string.chart_legend_massage))
                    .takeIf { timeline.massage.isNotEmpty() },
                LegendEntry(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), stringResource(R.string.chart_legend_gap))
                    .takeIf { timeline.gaps.isNotEmpty() },
            )
        ChartLegend(legend, Modifier.padding(top = Dimens.SpaceM))
        TextButton(
            onClick = { showTable = !showTable },
            modifier = Modifier
                .padding(top = Dimens.SpaceS)
                .testTag("chart_table_toggle"),
        ) {
            Text(stringResource(if (showTable) R.string.chart_hide_table else R.string.chart_show_table))
        }
        if (showTable) TimelineTable(state)
    }
}

/** Tabellenansicht des Verlaufs in 30-Minuten-Schritten (Alternative zum Diagramm). */
@Composable
private fun TimelineTable(state: NightDetailUiState.Content) {
    val timeline = state.timeline
    val locale = currentLocale()
    val rows = remember(timeline) {
        // Auf volle halbe Stunden ausgerichtet, damit die Zeilen leicht zu lesen sind.
        val first = timeline.start.atZone(ZoneId.systemDefault()).truncatedTo(ChronoUnit.HOURS)
            .let { hour -> generateSequence(hour) { it.plus(TABLE_STEP) }.first { !it.toInstant().isBefore(timeline.start) } }
            .toInstant()
        generateSequence(first) { it.plus(TABLE_STEP) }
            .takeWhile { it.isBefore(timeline.end) }
            .map { timeline.valuesAt(it.plus(Duration.ofMinutes(1))) }
            .toList()
    }
    val dash = stringResource(R.string.table_dash)
    val cell = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
    Column(Modifier.testTag("chart_table"), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
        Row {
            listOf(R.string.table_time, R.string.table_stage, R.string.table_heart_rate, R.string.table_hrv, R.string.table_skin_temperature)
                .forEach { header ->
                    Text(
                        stringResource(header),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
        }
        rows.forEach { reading ->
            Row(Modifier.semantics(mergeDescendants = true) {}) {
                Text(formatTime(reading.time.minus(Duration.ofMinutes(1)), state.use24HourClock, locale), style = cell, modifier = Modifier.weight(1f))
                Text(reading.stage?.let { stageLabel(it) } ?: dash, style = cell, modifier = Modifier.weight(1f))
                Text(reading.heartRate?.roundToInt()?.toString() ?: dash, style = cell, modifier = Modifier.weight(1f))
                Text(reading.hrv?.roundToInt()?.toString() ?: dash, style = cell, modifier = Modifier.weight(1f))
                Text(
                    reading.skinTemperature?.let { axisValue(VitalSeries.SKIN_TEMPERATURE, toUnit(it, state.temperatureUnit), locale) } ?: dash,
                    style = cell,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private val TABLE_STEP: Duration = Duration.ofMinutes(30)

@Composable
private fun AveragesCard(state: NightDetailUiState.Content) {
    val summary = state.summary
    ZocksCard(title = stringResource(R.string.night_section_measurements)) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            MeasurementRow(stringResource(R.string.night_avg_heart_rate), formatBpm(summary.avgHeartRateBpm))
            MeasurementRow(stringResource(R.string.night_avg_hrv), formatMs(summary.avgHrvRmssdMs))
            MeasurementRow(stringResource(R.string.night_avg_spo2), formatPercent(summary.avgSpo2Percent))
            MeasurementRow(
                stringResource(R.string.night_avg_skin_temperature),
                formatTemperature(summary.avgSkinTemperatureC, state.temperatureUnit),
            )
            if (!summary.gapDuration.isZero) {
                Text(
                    stringResource(R.string.night_gaps, formatDuration(summary.gapDuration)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EventsCard(state: NightDetailUiState.Content) {
    val locale = currentLocale()
    fun time(instant: Instant) = formatTime(instant, state.use24HourClock, locale)
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
                    val range = event.end?.let { end -> stringResource(R.string.time_range, time(event.start), time(end)) }
                        ?: time(event.start)
                    LegendRow(color, eventLabel(event.type), range)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsCard(state: NightDetailUiState.Content, onEvent: (NightDetailEvent) -> Unit) {
    var adding by rememberSaveable { mutableStateOf(false) }
    val selectedIds = state.night.tags.map { it.id }.toSet()
    ZocksCard(title = stringResource(R.string.night_section_tags)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            state.allTags.forEach { tag ->
                val selected = tag.id in selectedIds
                FilterChip(
                    selected = selected,
                    onClick = { onEvent(NightDetailEvent.ToggleTag(tag)) },
                    label = { Text(tagLabel(tag)) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("tag_chip_${tag.key ?: tag.id}"),
                )
            }
            AssistChip(
                onClick = { adding = true },
                label = { Text(stringResource(R.string.tags_add)) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("action_add_tag"),
            )
        }
        NoteEditor(state.night.note, onSave = { onEvent(NightDetailEvent.SaveNote(it)) })
    }
    if (adding) {
        AddTagDialog(onDismiss = { adding = false }, onAdd = {
            onEvent(NightDetailEvent.AddTag(it))
            adding = false
        })
    }
}

@Composable
private fun NoteEditor(note: String?, onSave: (String) -> Unit) {
    var text by rememberSaveable(note) { mutableStateOf(note.orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(stringResource(R.string.note_label)) },
        placeholder = { Text(stringResource(R.string.note_placeholder)) },
        minLines = 2,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.SpaceL)
            .testTag("note_field"),
    )
    if (text.trim() != note.orEmpty()) {
        FilledTonalButton(
            onClick = { onSave(text) },
            modifier = Modifier
                .padding(top = Dimens.SpaceS)
                .heightIn(min = 48.dp)
                .testTag("action_save_note"),
        ) { Text(stringResource(R.string.action_save)) }
    }
}

@Composable
private fun AddTagDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var label by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tags_add_title)) },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(MAX_TAG_LENGTH) },
                label = { Text(stringResource(R.string.tags_add_label)) },
                singleLine = true,
                modifier = Modifier.testTag("tag_name_field"),
            )
        },
        confirmButton = {
            TextButton(onClick = { onAdd(label) }, enabled = label.isNotBlank(), modifier = Modifier.testTag("confirm_tag")) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private const val MAX_TAG_LENGTH = 30

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(state: NightDetailUiState.Content, field: EditField, onDismiss: () -> Unit, onEvent: (NightDetailEvent) -> Unit) {
    val zone = ZoneId.systemDefault()
    val night = state.night
    val current = (if (field == EditField.ONSET) night.sleepOnset else night.finalWake)?.atZone(zone)?.toLocalTime()
        ?: LocalTime.of(if (field == EditField.ONSET) 23 else 7, 0)
    val pickerState = rememberTimePickerState(current.hour, current.minute, is24Hour = state.use24HourClock)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (field == EditField.ONSET) R.string.night_edit_onset else R.string.night_edit_wake)) },
        text = { TimePicker(pickerState) },
        confirmButton = {
            TextButton(
                onClick = {
                    val picked = LocalTime.of(pickerState.hour, pickerState.minute)
                    onEvent(if (field == EditField.ONSET) NightDetailEvent.EditOnset(picked) else NightDetailEvent.EditWake(picked))
                    onDismiss()
                },
                modifier = Modifier.testTag("confirm_time"),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Einschlaf-/Aufwachzeit; antippen öffnet die Korrektur. */
@Composable
private fun EditableTime(
    label: String,
    value: String,
    editLabel: String,
    enabled: Boolean,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClickLabel = editLabel, onClick = onEdit),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
        if (enabled) {
            Icon(Icons.Outlined.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
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

/** Welcher Messwert im Verlauf als Linie gezeigt wird. */
enum class VitalSeries { HEART_RATE, HRV, SKIN_TEMPERATURE }

@Composable
private fun vitalLabel(series: VitalSeries): String = stringResource(
    when (series) {
        VitalSeries.HEART_RATE -> R.string.chart_vital_heart_rate
        VitalSeries.HRV -> R.string.chart_vital_hrv
        VitalSeries.SKIN_TEMPERATURE -> R.string.chart_vital_skin_temperature
    },
)

/** Kurzform für die Auswahl über dem Diagramm. */
@Composable
private fun vitalShortLabel(series: VitalSeries): String =
    if (series == VitalSeries.SKIN_TEMPERATURE) stringResource(R.string.chart_vital_skin_temperature_short) else vitalLabel(series)

@Composable
private fun vitalValue(series: VitalSeries, heartRate: Double?, hrv: Double?, celsius: Double?, unit: TemperatureUnit): String =
    when (series) {
        VitalSeries.HEART_RATE -> formatBpm(heartRate)
        VitalSeries.HRV -> formatMs(hrv)
        VitalSeries.SKIN_TEMPERATURE -> formatTemperature(celsius, unit)
    }

private fun toUnit(celsius: Double, unit: TemperatureUnit) = when (unit) {
    TemperatureUnit.CELSIUS -> celsius
    TemperatureUnit.FAHRENHEIT -> celsius * 9 / 5 + 32
}

/** Kurze Achsenbeschriftung: Puls und HRV ganzzahlig, Temperatur mit einer Nachkommastelle. */
private fun axisValue(series: VitalSeries, value: Double, locale: Locale): String = when (series) {
    VitalSeries.SKIN_TEMPERATURE -> String.format(locale, "%.1f", value)
    else -> value.roundToInt().toString()
}
