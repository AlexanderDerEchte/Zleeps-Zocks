package at.zocks.zleep.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.zocks.zleep.R
import at.zocks.zleep.domain.alarm.AlarmState
import at.zocks.zleep.domain.alarm.SmartAlarmController
import at.zocks.zleep.domain.analysis.NightSummaryUpdater
import at.zocks.zleep.domain.health.HealthExporter
import at.zocks.zleep.domain.heat.HeatController
import at.zocks.zleep.domain.heat.HeatNotice
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.domain.recording.RecordingLauncher
import at.zocks.zleep.domain.repository.SettingsRepository
import at.zocks.zleep.ui.components.UiText
import at.zocks.zleep.ui.format.rejectionRes
import at.zocks.zleep.ui.format.shutoffReasonRes
import at.zocks.zleep.ui.format.sideLabelRes
import at.zocks.zleep.ui.onboarding.OnboardingPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

/** App-weiter Zustand: deutliche Anzeige beim Heizen und Sicherheitsmeldungen auf jedem Screen. */
data class AppUiState(
    val heatingRemaining: Duration? = null,
    val notice: UiText? = null,
    val alarm: AlarmState = AlarmState.Off,
    /** `null`, solange die Einstellungen noch nicht geladen sind. */
    val showOnboarding: Boolean? = null,
    val use24HourClock: Boolean = true,
) {
    val isHeating: Boolean get() = heatingRemaining != null
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppViewModel @Inject constructor(
    private val heatController: HeatController,
    private val clock: Clock,
    launcher: RecordingLauncher,
    summaryUpdater: NightSummaryUpdater,
    private val smartAlarm: SmartAlarmController,
    healthExporter: HealthExporter,
    private val settingsRepository: SettingsRepository,
    onboardingPolicy: OnboardingPolicy,
) : ViewModel() {

    private val offerOnboarding = onboardingPolicy.offerOnboarding()

    init {
        // Eine offene Nacht (z. B. nach Absturz oder Update) weiter aufzeichnen.
        launcher.resumeIfNeeded()
        // Kennzahlen für Nächte nachtragen, die noch keine haben (z. B. nach einem Update).
        summaryUpdater.start()
        smartAlarm.start()
        healthExporter.start()
    }

    private val ticker = heatController.state.map { it.isHeating }.distinctUntilChanged().flatMapLatest { heating ->
        if (heating) {
            flow {
                while (true) {
                    emit(Unit)
                    delay(TICK_MS)
                }
            }
        } else {
            flowOf(Unit)
        }
    }

    val uiState: StateFlow<AppUiState> = combine(
        heatController.state,
        ticker,
        smartAlarm.state,
        settingsRepository.settings.map { it.onboardingCompleted to it.use24HourClock }.distinctUntilChanged(),
    ) { state, _, alarm, (onboardingDone, use24h) ->
        val now = clock.instant()
        AppUiState(
            heatingRemaining = state.plans.values.maxOfOrNull { Duration.between(now, it.endsAt) }?.coerceAtLeast(Duration.ZERO),
            notice = state.notice?.toUiText(),
            alarm = alarm,
            showOnboarding = offerOnboarding && !onboardingDone,
            use24HourClock = use24h,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState(showOnboarding = if (offerOnboarding) null else false))

    fun dismissAlarm() {
        viewModelScope.launch { smartAlarm.dismiss() }
    }

    fun snoozeAlarm() {
        viewModelScope.launch { smartAlarm.snooze() }
    }

    fun stopHeating() {
        viewModelScope.launch { heatController.stop(SockSide.BOTH) }
    }

    fun noticeShown() = heatController.consumeNotice()

    private companion object {
        const val TICK_MS = 15_000L
    }
}

internal fun HeatNotice.toUiText(): UiText {
    val sideText = UiText.of(sideLabelRes(side))
    return when (this) {
        is HeatNotice.SafetyShutoff -> UiText.of(R.string.heat_notice_safety, sideText, UiText.of(shutoffReasonRes(reason)))
        is HeatNotice.Rejected -> UiText.of(R.string.heat_notice_rejected, sideText, UiText.of(rejectionRes(reason)))
        is HeatNotice.TimerFinished -> UiText.of(R.string.heat_notice_timer)
        is HeatNotice.FellAsleep -> UiText.of(R.string.heat_notice_asleep)
        is HeatNotice.PreheatStarted -> UiText.of(R.string.heat_notice_preheat)
    }
}
