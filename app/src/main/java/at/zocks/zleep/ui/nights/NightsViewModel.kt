package at.zocks.zleep.ui.nights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.zocks.zleep.di.DefaultDispatcher
import at.zocks.zleep.domain.analysis.Insight
import at.zocks.zleep.domain.analysis.InsightEngine
import at.zocks.zleep.domain.analysis.NightForInsights
import at.zocks.zleep.domain.analysis.SleepScoreCalculator
import at.zocks.zleep.domain.analysis.TrendCalculator
import at.zocks.zleep.domain.analysis.TrendPeriod
import at.zocks.zleep.domain.analysis.TrendSummary
import at.zocks.zleep.domain.model.Night
import at.zocks.zleep.domain.repository.NightRepository
import at.zocks.zleep.domain.repository.NightSummaryRepository
import at.zocks.zleep.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

enum class NightsView { LIST, CALENDAR, TRENDS }

/** Eine Nacht in Liste und Kalender: Score und Schlafdauer, sobald die Kennzahlen da sind. */
data class NightListItem(val night: Night, val nightDate: LocalDate, val score: Int?, val totalSleep: Duration?)

sealed interface NightsUiState {
    data object Loading : NightsUiState
    data object Empty : NightsUiState
    data object Error : NightsUiState
    data class Content(
        val view: NightsView,
        val items: List<NightListItem>,
        val month: YearMonth,
        val currentMonth: YearMonth,
        /** Nächte des gezeigten Monats nach Datum (bei mehreren pro Tag die längste). */
        val calendar: Map<LocalDate, NightListItem>,
        val trendPeriod: TrendPeriod,
        val trend: TrendSummary,
        val insights: List<Insight>,
        val enoughForInsights: Boolean,
        val sleepGoal: Duration,
        val use24HourClock: Boolean,
    ) : NightsUiState
}

sealed interface NightsEvent {
    data class SelectView(val view: NightsView) : NightsEvent
    data object PreviousMonth : NightsEvent
    data object NextMonth : NightsEvent
    data class SelectPeriod(val period: TrendPeriod) : NightsEvent
}

@HiltViewModel
class NightsViewModel @Inject constructor(
    nightRepository: NightRepository,
    summaryRepository: NightSummaryRepository,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
    @DefaultDispatcher computeDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private data class Selection(
        val view: NightsView = NightsView.LIST,
        val month: YearMonth? = null,
        val period: TrendPeriod = TrendPeriod.WEEK,
    )

    private val selection = MutableStateFlow(Selection())

    val uiState: StateFlow<NightsUiState> = combine(
        nightRepository.observeNights(),
        summaryRepository.observeSummaries(),
        settingsRepository.settings,
        selection,
    ) { nights, summaries, settings, sel ->
        if (nights.isEmpty()) return@combine NightsUiState.Empty
        val zone = clock.zone
        val goal = Duration.ofMinutes(settings.sleepGoalMinutes.toLong())
        val byId = summaries.associateBy { it.nightId }
        val items = nights.map { night ->
            val summary = byId[night.id]
            NightListItem(
                night = night,
                nightDate = summary?.nightDate ?: night.nightOf(zone),
                score = summary?.let { SleepScoreCalculator.calculate(it, goal)?.value },
                totalSleep = summary?.totalSleep,
            )
        }
        val today = LocalDate.now(clock)
        val currentMonth = YearMonth.from(today)
        val month = sel.month ?: currentMonth
        val usable = nights.mapNotNull { night -> byId[night.id]?.let { NightForInsights(it, night.tags) } }
        NightsUiState.Content(
            view = sel.view,
            items = items,
            month = month,
            currentMonth = currentMonth,
            calendar = items.filter { YearMonth.from(it.nightDate) == month }
                .groupBy { it.nightDate }
                .mapValues { (_, list) -> list.maxBy { it.totalSleep ?: Duration.ZERO } },
            trendPeriod = sel.period,
            trend = TrendCalculator.calculate(summaries, sel.period, today, goal, zone),
            insights = InsightEngine.find(usable, goal),
            enoughForInsights = InsightEngine.hasEnoughData(usable),
            sleepGoal = goal,
            use24HourClock = settings.use24HourClock,
        )
    }
        .flowOn(computeDispatcher)
        .catch { emit(NightsUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NightsUiState.Loading)

    fun onEvent(event: NightsEvent) {
        val current = (uiState.value as? NightsUiState.Content)?.month ?: YearMonth.now(clock)
        when (event) {
            is NightsEvent.SelectView -> selection.update { it.copy(view = event.view) }
            NightsEvent.PreviousMonth -> selection.update { it.copy(month = current.minusMonths(1)) }
            NightsEvent.NextMonth -> selection.update {
                it.copy(month = current.plusMonths(1).coerceAtMost(YearMonth.now(clock)))
            }
            is NightsEvent.SelectPeriod -> selection.update { it.copy(period = event.period) }
        }
    }
}
