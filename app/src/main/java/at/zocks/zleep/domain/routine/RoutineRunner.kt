package at.zocks.zleep.domain.routine

import at.zocks.zleep.domain.device.SockPairProvider
import at.zocks.zleep.domain.heat.HeatController
import at.zocks.zleep.domain.heat.HeatMode
import at.zocks.zleep.domain.heat.HeatRequest
import at.zocks.zleep.domain.massage.BuiltInMassagePrograms
import at.zocks.zleep.domain.massage.MassageController
import at.zocks.zleep.domain.massage.MassageProgram
import at.zocks.zleep.domain.model.ConnectionState
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.domain.repository.MassageProgramRepository
import at.zocks.zleep.domain.sleep.MotionSleepDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant

enum class RoutineEndReason {
    /** Alle Schritte durchlaufen. */
    COMPLETED,

    /** Schlaf erkannt – die Routine hat sich zurückgezogen. */
    FELL_ASLEEP,

    /** Von Hand beendet. */
    STOPPED,
}

sealed interface RoutineState {
    /** Keine Routine aktiv; [lastEnd] sagt, wie die letzte geendet hat (für eine Meldung). */
    data class Idle(val lastEnd: RoutineEndReason? = null) : RoutineState

    data class Running(
        val routine: EveningRoutine,
        val stepIndex: Int,
        val stepEndsAt: Instant,
        val endsAt: Instant,
    ) : RoutineState {
        val step: RoutineStep get() = routine.steps[stepIndex]
    }
}

/**
 * Spielt die Abendroutine Schritt für Schritt ab. Wärme läuft über den [HeatController]
 * (und damit durch den Sicherheitswächter), Massage über den [MassageController].
 * Folgt auf einen Schritt einer mit derselben Art, läuft sie ohne Ausklingen weiter;
 * sonst klingt sie am Schrittende aus. Mit „endet bei Schlaf“ beendet erkannter Schlaf
 * (Bewegung, [MotionSleepDetector]) die Routine sofort und sanft.
 */
class RoutineRunner(
    private val pairProvider: SockPairProvider,
    private val heat: HeatController,
    private val massage: MassageController,
    private val programs: MassageProgramRepository,
    private val scope: CoroutineScope,
    private val clock: Clock,
) {
    private val _state = MutableStateFlow<RoutineState>(RoutineState.Idle())
    val state: StateFlow<RoutineState> = _state.asStateFlow()

    private val lifecycle = Mutex()
    private var job: Job? = null

    /** Startet [routine]; `false`, wenn sie leer ist oder keine Socke verbunden ist. */
    suspend fun start(routine: EveningRoutine): Boolean = lifecycle.withLock {
        val normalized = routine.normalized()
        val pair = pairProvider.pair.value
        val connected = pair.devices(normalized.side).any { it.connectionState.value == ConnectionState.CONNECTED }
        if (normalized.steps.isEmpty() || !connected) return false
        job?.cancelAndJoin()
        job = scope.launch { run(normalized) }
        true
    }

    /** Beendet die laufende Routine; Wärme aus, Massage klingt kurz aus. */
    suspend fun stop() = lifecycle.withLock {
        if (job?.isActive != true) return
        job?.cancelAndJoin()
        job = null
        finish(currentRoutine(), RoutineEndReason.STOPPED)
    }

    private fun currentRoutine(): EveningRoutine? = (_state.value as? RoutineState.Running)?.routine

    private suspend fun run(routine: EveningRoutine) {
        val sleepWatch = if (routine.endWhenAsleep) scope.launch { watchSleep() } else null
        try {
            val steps = routine.steps
            steps.forEachIndexed { index, step ->
                val now = clock.instant()
                val duration = minutes(step.durationMinutes)
                val endsAt = now.plus(minutes(steps.drop(index).sumOf { it.durationMinutes }))
                _state.value = RoutineState.Running(routine, index, now.plus(duration), endsAt)
                apply(routine, step, next = steps.getOrNull(index + 1))
                delay(duration.toMillis())
            }
            finish(routine, RoutineEndReason.COMPLETED)
        } finally {
            sleepWatch?.cancel()
        }
    }

    private suspend fun apply(routine: EveningRoutine, step: RoutineStep, next: RoutineStep?) {
        val side = routine.side
        // Läuft der nächste Schritt mit derselben Art weiter, eine Minute Puffer, damit nichts
        // vorher ausgeht; der nächste Schritt setzt die Werte dann neu.
        val heatPlan = step.heat
        if (heatPlan != null) {
            val extra = if (next?.heat != null) 1 else 0
            heat.start(
                HeatRequest(side = side, mode = HeatMode.LEVEL, level = heatPlan.level, duration = minutes(step.durationMinutes + extra)),
            )
        } else if (heat.state.value.isHeating) {
            heat.stop(side)
        }
        val massagePlan = step.massage
        if (massagePlan != null) {
            val extra = if (next?.massage != null) 1 else 0
            massage.start(program(massagePlan.programId), massagePlan.intensity, step.durationMinutes + extra, side)
        } else if (massage.state.value.session != null) {
            massage.stop(soft = true)
        }
    }

    private suspend fun program(id: String): MassageProgram =
        BuiltInMassagePrograms.byId(id)
            ?: programs.observeCustomPrograms().first().firstOrNull { it.id == id }
            ?: BuiltInMassagePrograms.RELAX

    /** Wartet auf erkannten Schlaf und beendet dann die Routine (eigene Coroutine, s. u.). */
    private suspend fun watchSleep() {
        val detector = MotionSleepDetector()
        val pair = pairProvider.pair.value
        merge(pair.left.sensorData, pair.right.sensorData).first { detector.onSample(it) }
        // Außerhalb dieser Coroutine: Das Beenden bricht den Ablauf ab, der sie gestartet hat.
        scope.launch {
            lifecycle.withLock {
                val routine = currentRoutine() ?: return@withLock
                job?.cancelAndJoin()
                job = null
                finish(routine, RoutineEndReason.FELL_ASLEEP)
            }
        }
    }

    private suspend fun finish(routine: EveningRoutine?, reason: RoutineEndReason) {
        val side = routine?.side ?: SockSide.BOTH
        if (reason != RoutineEndReason.COMPLETED) {
            if (heat.state.value.isHeating) heat.stop(side)
            if (massage.state.value.session != null) massage.stop(soft = true)
        }
        _state.value = RoutineState.Idle(lastEnd = reason)
    }

    fun consumeEnd() {
        if (_state.value is RoutineState.Idle) _state.value = RoutineState.Idle()
    }

    private fun minutes(value: Int): Duration = Duration.ofMinutes(value.toLong())
}
