package at.zocks.zleep.ui.nightdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import at.zocks.zleep.domain.analysis.NightStatistics
import at.zocks.zleep.domain.analysis.NightStatisticsCalculator
import at.zocks.zleep.domain.model.NightData
import at.zocks.zleep.domain.model.TemperatureUnit
import at.zocks.zleep.domain.repository.NightRepository
import at.zocks.zleep.domain.repository.SettingsRepository
import at.zocks.zleep.ui.navigation.NightDetailDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

sealed interface NightDetailUiState {
    data object Loading : NightDetailUiState
    data object NotFound : NightDetailUiState
    data object Error : NightDetailUiState
    data class Content(
        val data: NightData,
        val statistics: NightStatistics,
        val use24HourClock: Boolean,
        val temperatureUnit: TemperatureUnit,
    ) : NightDetailUiState
}

@HiltViewModel
class NightDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val nightRepository: NightRepository,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val nightId = savedStateHandle.toRoute<NightDetailDestination>().nightId
    private val loaded = MutableStateFlow<LoadResult>(LoadResult.Loading)

    val uiState: StateFlow<NightDetailUiState> = combine(loaded, settingsRepository.settings) { result, settings ->
        when (result) {
            LoadResult.Loading -> NightDetailUiState.Loading
            LoadResult.NotFound -> NightDetailUiState.NotFound
            LoadResult.Failed -> NightDetailUiState.Error
            is LoadResult.Loaded -> NightDetailUiState.Content(
                data = result.data,
                statistics = result.statistics,
                use24HourClock = settings.use24HourClock,
                temperatureUnit = settings.temperatureUnit,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NightDetailUiState.Loading)

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        loaded.value = LoadResult.Loading
        viewModelScope.launch {
            loaded.value = runCatching { nightRepository.getNightData(nightId) }.fold(
                onSuccess = { data ->
                    if (data == null) {
                        LoadResult.NotFound
                    } else {
                        LoadResult.Loaded(data, NightStatisticsCalculator.calculate(data, clock.instant()))
                    }
                },
                onFailure = { LoadResult.Failed },
            )
        }
    }

    private sealed interface LoadResult {
        data object Loading : LoadResult
        data object NotFound : LoadResult
        data object Failed : LoadResult
        data class Loaded(val data: NightData, val statistics: NightStatistics) : LoadResult
    }
}
