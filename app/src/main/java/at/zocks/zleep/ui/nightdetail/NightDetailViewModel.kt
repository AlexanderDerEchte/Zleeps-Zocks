package at.zocks.zleep.ui.nightdetail

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import at.zocks.zleep.domain.analysis.NightStatistics
import at.zocks.zleep.domain.analysis.NightStatisticsCalculator
import at.zocks.zleep.R
import at.zocks.zleep.domain.model.Night
import at.zocks.zleep.domain.model.NightData
import at.zocks.zleep.domain.recording.NightAnalyzer
import at.zocks.zleep.domain.recording.SleepWindowCorrection
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
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
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
        @param:StringRes val userMessage: Int? = null,
    ) : NightDetailUiState
}

sealed interface NightDetailEvent {
    data class EditOnset(val time: LocalTime) : NightDetailEvent
    data class EditWake(val time: LocalTime) : NightDetailEvent
    data object ResetWindow : NightDetailEvent
    data object MessageShown : NightDetailEvent
}

@HiltViewModel
class NightDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val nightRepository: NightRepository,
    private val analyzer: NightAnalyzer,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val nightId = savedStateHandle.toRoute<NightDetailDestination>().nightId
    private val loaded = MutableStateFlow<LoadResult>(LoadResult.Loading)

    /** Einmalige Meldung (Korrektur gespeichert/ungültig). */
    private val message = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<NightDetailUiState> = combine(loaded, settingsRepository.settings, message) { result, settings, userMessage ->
        when (result) {
            LoadResult.Loading -> NightDetailUiState.Loading
            LoadResult.NotFound -> NightDetailUiState.NotFound
            LoadResult.Failed -> NightDetailUiState.Error
            is LoadResult.Loaded -> NightDetailUiState.Content(
                data = result.data,
                statistics = result.statistics,
                use24HourClock = settings.use24HourClock,
                temperatureUnit = settings.temperatureUnit,
                userMessage = userMessage,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NightDetailUiState.Loading)

    init {
        load()
    }

    fun retry() = load()

    fun onEvent(event: NightDetailEvent) {
        val content = uiState.value as? NightDetailUiState.Content ?: return
        val night = content.data.night
        val zone = ZoneId.systemDefault()
        when (event) {
            is NightDetailEvent.EditOnset -> correct(night, SleepWindowCorrection.resolve(event.time, night, zone), night.finalWake)
            is NightDetailEvent.EditWake -> correct(night, night.sleepOnset, SleepWindowCorrection.resolve(event.time, night, zone))
            NightDetailEvent.ResetWindow -> viewModelScope.launch {
                nightRepository.resetSleepWindowCorrection(nightId)
                analyzer.analyze(nightId)
                load()
            }
            NightDetailEvent.MessageShown -> message.value = null
        }
    }

    private fun correct(night: Night, onset: Instant?, wake: Instant?) {
        if (onset == null || wake == null || !SleepWindowCorrection.isValid(onset, wake, night)) {
            message.value = R.string.night_window_invalid
            return
        }
        viewModelScope.launch {
            nightRepository.updateSleepWindow(nightId, onset, wake, manual = true)
            message.value = R.string.night_window_saved
            load()
        }
    }

    private fun load() {
        if (loaded.value !is LoadResult.Loaded) loaded.value = LoadResult.Loading
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
