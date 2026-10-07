package at.zocks.zleep.ui.recording

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import at.zocks.zleep.R
import at.zocks.zleep.domain.alarm.WakeWindow
import at.zocks.zleep.domain.model.TemperatureUnit
import at.zocks.zleep.domain.recording.RecordingState
import at.zocks.zleep.ui.components.BigActionButton
import at.zocks.zleep.ui.components.InfoBadge
import at.zocks.zleep.ui.format.currentLocale
import at.zocks.zleep.ui.format.formatBpm
import at.zocks.zleep.ui.format.formatDuration
import at.zocks.zleep.ui.format.formatTemperature
import at.zocks.zleep.ui.format.formatTime
import at.zocks.zleep.ui.format.sideLabelRes
import at.zocks.zleep.ui.theme.Dimens
import at.zocks.zleep.ui.theme.ZocksThemeExt

/** Laufende Nacht auf der Startseite: Dauer, Schlafstatus, Live-Werte, Verbindung. */
@Composable
fun RecordingCard(
    state: RecordingState.Active,
    use24h: Boolean,
    unit: TemperatureUnit,
    onOpenNightMode: () -> Unit,
    onStop: () -> Unit,
    armedWindow: WakeWindow? = null,
) {
    val colors = ZocksThemeExt.colors
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.sleepContainer),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recording_card"),
    ) {
        Column(Modifier.padding(Dimens.CardPadding), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bedtime, contentDescription = null, tint = colors.sleep, modifier = Modifier.size(28.dp))
                Text(
                    stringResource(R.string.recording_card_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Dimens.SpaceM),
                )
                if (state.simulated) InfoBadge(stringResource(R.string.badge_simulator), color = colors.sleep)
            }
            RecordingStatusLines(state, use24h, unit, armedWindow)
            BigActionButton(
                text = stringResource(R.string.recording_stop),
                icon = Icons.Filled.Bedtime,
                onClick = onStop,
                containerColor = colors.sleep,
                contentColor = colors.onSleep,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("stop_recording"),
            )
            OutlinedButton(
                onClick = onOpenNightMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.ThumbTarget)
                    .testTag("open_night_mode"),
            ) { Text(stringResource(R.string.recording_night_mode), style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/** Gemeinsame Statuszeilen für Karte und Nachtmodus. */
@Composable
fun RecordingStatusLines(state: RecordingState.Active, use24h: Boolean, unit: TemperatureUnit, armedWindow: WakeWindow? = null) {
    val locale = currentLocale()
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs), modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(
            stringResource(R.string.recording_since, formatTime(state.startedAt, use24h, locale), formatDuration(state.elapsed)),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = state.sleepOnset?.takeIf { state.asleep }
                ?.let { stringResource(R.string.recording_asleep_since, formatTime(it, use24h, locale)) }
                ?: stringResource(R.string.recording_awake),
            style = MaterialTheme.typography.titleMedium,
            color = ZocksThemeExt.colors.sleep,
            modifier = Modifier.testTag("recording_sleep_status"),
        )
        val samples = state.latest.values
        val heartRate = samples.mapNotNull { it.heartRateBpm?.toDouble() }.takeIf { it.isNotEmpty() }?.average()
        val skin = samples.mapNotNull { it.skinTemperatureC }.takeIf { it.isNotEmpty() }?.average()
        Text(
            stringResource(R.string.recording_vitals, formatBpm(heartRate), formatTemperature(skin, unit)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        armedWindow?.let { window ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("recording_alarm")) {
                Icon(Icons.Outlined.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(R.string.alarm_armed, formatTime(window.start, use24h, locale), formatTime(window.end, use24h, locale)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Dimens.SpaceXs),
                )
            }
        }
        state.openGaps.forEach { side ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.BluetoothDisabled, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(R.string.recording_connection_lost, stringResource(sideLabelRes(side))),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = Dimens.SpaceXs),
                )
            }
        }
    }
}

