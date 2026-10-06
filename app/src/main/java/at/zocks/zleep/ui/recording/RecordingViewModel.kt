package at.zocks.zleep.ui.recording

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.zocks.zleep.domain.model.TemperatureUnit
import at.zocks.zleep.domain.recording.NightRecorder
import at.zocks.zleep.domain.recording.RecordingLauncher
import at.zocks.zleep.domain.recording.RecordingState
import at.zocks.zleep.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class RecordingUiState(
    val recording: RecordingState = RecordingState.Idle(),
    val use24HourClock: Boolean = true,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val confirmStop: Boolean = false,
)

sealed interface RecordingEvent {
    data object RequestStop : RecordingEvent
    data object ConfirmStop : RecordingEvent
    data object DismissStop : RecordingEvent
}

@HiltViewModel
class RecordingViewModel @Inject constructor(
    recorder: NightRecorder,
    settingsRepository: SettingsRepository,
    private val launcher: RecordingLauncher,
) : ViewModel() {

    private val confirmStop = MutableStateFlow(false)

    val uiState: StateFlow<RecordingUiState> = combine(recorder.state, settingsRepository.settings, confirmStop) { recording, settings, confirm ->
        RecordingUiState(recording, settings.use24HourClock, settings.temperatureUnit, confirm && recording is RecordingState.Active)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordingUiState(recording = recorder.state.value))

    fun onEvent(event: RecordingEvent) {
        when (event) {
            RecordingEvent.RequestStop -> confirmStop.value = true
            RecordingEvent.DismissStop -> confirmStop.value = false
            RecordingEvent.ConfirmStop -> {
                confirmStop.update { false }
                launcher.stop()
            }
        }
    }
}
