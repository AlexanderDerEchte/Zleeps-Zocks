package at.zocks.zleep.ui.developer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.zocks.zleep.R
import at.zocks.zleep.domain.model.ConnectionState
import at.zocks.zleep.domain.model.DeviceMode
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.domain.simulator.SimulationMode
import at.zocks.zleep.ui.components.DetailTopBar
import at.zocks.zleep.ui.components.LoadingState
import at.zocks.zleep.ui.components.ScreenColumn
import at.zocks.zleep.ui.components.SockStatusView
import at.zocks.zleep.ui.components.UserMessageEffect
import at.zocks.zleep.ui.components.ZocksCard
import at.zocks.zleep.ui.format.currentLocale
import at.zocks.zleep.ui.format.formatBpm
import at.zocks.zleep.ui.format.formatDecimal
import at.zocks.zleep.ui.format.formatMs
import at.zocks.zleep.ui.format.formatPercent
import at.zocks.zleep.ui.format.formatTemperature
import at.zocks.zleep.ui.format.formatTime
import at.zocks.zleep.ui.format.stageLabel
import at.zocks.zleep.ui.theme.Dimens

@Composable
fun DeveloperRoute(onBack: () -> Unit, viewModel: DeveloperViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    UserMessageEffect(state.userMessage) { viewModel.onEvent(DeveloperEvent.MessageShown) }
    DeveloperScreen(state, viewModel::onEvent, onBack)
}

@Composable
fun DeveloperScreen(state: DeveloperUiState, onEvent: (DeveloperEvent) -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = { DetailTopBar(stringResource(R.string.dev_title), onBack) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        modifier = Modifier.testTag("screen_developer"),
    ) { padding ->
        Box(Modifier.padding(padding)) {
            if (state.loading) {
                LoadingState()
            } else {
                ScreenColumn(title = null) {
                    DeviceModeCard(state.deviceMode, onEvent)
                    if (state.deviceMode == DeviceMode.SIMULATOR) SimulatorCard(state, onEvent)
                    DemoDataCard(state, onEvent)
                }
            }
        }
    }
}

@Composable
private fun DeviceModeCard(mode: DeviceMode, onEvent: (DeveloperEvent) -> Unit) {
    ZocksCard(title = stringResource(R.string.dev_section_device)) {
        Column(Modifier.selectableGroup()) {
            ModeOption(
                selected = mode == DeviceMode.SIMULATOR,
                title = stringResource(R.string.dev_mode_simulator),
                body = stringResource(R.string.dev_mode_simulator_body),
                testTag = "dev_mode_simulator",
                onSelect = { onEvent(DeveloperEvent.SetDeviceMode(DeviceMode.SIMULATOR)) },
            )
            ModeOption(
                selected = mode == DeviceMode.BLE,
                title = stringResource(R.string.dev_mode_ble),
                body = stringResource(R.string.dev_mode_ble_body),
                testTag = "dev_mode_ble",
                onSelect = { onEvent(DeveloperEvent.SetDeviceMode(DeviceMode.BLE)) },
            )
        }
    }
}

@Composable
private fun ModeOption(selected: Boolean, title: String, body: String, testTag: String, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.ThumbTarget)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = Dimens.SpaceM)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SimulatorCard(state: DeveloperUiState, onEvent: (DeveloperEvent) -> Unit) {
    val locale = currentLocale()
    val sim = state.simulator
    val status = state.pairStatus
    val connected = status?.anyConnected == true

    ZocksCard(title = stringResource(R.string.dev_section_simulator)) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
                SockStatusView(status?.left, SockSide.LEFT, Modifier.weight(1f))
                SockStatusView(status?.right, SockSide.RIGHT, Modifier.weight(1f))
            }
            OutlinedButton(
                onClick = { onEvent(if (connected) DeveloperEvent.Disconnect else DeveloperEvent.Connect) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.ThumbTarget)
                    .testTag("dev_connect"),
            ) {
                Text(stringResource(if (connected) R.string.action_disconnect_socks else R.string.action_connect_socks))
            }

            if (sim != null) {
                val time = formatTime(sim.simulatedTime, state.use24HourClock, locale)
                Text(
                    text = when (sim.mode) {
                        SimulationMode.AWAKE -> stringResource(R.string.dev_sim_mode_awake)
                        SimulationMode.NIGHT -> stringResource(R.string.dev_sim_mode_night, time)
                        SimulationMode.FINISHED -> stringResource(R.string.dev_sim_mode_finished, time)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag("dev_sim_mode"),
                )
                if (sim.mode != SimulationMode.AWAKE) {
                    LinearProgressIndicator(progress = { sim.progress }, modifier = Modifier.fillMaxWidth())
                }
                sim.trueStage?.let {
                    Text(
                        stringResource(R.string.dev_sim_true_stage, stageLabel(it)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(stringResource(R.string.dev_sim_speed), style = MaterialTheme.typography.bodyMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                DeveloperUiState.SPEEDS.forEachIndexed { index, speed ->
                    SegmentedButton(
                        selected = state.speed == speed,
                        onClick = { onEvent(DeveloperEvent.SetSpeed(speed)) },
                        shape = SegmentedButtonDefaults.itemShape(index, DeveloperUiState.SPEEDS.size),
                    ) {
                        Text(stringResource(R.string.dev_sim_speed_value, speed))
                    }
                }
            }
            val playing = sim?.mode == SimulationMode.NIGHT
            Button(
                onClick = { onEvent(if (playing) DeveloperEvent.StopNight else DeveloperEvent.PlayNight) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.ThumbTarget)
                    .testTag("dev_play_night"),
            ) {
                Text(stringResource(if (playing) R.string.dev_sim_stop else R.string.dev_sim_play_night))
            }

            if (status?.left?.connection == ConnectionState.CONNECTED || status?.right?.connection == ConnectionState.CONNECTED ||
                status?.left?.connection == ConnectionState.RECONNECTING || status?.right?.connection == ConnectionState.RECONNECTING
            ) {
                LiveTable(state)
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
                    listOf(SockSide.LEFT, SockSide.RIGHT).forEach { side ->
                        FilledTonalButton(
                            onClick = { onEvent(DeveloperEvent.SensorFault(side)) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = Dimens.ThumbTarget)
                                .testTag("dev_fault_${side.name.lowercase()}"),
                        ) {
                            Text(
                                stringResource(
                                    R.string.dev_sim_fault,
                                    stringResource(if (side == SockSide.LEFT) R.string.sock_left else R.string.sock_right),
                                ),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
                    listOf(SockSide.LEFT, SockSide.RIGHT).forEach { side ->
                        FilledTonalButton(
                            onClick = { onEvent(DeveloperEvent.Dropout(side)) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = Dimens.ThumbTarget),
                        ) {
                            Text(
                                stringResource(
                                    R.string.dev_sim_dropout,
                                    stringResource(if (side == SockSide.LEFT) R.string.sock_left else R.string.sock_right),
                                ),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            } else {
                Text(
                    stringResource(R.string.dev_sim_connect_first),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LiveTable(state: DeveloperUiState) {
    val locale = currentLocale()
    val left = state.live[SockSide.LEFT]
    val right = state.live[SockSide.RIGHT]
    val rows = listOf(
        Triple(stringResource(R.string.dev_live_heart_rate), formatBpm(left?.heartRateBpm?.toDouble()), formatBpm(right?.heartRateBpm?.toDouble())),
        Triple(stringResource(R.string.dev_live_hrv), formatMs(left?.hrvRmssdMs), formatMs(right?.hrvRmssdMs)),
        Triple(stringResource(R.string.dev_live_spo2), formatPercent(left?.spo2Percent?.toDouble()), formatPercent(right?.spo2Percent?.toDouble())),
        Triple(
            stringResource(R.string.dev_live_skin),
            formatTemperature(left?.skinTemperatureC, state.temperatureUnit),
            formatTemperature(right?.skinTemperatureC, state.temperatureUnit),
        ),
        Triple(
            stringResource(R.string.dev_live_motion),
            left?.motion?.let { formatDecimal(it, 2, locale) } ?: stringResource(R.string.value_not_available),
            right?.motion?.let { formatDecimal(it, 2, locale) } ?: stringResource(R.string.value_not_available),
        ),
        Triple(
            stringResource(R.string.dev_live_battery),
            formatPercent(state.pairStatus?.left?.batteryPercent?.toDouble()),
            formatPercent(state.pairStatus?.right?.batteryPercent?.toDouble()),
        ),
    )
    Column(Modifier.testTag("dev_live_table"), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
        Row {
            Text("", Modifier.weight(1.2f))
            Text(stringResource(R.string.sock_left), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End)
            Text(stringResource(R.string.sock_right), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End)
        }
        rows.forEach { (label, l, r) ->
            Row(Modifier.semantics(mergeDescendants = true) {}) {
                Text(label, Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium)
                Text(l, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
                Text(r, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun DemoDataCard(state: DeveloperUiState, onEvent: (DeveloperEvent) -> Unit) {
    ZocksCard(title = stringResource(R.string.dev_section_demo)) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
            Text(
                pluralStringResource(R.plurals.dev_demo_count, state.demo.demoNightCount, state.demo.demoNightCount),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag("dev_demo_count"),
            )
            if (state.demo.loading) {
                Text(stringResource(R.string.dev_demo_loading), style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(progress = { state.demo.progress }, modifier = Modifier.fillMaxWidth())
            }
            Button(
                onClick = { onEvent(DeveloperEvent.LoadDemo) },
                enabled = !state.demo.loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.ThumbTarget)
                    .testTag("dev_demo_load"),
            ) {
                Text(stringResource(R.string.dev_demo_load))
            }
            OutlinedButton(
                onClick = { onEvent(DeveloperEvent.ClearDemo) },
                enabled = !state.demo.loading && state.demo.demoNightCount > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.ThumbTarget)
                    .testTag("dev_demo_clear"),
            ) {
                Text(stringResource(R.string.dev_demo_clear))
            }
        }
    }
}
