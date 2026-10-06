package at.zocks.zleep.ui.nights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.zocks.zleep.domain.model.Night
import at.zocks.zleep.domain.repository.NightRepository
import at.zocks.zleep.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface NightsUiState {
    data object Loading : NightsUiState
    data object Empty : NightsUiState
    data object Error : NightsUiState
    data class Content(val nights: List<Night>, val use24HourClock: Boolean) : NightsUiState
}

@HiltViewModel
class NightsViewModel @Inject constructor(
    nightRepository: NightRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<NightsUiState> = combine(
        nightRepository.observeNights(),
        settingsRepository.settings,
    ) { nights, settings ->
        if (nights.isEmpty()) NightsUiState.Empty else NightsUiState.Content(nights, settings.use24HourClock)
    }
        .catch { emit(NightsUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NightsUiState.Loading)
}
