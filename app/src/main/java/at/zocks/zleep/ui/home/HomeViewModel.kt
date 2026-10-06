package at.zocks.zleep.ui.home

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.zocks.zleep.R
import at.zocks.zleep.domain.device.PairStatus
import at.zocks.zleep.domain.device.SockPairProvider
import at.zocks.zleep.domain.model.DeviceMode
import at.zocks.zleep.domain.model.Night
import at.zocks.zleep.domain.model.TemperatureUnit
import at.zocks.zleep.domain.recording.NightRecorder
import at.zocks.zleep.domain.recording.RecordingLauncher
import at.zocks.zleep.domain.recording.RecordingState
import at.zocks.zleep.domain.repository.NightRepository
import at.zocks.zleep.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val pairStatus: PairStatus? = null,
    val deviceMode: DeviceMode = DeviceMode.SIMULATOR,
    val lastNight: Night? = null,
    val use24HourClock: Boolean = true,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val recording: RecordingState = RecordingState.Idle(),
    val showStartSheet: Boolean = false,
    @param:StringRes val userMessage: Int? = null,
)

sealed interface HomeEvent {
    data object ConnectSocks : HomeEvent
    data object DisconnectSocks : HomeEvent

    /** Öffnet die Checkliste vor der Nacht. */
    data object StartNight : HomeEvent
    data object ConfirmStart : HomeEvent
    data object DismissStartSheet : HomeEvent
    data object StopNight : HomeEvent
    data object MessageShown : HomeEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val pairProvider: SockPairProvider,
    nightRepository: NightRepository,
    settingsRepository: SettingsRepository,
    recorder: NightRecorder,
    private val launcher: RecordingLauncher,
) : ViewModel() {

    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<HomeUiState> = combine(
        pairProvider.pair.flatMapLatest { it.status },
        nightRepository.observeLatestCompletedNight(),
        settingsRepository.settings,
        recorder.state,
        local,
    ) { status, lastNight, settings, recording, localState ->
        HomeUiState(
            loading = false,
            pairStatus = status,
            deviceMode = settings.deviceMode,
            lastNight = lastNight,
            use24HourClock = settings.use24HourClock,
            temperatureUnit = settings.temperatureUnit,
            recording = recording,
            showStartSheet = localState.showStartSheet,
            userMessage = localState.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // Ende einer Aufzeichnung (auch automatisch am Morgen) kurz bestätigen.
        viewModelScope.launch {
            var wasActive = recorder.state.value is RecordingState.Active
            recorder.state.collect { state ->
                if (wasActive && state is RecordingState.Idle) {
                    message(
                        when {
                            state.discarded -> R.string.recording_discarded
                            state.autoStopped -> R.string.recording_saved_auto
                            else -> R.string.recording_saved
                        },
                    )
                }
                wasActive = state is RecordingState.Active
            }
        }
    }

    private fun message(@StringRes id: Int?) = local.update { it.copy(message = id) }

    private data class LocalState(val showStartSheet: Boolean = false, @param:StringRes val message: Int? = null)

    fun onEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.ConnectSocks -> connect()
            HomeEvent.DisconnectSocks -> viewModelScope.launch { pairProvider.pair.value.disconnect() }
            HomeEvent.StartNight -> local.update { it.copy(showStartSheet = true) }
            HomeEvent.DismissStartSheet -> local.update { it.copy(showStartSheet = false) }
            HomeEvent.ConfirmStart -> {
                if (uiState.value.pairStatus?.anyConnected == true) {
                    launcher.start()
                    local.update { it.copy(showStartSheet = false) }
                } else {
                    message(R.string.snackbar_pair_first)
                }
            }
            HomeEvent.StopNight -> launcher.stop()
            HomeEvent.MessageShown -> message(null)
        }
    }

    private fun connect() {
        if (uiState.value.deviceMode == DeviceMode.BLE) {
            message(R.string.message_ble_not_ready)
            return
        }
        viewModelScope.launch {
            runCatching { pairProvider.pair.value.connect() }
                .onFailure { message(R.string.error_generic) }
        }
    }
}
