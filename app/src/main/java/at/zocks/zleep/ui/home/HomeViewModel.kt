package at.zocks.zleep.ui.home

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.zocks.zleep.R
import at.zocks.zleep.domain.device.PairStatus
import at.zocks.zleep.domain.device.SockPairProvider
import at.zocks.zleep.domain.model.DeviceMode
import at.zocks.zleep.domain.model.Night
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
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val pairStatus: PairStatus? = null,
    val deviceMode: DeviceMode = DeviceMode.SIMULATOR,
    val lastNight: Night? = null,
    val use24HourClock: Boolean = true,
    @param:StringRes val userMessage: Int? = null,
)

sealed interface HomeEvent {
    data object ConnectSocks : HomeEvent
    data object DisconnectSocks : HomeEvent
    data object StartNight : HomeEvent
    data object MessageShown : HomeEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val pairProvider: SockPairProvider,
    nightRepository: NightRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val message = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        pairProvider.pair.flatMapLatest { it.status },
        nightRepository.observeLatestCompletedNight(),
        settingsRepository.settings,
        message,
    ) { status, lastNight, settings, userMessage ->
        HomeUiState(
            loading = false,
            pairStatus = status,
            deviceMode = settings.deviceMode,
            lastNight = lastNight,
            use24HourClock = settings.use24HourClock,
            userMessage = userMessage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.ConnectSocks -> connect()
            HomeEvent.DisconnectSocks -> viewModelScope.launch { pairProvider.pair.value.disconnect() }
            HomeEvent.StartNight -> message.value = if (uiState.value.pairStatus?.anyConnected == true) {
                R.string.message_night_recording_soon
            } else {
                R.string.snackbar_pair_first
            }
            HomeEvent.MessageShown -> message.value = null
        }
    }

    private fun connect() {
        if (uiState.value.deviceMode == DeviceMode.BLE) {
            message.value = R.string.message_ble_not_ready
            return
        }
        viewModelScope.launch {
            runCatching { pairProvider.pair.value.connect() }
                .onFailure { message.value = R.string.error_generic }
        }
    }
}
