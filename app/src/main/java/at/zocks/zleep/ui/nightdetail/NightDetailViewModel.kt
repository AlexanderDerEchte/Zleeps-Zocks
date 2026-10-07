package at.zocks.zleep.ui.nightdetail

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import at.zocks.zleep.R
import at.zocks.zleep.di.DefaultDispatcher
import at.zocks.zleep.domain.analysis.NightSummarizer
import at.zocks.zleep.domain.analysis.NightSummaryUpdater
import at.zocks.zleep.domain.analysis.NightTimeline
import at.zocks.zleep.domain.analysis.SleepScore
import at.zocks.zleep.domain.analysis.SleepScoreCalculator
import at.zocks.zleep.domain.model.Night
import at.zocks.zleep.domain.model.NightData
import at.zocks.zleep.domain.model.NightSummary
import at.zocks.zleep.domain.model.Tag
import at.zocks.zleep.domain.model.TemperatureUnit
import at.zocks.zleep.domain.recording.NightAnalyzer
import at.zocks.zleep.domain.recording.SleepWindowCorrection
import at.zocks.zleep.domain.repository.NightRepository
import at.zocks.zleep.domain.repository.SettingsRepository
import at.zocks.zleep.domain.repository.TagRepository
import at.zocks.zleep.ui.navigation.NightDetailDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

sealed interface NightDetailUiState {
    data object Loading : NightDetailUiState
    data object NotFound : NightDetailUiState
    data object Error : NightDetailUiState
    data class Content(
        /** Aktueller Stand der Nacht (Tags, Notiz, Schlaffenster). */
        val night: Night,
        val data: NightData,
        val summary: NightSummary,
        val score: SleepScore?,
        val timeline: NightTimeline,
        val allTags: List<Tag>,
        val sleepGoal: Duration,
        val use24HourClock: Boolean,
        val temperatureUnit: TemperatureUnit,
        @param:StringRes val userMessage: Int? = null,
    ) : NightDetailUiState
}

sealed interface NightDetailEvent {
    data class EditOnset(val time: LocalTime) : NightDetailEvent
    data class EditWake(val time: LocalTime) : NightDetailEvent
    data object ResetWindow : NightDetailEvent
    data class ToggleTag(val tag: Tag) : NightDetailEvent
    data class AddTag(val label: String) : NightDetailEvent
    data class SaveNote(val note: String) : NightDetailEvent
    data object MessageShown : NightDetailEvent
}

@HiltViewModel
class NightDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val nightRepository: NightRepository,
    private val tagRepository: TagRepository,
    private val analyzer: NightAnalyzer,
    private val summaryUpdater: NightSummaryUpdater,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
    @param:DefaultDispatcher private val computeDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val nightId = savedStateHandle.toRoute<NightDetailDestination>().nightId
    private val loaded = MutableStateFlow<LoadResult>(LoadResult.Loading)

    /** Einmalige Meldung (Korrektur gespeichert/ungültig, Notiz gespeichert). */
    private val message = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<NightDetailUiState> = combine(
        loaded,
        nightRepository.observeNight(nightId),
        tagRepository.observeTags(),
        settingsRepository.settings,
        message,
    ) { result, night, tags, settings, userMessage ->
        when (result) {
            LoadResult.Loading -> NightDetailUiState.Loading
            LoadResult.NotFound -> NightDetailUiState.NotFound
            LoadResult.Failed -> NightDetailUiState.Error
            is LoadResult.Loaded -> if (night == null) {
                NightDetailUiState.NotFound
            } else {
                val goal = Duration.ofMinutes(settings.sleepGoalMinutes.toLong())
                NightDetailUiState.Content(
                    night = night,
                    data = result.data,
                    summary = result.summary,
                    score = SleepScoreCalculator.calculate(result.summary, goal),
                    timeline = result.timeline,
                    allTags = tags,
                    sleepGoal = goal,
                    use24HourClock = settings.use24HourClock,
                    temperatureUnit = settings.temperatureUnit,
                    userMessage = userMessage,
                )
            }
        }
    }
        .catch { emit(NightDetailUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NightDetailUiState.Loading)

    init {
        load()
    }

    fun retry() = load()

    fun onEvent(event: NightDetailEvent) {
        val content = uiState.value as? NightDetailUiState.Content ?: return
        val night = content.night
        val zone = ZoneId.systemDefault()
        when (event) {
            is NightDetailEvent.EditOnset -> correct(night, SleepWindowCorrection.resolve(event.time, night, zone), night.finalWake)
            is NightDetailEvent.EditWake -> correct(night, night.sleepOnset, SleepWindowCorrection.resolve(event.time, night, zone))
            NightDetailEvent.ResetWindow -> viewModelScope.launch {
                nightRepository.resetSleepWindowCorrection(nightId)
                analyzer.analyze(nightId)
                load()
            }
            is NightDetailEvent.ToggleTag -> viewModelScope.launch {
                val ids = night.tags.map { it.id }.toSet()
                nightRepository.setTags(nightId, if (event.tag.id in ids) ids - event.tag.id else ids + event.tag.id)
            }
            is NightDetailEvent.AddTag -> addTag(event.label, night, content.allTags)
            is NightDetailEvent.SaveNote -> viewModelScope.launch {
                nightRepository.setNote(nightId, event.note)
                message.value = R.string.note_saved
            }
            NightDetailEvent.MessageShown -> message.value = null
        }
    }

    private fun addTag(label: String, night: Night, allTags: List<Tag>) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            // Gibt es den Tag schon (gleicher Name), wird er wiederverwendet.
            val id = allTags.firstOrNull { it.label.equals(trimmed, ignoreCase = true) }?.id
                ?: tagRepository.addCustomTag(trimmed)
            nightRepository.setTags(nightId, night.tags.map { it.id }.toSet() + id)
        }
    }

    private fun correct(night: Night, onset: Instant?, wake: Instant?) {
        if (onset == null || wake == null || !SleepWindowCorrection.isValid(onset, wake, night)) {
            message.value = R.string.night_window_invalid
            return
        }
        viewModelScope.launch {
            nightRepository.updateSleepWindow(nightId, onset, wake, manual = true)
            summaryUpdater.refresh(nightId)
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
                        val now = clock.instant()
                        withContext(computeDispatcher) {
                            LoadResult.Loaded(
                                data = data,
                                summary = NightSummarizer.summarize(data, ZoneId.systemDefault(), now),
                                timeline = NightTimeline.from(data, now),
                            )
                        }
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
        data class Loaded(val data: NightData, val summary: NightSummary, val timeline: NightTimeline) : LoadResult
    }
}
