package at.zocks.zleep.ui.control

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.zocks.zleep.R
import at.zocks.zleep.domain.device.PairStatus
import at.zocks.zleep.domain.device.SockPairProvider
import at.zocks.zleep.domain.model.DeviceMode
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

data class ControlUiState(
    val pairStatus: PairStatus? = null,
    val deviceMode: DeviceMode = DeviceMode.SIMULATOR,
    @param:StringRes val userMessage: Int? = null,
)

sealed interface ControlEvent {
    data object ConnectSocks : ControlEvent
    data object MessageShown : ControlEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ControlViewModel @Inject constructor(
    private val pairProvider: SockPairProvider,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val message = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<ControlUiState> = combine(
        pairProvider.pair.flatMapLatest { it.status },
        settingsRepository.settings,
        message,
    ) { status, settings, userMessage -> ControlUiState(status, settings.deviceMode, userMessage) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ControlUiState())

    fun onEvent(event: ControlEvent) {
        when (event) {
            ControlEvent.ConnectSocks -> if (uiState.value.deviceMode == DeviceMode.BLE) {
                message.value = R.string.message_ble_not_ready
            } else {
                viewModelScope.launch {
                    runCatching { pairProvider.pair.value.connect() }.onFailure { message.value = R.string.error_generic }
                }
            }
            ControlEvent.MessageShown -> message.value = null
        }
    }
}
