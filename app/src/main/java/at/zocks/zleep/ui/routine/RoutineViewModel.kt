package at.zocks.zleep.ui.routine

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.zocks.zleep.R
import at.zocks.zleep.domain.device.SockPairProvider
import at.zocks.zleep.domain.massage.BuiltInMassagePrograms
import at.zocks.zleep.domain.massage.MassageProgram
import at.zocks.zleep.domain.repository.MassageProgramRepository
import at.zocks.zleep.domain.repository.SettingsRepository
import at.zocks.zleep.domain.routine.EveningRoutine
import at.zocks.zleep.domain.routine.RoutineEndReason
import at.zocks.zleep.domain.routine.RoutineHeat
import at.zocks.zleep.domain.routine.RoutineMassage
import at.zocks.zleep.domain.routine.RoutineRunner
import at.zocks.zleep.domain.routine.RoutineState
import at.zocks.zleep.domain.routine.RoutineStep
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

data class RoutineUiState(
    val loading: Boolean = true,
    val routine: EveningRoutine = EveningRoutine.Default,
    val programs: List<MassageProgram> = BuiltInMassagePrograms.all,
    val running: RoutineState.Running? = null,
    /** Aktuelle Zeit für die Restzeit (nur während die Routine läuft). */
    val now: Instant? = null,
    val connected: Boolean = false,
    val withNight: Boolean = false,
    val use24HourClock: Boolean = true,
    @param:StringRes val userMessage: Int? = null,
)

sealed interface RoutineEvent {
    data class ChangeStep(val index: Int, val step: RoutineStep) : RoutineEvent
    data object AddStep : RoutineEvent
    data class RemoveStep(val index: Int) : RoutineEvent
    data class MoveStep(val index: Int, val by: Int) : RoutineEvent
    data class SetEndWhenAsleep(val enabled: Boolean) : RoutineEvent
    data class SetWithNight(val enabled: Boolean) : RoutineEvent
    data object Reset : RoutineEvent
    data object Start : RoutineEvent
    data object Stop : RoutineEvent
    data object MessageShown : RoutineEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RoutineViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val runner: RoutineRunner,
    pairProvider: SockPairProvider,
    programRepository: MassageProgramRepository,
    private val clock: Clock,
) : ViewModel() {

    private val message = MutableStateFlow<Int?>(null)

    /** Sekundentakt nur, solange eine Routine läuft. */
    private val ticker = runner.state.map { it is RoutineState.Running }.flatMapLatest { running ->
        if (!running) {
            flowOf<Instant?>(null)
        } else {
            flow<Instant?> {
                while (true) {
                    emit(clock.instant())
                    delay(1_000)
                }
            }
        }
    }

    private val connected = pairProvider.pair.flatMapLatest { it.status }.map { it.anyConnected }

    val uiState: StateFlow<RoutineUiState> = combine(
        settingsRepository.settings,
        programRepository.observeCustomPrograms(),
        combine(runner.state, ticker) { state, now -> state to now },
        connected,
        message,
    ) { settings, custom, (state, now), isConnected, userMessage ->
        RoutineUiState(
            loading = false,
            routine = settings.routine,
            programs = BuiltInMassagePrograms.all + custom,
            running = state as? RoutineState.Running,
            now = now,
            connected = isConnected,
            withNight = settings.routineWithNight,
            use24HourClock = settings.use24HourClock,
            userMessage = userMessage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutineUiState())

    init {
        // Ende der Routine kurz melden (auch wenn sie im Hintergrund endete).
        viewModelScope.launch {
            runner.state.collect { state ->
                val end = (state as? RoutineState.Idle)?.lastEnd ?: return@collect
                when (end) {
                    RoutineEndReason.COMPLETED -> message.value = R.string.routine_ended_completed
                    RoutineEndReason.FELL_ASLEEP -> message.value = R.string.routine_ended_asleep
                    RoutineEndReason.STOPPED -> Unit
                }
                runner.consumeEnd()
            }
        }
    }

    fun onEvent(event: RoutineEvent) {
        when (event) {
            is RoutineEvent.ChangeStep -> edit { r ->
                r.copy(steps = r.steps.mapIndexed { i, step -> if (i == event.index) event.step else step })
            }
            RoutineEvent.AddStep -> edit { r ->
                if (r.steps.size >= EveningRoutine.MAX_STEPS) r else r.copy(steps = r.steps + NEW_STEP)
            }
            is RoutineEvent.RemoveStep -> edit { r -> r.copy(steps = r.steps.filterIndexed { i, _ -> i != event.index }) }
            is RoutineEvent.MoveStep -> edit { r ->
                val target = event.index + event.by
                if (target !in r.steps.indices) {
                    r
                } else {
                    r.copy(steps = r.steps.toMutableList().apply { add(target, removeAt(event.index)) })
                }
            }
            is RoutineEvent.SetEndWhenAsleep -> edit { it.copy(endWhenAsleep = event.enabled) }
            is RoutineEvent.SetWithNight -> viewModelScope.launch { settingsRepository.update { it.copy(routineWithNight = event.enabled) } }
            RoutineEvent.Reset -> edit { EveningRoutine.Default }
            RoutineEvent.Start -> viewModelScope.launch {
                val routine = settingsRepository.settings.first().routine
                if (!runner.start(routine)) message.value = R.string.snackbar_pair_first
            }
            RoutineEvent.Stop -> viewModelScope.launch { runner.stop() }
            RoutineEvent.MessageShown -> message.value = null
        }
    }

    private fun edit(transform: (EveningRoutine) -> EveningRoutine) = viewModelScope.launch {
        settingsRepository.update { it.copy(routine = transform(it.routine).normalized()) }
    }

    companion object {
        val NEW_STEP = RoutineStep(10, heat = RoutineHeat(2), massage = RoutineMassage(BuiltInMassagePrograms.RELAX.id, 40))
    }
}
