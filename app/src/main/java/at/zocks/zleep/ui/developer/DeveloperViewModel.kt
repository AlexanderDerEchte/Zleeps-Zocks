package at.zocks.zleep.ui.developer

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.zocks.zleep.R
import at.zocks.zleep.domain.device.PairStatus
import at.zocks.zleep.domain.device.SockPair
import at.zocks.zleep.domain.device.SockPairProvider
import at.zocks.zleep.domain.model.DeviceMode
import at.zocks.zleep.domain.model.SensorSample
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.domain.model.TemperatureUnit
import at.zocks.zleep.domain.repository.SettingsRepository
import at.zocks.zleep.domain.simulator.DemoDataController
import at.zocks.zleep.domain.simulator.DemoDataStatus
import at.zocks.zleep.domain.simulator.SimulatorController
import at.zocks.zleep.domain.simulator.SimulatorState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeveloperUiState(
    val loading: Boolean = true,
    val deviceMode: DeviceMode = DeviceMode.SIMULATOR,
    val pairStatus: PairStatus? = null,
    val simulator: SimulatorState? = null,
    val speed: Int = SPEEDS[1],
    val live: Map<SockSide, SensorSample> = emptyMap(),
    val demo: DemoDataStatus = DemoDataStatus(0, loading = false, progress = 0f),
    val use24HourClock: Boolean = true,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    @param:StringRes val userMessage: Int? = null,
) {
    companion object {
        val SPEEDS = listOf(60, 300, 1200)
    }
}

sealed interface DeveloperEvent {
    data class SetDeviceMode(val mode: DeviceMode) : DeveloperEvent
    data object Connect : DeveloperEvent
    data object Disconnect : DeveloperEvent
    data class SetSpeed(val speed: Int) : DeveloperEvent
    data object PlayNight : DeveloperEvent
    data object StopNight : DeveloperEvent
    data class Dropout(val side: SockSide) : DeveloperEvent
    data object LoadDemo : DeveloperEvent
    data object ClearDemo : DeveloperEvent
    data object MessageShown : DeveloperEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DeveloperViewModel @Inject constructor(
    private val pairProvider: SockPairProvider,
    private val simulator: SimulatorController,
    private val demoData: DemoDataController,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val message = MutableStateFlow<Int?>(null)

    private val devices: Flow<Pair<PairStatus, Map<SockSide, SensorSample>>> = pairProvider.pair.flatMapLatest { pair ->
        combine(pair.status, liveSamples(pair)) { status, live -> status to live }
    }

    val uiState: StateFlow<DeveloperUiState> = combine(
        devices,
        simulator.state,
        demoData.status,
        settingsRepository.settings,
        message,
    ) { (status, live), simState, demo, settings, userMessage ->
        DeveloperUiState(
            loading = false,
            deviceMode = settings.deviceMode,
            pairStatus = status,
            simulator = simState,
            speed = settings.simulatorSpeed,
            live = live,
            demo = demo,
            use24HourClock = settings.use24HourClock,
            temperatureUnit = settings.temperatureUnit,
            userMessage = userMessage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeveloperUiState())

    fun onEvent(event: DeveloperEvent) {
        when (event) {
            is DeveloperEvent.SetDeviceMode -> launchSafely { settingsRepository.update { it.copy(deviceMode = event.mode) } }
            DeveloperEvent.Connect -> launchSafely { pairProvider.pair.value.connect() }
            DeveloperEvent.Disconnect -> launchSafely { pairProvider.pair.value.disconnect() }
            is DeveloperEvent.SetSpeed -> launchSafely { settingsRepository.update { it.copy(simulatorSpeed = event.speed) } }
            DeveloperEvent.PlayNight -> simulator.playNight(uiState.value.speed)
            DeveloperEvent.StopNight -> simulator.stopNight()
            is DeveloperEvent.Dropout -> simulator.simulateDropout(event.side, DROPOUT_SECONDS)
            DeveloperEvent.LoadDemo -> launchSafely {
                demoData.loadDemoNights()
                message.value = R.string.dev_demo_loaded
            }
            DeveloperEvent.ClearDemo -> launchSafely {
                demoData.clearDemoNights()
                message.value = R.string.dev_demo_cleared
            }
            DeveloperEvent.MessageShown -> message.value = null
        }
    }

    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { message.value = R.string.error_generic }
        }
    }

    private fun liveSamples(pair: SockPair): Flow<Map<SockSide, SensorSample>> =
        merge(pair.left.sensorData, pair.right.sensorData)
            .scan(emptyMap()) { latest, sample -> latest + (sample.side to sample) }

    private companion object {
        const val DROPOUT_SECONDS = 30
    }
}
