package at.zocks.zleep.ui.heat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.zocks.zleep.R
import at.zocks.zleep.domain.heat.HeatMode
import at.zocks.zleep.domain.heat.HeatSafetyGuard
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.domain.model.TemperatureUnit
import at.zocks.zleep.ui.components.BigActionButton
import at.zocks.zleep.ui.components.BigSegmentedChoice
import at.zocks.zleep.ui.components.BigStepper
import at.zocks.zleep.ui.components.LoadingState
import at.zocks.zleep.ui.components.SideChoice
import at.zocks.zleep.ui.components.ZocksCard
import at.zocks.zleep.ui.format.currentLocale
import at.zocks.zleep.ui.format.formatDuration
import at.zocks.zleep.ui.format.formatTemperature
import at.zocks.zleep.ui.format.formatTime
import at.zocks.zleep.ui.format.sideLabelRes
import at.zocks.zleep.ui.theme.Dimens
import at.zocks.zleep.ui.theme.ZocksTheme
import at.zocks.zleep.ui.theme.ZocksThemeExt
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun HeatRoute(viewModel: HeatViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HeatPanel(state, viewModel::onEvent)
}

@Composable
fun HeatPanel(state: HeatUiState, onEvent: (HeatEvent) -> Unit, modifier: Modifier = Modifier) {
    if (state.loading) {
        LoadingState(modifier)
        return
    }
    Column(modifier.testTag("heat_panel"), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL)) {
        HeatStatusCard(state, onEvent)
        HeatSettingsCard(state, onEvent)
        PreheatCard(state, onEvent)
        SafetyInfo(state.temperatureUnit)
    }
}

@Composable
private fun HeatStatusCard(state: HeatUiState, onEvent: (HeatEvent) -> Unit) {
    val colors = ZocksThemeExt.colors
    val heating = state.isHeating
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (heating) colors.heatContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("heat_status"),
    ) {
        Column(Modifier.padding(Dimens.CardPadding), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Icon(
                    Icons.Filled.Whatshot,
                    contentDescription = null,
                    tint = if (heating) colors.heat else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = statusText(state),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = Dimens.SpaceM),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXl)) {
                listOf(SockSide.LEFT, SockSide.RIGHT).forEach { side ->
                    Column(Modifier.semantics(mergeDescendants = true) {}) {
                        Text(
                            stringResource(R.string.heat_foot, stringResource(sideLabelRes(side))),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            formatTemperature(state.footTemperature[side], state.temperatureUnit),
                            style = MaterialTheme.typography.titleLarge,
                            color = if (side in state.plans) colors.heat else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            if (heating) {
                BigActionButton(
                    text = stringResource(R.string.heat_stop),
                    icon = Icons.Filled.Whatshot,
                    onClick = { onEvent(HeatEvent.Stop) },
                    containerColor = colors.heat,
                    contentColor = colors.onHeat,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("heat_stop"),
                )
            }
        }
    }
}

@Composable
private fun statusText(state: HeatUiState): String {
    val remaining = state.remaining ?: return stringResource(R.string.heat_status_off)
    val time = formatDuration(remaining)
    val sides = state.plans.keys
    return if (sides.size == 1) {
        stringResource(R.string.heat_status_side_on, stringResource(sideLabelRes(sides.single())), time)
    } else {
        stringResource(R.string.heat_status_on, time)
    }
}

@Composable
private fun HeatSettingsCard(state: HeatUiState, onEvent: (HeatEvent) -> Unit) {
    val prefs = state.prefs
    val heatContainer = ZocksThemeExt.colors.heatContainer
    ZocksCard {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL)) {
            Labeled(stringResource(R.string.control_side)) {
                SideChoice(prefs.side, { onEvent(HeatEvent.SetSide(it)) }, activeColor = heatContainer)
            }
            Labeled(stringResource(R.string.heat_mode)) {
                BigSegmentedChoice(
                    options = HeatMode.entries,
                    selected = prefs.mode,
                    label = { stringResource(if (it == HeatMode.LEVEL) R.string.heat_mode_level else R.string.heat_mode_target) },
                    onSelect = { onEvent(HeatEvent.SetMode(it)) },
                    activeColor = heatContainer,
                    testTagPrefix = "heat_mode",
                )
            }
            when (prefs.mode) {
                HeatMode.LEVEL -> Labeled(stringResource(R.string.heat_level_value, prefs.level)) {
                    BigSegmentedChoice(
                        options = (1..state.heatLevels).toList(),
                        selected = prefs.level.coerceIn(1, state.heatLevels),
                        label = { it.toString() },
                        onSelect = { onEvent(HeatEvent.SetLevel(it)) },
                        activeColor = heatContainer,
                        testTagPrefix = "heat_level",
                    )
                }
                HeatMode.TARGET -> Labeled(stringResource(R.string.heat_target)) {
                    BigStepper(
                        value = formatTemperature(prefs.targetTemperatureC, state.temperatureUnit),
                        decreaseLabel = stringResource(R.string.heat_target_decrease),
                        increaseLabel = stringResource(R.string.heat_target_increase),
                        onDecrease = { onEvent(HeatEvent.ChangeTarget(-1)) },
                        onIncrease = { onEvent(HeatEvent.ChangeTarget(+1)) },
                        canDecrease = prefs.targetTemperatureC > HeatSafetyGuard.TARGET_MIN_C,
                        canIncrease = prefs.targetTemperatureC < HeatSafetyGuard.TARGET_MAX_C,
                        color = ZocksThemeExt.colors.heat,
                        testTag = "heat_target",
                    )
                }
            }
            Labeled(stringResource(R.string.heat_timer)) {
                BigSegmentedChoice(
                    options = HeatUiState.TIMER_OPTIONS,
                    selected = prefs.timerMinutes,
                    label = { it.toString() },
                    onSelect = { onEvent(HeatEvent.SetTimer(it)) },
                    activeColor = heatContainer,
                    testTagPrefix = "heat_timer",
                )
                Text(
                    formatDuration(Duration.ofMinutes(prefs.timerMinutes.toLong())),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SwitchRow(
                title = stringResource(R.string.heat_auto_off_asleep),
                body = stringResource(R.string.heat_auto_off_asleep_body),
                checked = prefs.autoOffWhenAsleep,
                onCheckedChange = { onEvent(HeatEvent.SetAutoOff(it)) },
                testTag = "heat_auto_off",
            )
            state.cooldownUntil?.let { until ->
                Text(
                    stringResource(R.string.heat_cooldown, formatTime(until, state.use24HourClock, currentLocale())),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (state.isHeating) {
                OutlinedButton(
                    onClick = { onEvent(HeatEvent.Start) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.ThumbTarget)
                        .testTag("heat_apply"),
                ) {
                    Text(stringResource(R.string.control_apply), style = MaterialTheme.typography.labelLarge)
                }
            } else {
                BigActionButton(
                    text = stringResource(R.string.heat_start),
                    icon = Icons.Filled.Whatshot,
                    onClick = { onEvent(HeatEvent.Start) },
                    containerColor = ZocksThemeExt.colors.heat,
                    contentColor = ZocksThemeExt.colors.onHeat,
                    enabled = state.cooldownUntil == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("heat_start"),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreheatCard(state: HeatUiState, onEvent: (HeatEvent) -> Unit) {
    val prefs = state.prefs
    val locale = currentLocale()
    var pickTime by rememberSaveable { mutableStateOf(false) }
    val time = prefs.preheatTime
    val timeText = formatTime(
        time.atDate(LocalDate.of(2026, 1, 1)).atZone(ZoneId.systemDefault()).toInstant(),
        state.use24HourClock,
        locale,
    )

    ZocksCard(title = stringResource(R.string.heat_preheat)) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
            SwitchRow(
                title = if (prefs.preheatEnabled) {
                    stringResource(R.string.heat_preheat_body, timeText, formatDuration(Duration.ofMinutes(prefs.preheatMinutes.toLong())))
                } else {
                    stringResource(R.string.heat_preheat)
                },
                body = if (prefs.preheatEnabled) null else stringResource(R.string.heat_preheat_off),
                checked = prefs.preheatEnabled,
                onCheckedChange = { onEvent(HeatEvent.SetPreheatEnabled(it)) },
                testTag = "heat_preheat",
            )
            if (prefs.preheatEnabled) {
                OutlinedButton(
                    onClick = { pickTime = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.ThumbTarget),
                ) {
                    Text("${stringResource(R.string.heat_preheat_time)} · $timeText", style = MaterialTheme.typography.labelLarge)
                }
                Labeled(stringResource(R.string.heat_preheat_duration)) {
                    BigSegmentedChoice(
                        options = HeatUiState.PREHEAT_OPTIONS,
                        selected = prefs.preheatMinutes,
                        label = { stringResource(R.string.duration_m, it) },
                        onSelect = { onEvent(HeatEvent.SetPreheatMinutes(it)) },
                        activeColor = ZocksThemeExt.colors.heatContainer,
                    )
                }
            }
        }
    }

    if (pickTime) {
        val pickerState = rememberTimePickerState(time.hour, time.minute, is24Hour = state.use24HourClock)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    onEvent(HeatEvent.SetPreheatTime(LocalTime.of(pickerState.hour, pickerState.minute)))
                    pickTime = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.action_cancel)) } },
            text = { TimePicker(pickerState) },
        )
    }
}

@Composable
private fun SafetyInfo(unit: TemperatureUnit) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(horizontal = Dimens.SpaceXs)) {
        Icon(Icons.Outlined.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(
                R.string.heat_safety_info,
                formatTemperature(HeatSafetyGuard.TARGET_MAX_C, unit),
                formatDuration(HeatSafetyGuard.MAX_CONTINUOUS),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Dimens.SpaceS),
        )
    }
}

@Composable
internal fun Labeled(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
internal fun SwitchRow(title: String, body: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit, testTag: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.ThumbTarget)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (body != null) {
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null, modifier = Modifier.padding(start = Dimens.SpaceM))
    }
}

@Preview
@Composable
private fun HeatPanelPreview() {
    ZocksTheme { HeatPanel(HeatUiState(loading = false), {}) }
}
