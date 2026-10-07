package at.zocks.zleep.ui.onboarding

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.zocks.zleep.R
import at.zocks.zleep.domain.device.PairStatus
import at.zocks.zleep.domain.device.SockPairProvider
import at.zocks.zleep.domain.model.DeviceMode
import at.zocks.zleep.domain.model.UserSettings
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
import java.time.Duration
import java.time.LocalTime
import javax.inject.Inject

enum class OnboardingStep { WELCOME, PAIR, TIMES }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val settings: UserSettings = UserSettings(),
    val pairStatus: PairStatus? = null,
    @param:StringRes val userMessage: Int? = null,
)

sealed interface OnboardingEvent {
    data object Next : OnboardingEvent
    data object Back : OnboardingEvent
    data object ConnectSocks : OnboardingEvent
    data class SetBedtime(val time: LocalTime) : OnboardingEvent
    data class SetWakeTime(val time: LocalTime) : OnboardingEvent
    data class SetSleepGoal(val minutes: Int) : OnboardingEvent
    data class SetAlarm(val enabled: Boolean) : OnboardingEvent
    data object Finish : OnboardingEvent
    data object MessageShown : OnboardingEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val pairProvider: SockPairProvider,
) : ViewModel() {

    private data class Local(val step: OnboardingStep = OnboardingStep.WELCOME, @param:StringRes val message: Int? = null)

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<OnboardingUiState> = combine(
        local,
        settingsRepository.settings,
        pairProvider.pair.flatMapLatest { it.status },
    ) { localState, settings, status ->
        OnboardingUiState(localState.step, settings, status, localState.message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingUiState())

    fun onEvent(event: OnboardingEvent) {
        when (event) {
            OnboardingEvent.Next -> local.update { it.copy(step = OnboardingStep.entries[(it.step.ordinal + 1).coerceAtMost(OnboardingStep.entries.lastIndex)]) }
            OnboardingEvent.Back -> local.update { it.copy(step = OnboardingStep.entries[(it.step.ordinal - 1).coerceAtLeast(0)]) }
            OnboardingEvent.ConnectSocks -> connect()
            // Mit Schlafens- oder Aufstehzeit wächst das Schlafziel mit (15 Minuten Einschlafzeit abgezogen).
            is OnboardingEvent.SetBedtime -> update { it.copy(bedtime = event.time, sleepGoalMinutes = suggestedGoal(event.time, it.wakeTime)) }
            is OnboardingEvent.SetWakeTime -> update { it.copy(wakeTime = event.time, sleepGoalMinutes = suggestedGoal(it.bedtime, event.time)) }
            is OnboardingEvent.SetSleepGoal -> update { it.copy(sleepGoalMinutes = event.minutes.coerceIn(MIN_GOAL, MAX_GOAL)) }
            is OnboardingEvent.SetAlarm -> update { it.copy(alarm = it.alarm.copy(enabled = event.enabled)) }
            OnboardingEvent.Finish -> update { it.copy(onboardingCompleted = true) }
            OnboardingEvent.MessageShown -> local.update { it.copy(message = null) }
        }
    }

    private fun connect() {
        if (uiState.value.settings.deviceMode == DeviceMode.BLE) {
            local.update { it.copy(message = R.string.message_ble_not_ready) }
            return
        }
        viewModelScope.launch {
            runCatching { pairProvider.pair.value.connect() }.onFailure { local.update { it.copy(message = R.string.error_generic) } }
        }
    }

    private fun update(transform: (UserSettings) -> UserSettings) = viewModelScope.launch { settingsRepository.update(transform) }

    companion object {
        const val MIN_GOAL = 6 * 60
        const val MAX_GOAL = 10 * 60
        const val GOAL_STEP = 15

        /** Zeit im Bett minus 15 Minuten Einschlafen, auf Viertelstunden gerundet, 6–10 Stunden. */
        fun suggestedGoal(bedtime: LocalTime, wakeTime: LocalTime): Int {
            var minutes = Duration.between(bedtime, wakeTime).toMinutes()
            if (minutes <= 0) minutes += 24 * 60
            val goal = ((minutes - 15) / GOAL_STEP) * GOAL_STEP
            return goal.toInt().coerceIn(MIN_GOAL, MAX_GOAL)
        }
    }
}
