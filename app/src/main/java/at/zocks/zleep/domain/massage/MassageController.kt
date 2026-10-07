package at.zocks.zleep.domain.massage

import at.zocks.zleep.domain.device.SockPairProvider
import at.zocks.zleep.domain.model.ConnectionState
import at.zocks.zleep.domain.model.MassageCommand
import at.zocks.zleep.domain.model.SockSide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant

data class MassageSession(
    val program: MassageProgram,
    val side: SockSide,
    val intensity: Int,
    val startedAt: Instant,
    val endsAt: Instant,
    val fadingOut: Boolean,
)

data class MassageControlState(
    val session: MassageSession? = null,
    /** Socken, die beim Start nicht verbunden waren. */
    val skipped: Set<SockSide> = emptySet(),
)

/**
 * Startet und beendet Massageprogramme. Am Ende klingt die Massage sanft aus:
 * normal über [DEFAULT_FADE], beim Sleep Mode über die halbe Dauer (max. 5 Minuten).
 */
class MassageController(
    private val pairProvider: SockPairProvider,
    private val scope: CoroutineScope,
    private val clock: Clock,
) {
    private val _state = MutableStateFlow(MassageControlState())
    val state: StateFlow<MassageControlState> = _state.asStateFlow()

    private var timer: Job? = null

    /** Startet [program]; liefert `false`, wenn keine der gewählten Socken verbunden ist oder den Befehl annimmt. */
    suspend fun start(program: MassageProgram, intensity: Int, durationMinutes: Int, side: SockSide = SockSide.BOTH): Boolean {
        val pair = pairProvider.pair.value
        val devices = pair.devices(side)
        val connected = devices.filter { it.connectionState.value == ConnectionState.CONNECTED }
        if (connected.isEmpty()) return false

        timer?.cancel()
        val level = intensity.coerceIn(MIN_INTENSITY, 100)
        val started = connected.filter { device ->
            runCatching { device.startMassage(MassageCommand(MassagePatterns.patternFor(program, device.side), level)) }.isSuccess
        }
        if (started.isEmpty()) return false

        val duration = Duration.ofMinutes(durationMinutes.coerceIn(1, MAX_MINUTES).toLong())
        val fade = fadeFor(program, duration)
        val now = clock.instant()
        _state.value = MassageControlState(
            session = MassageSession(program, side, level, now, now.plus(duration), fadingOut = false),
            skipped = (devices - started.toSet()).map { it.side }.toSet(),
        )
        timer = scope.launch {
            delay(duration.minus(fade).toMillis())
            fadeOut(fade)
        }
        return true
    }

    /** Ändert die Intensität einer laufenden Massage. */
    suspend fun setIntensity(intensity: Int) {
        val session = _state.value.session ?: return
        if (session.fadingOut) return
        val level = intensity.coerceIn(MIN_INTENSITY, 100)
        pairProvider.pair.value.devices(session.side)
            .filter { it.connectionState.value == ConnectionState.CONNECTED }
            .forEach { runCatching { it.startMassage(MassageCommand(MassagePatterns.patternFor(session.program, it.side), level)) } }
        _state.update { it.copy(session = session.copy(intensity = level)) }
    }

    /** Beendet die Massage; mit [soft] klingt sie kurz aus. */
    suspend fun stop(soft: Boolean = true) {
        timer?.cancel()
        if (_state.value.session == null) return
        if (soft) {
            timer = scope.launch { fadeOut(MANUAL_FADE) }
        } else {
            pairProvider.pair.value.devices().forEach { runCatching { it.stopMassage(0) } }
            _state.value = MassageControlState()
        }
    }

    private suspend fun fadeOut(fade: Duration) {
        val session = _state.value.session ?: return
        _state.update { it.copy(session = session.copy(fadingOut = true)) }
        pairProvider.pair.value.devices(session.side).forEach { runCatching { it.stopMassage(fade.toMillis()) } }
        delay(fade.toMillis())
        _state.value = MassageControlState()
    }

    companion object {
        const val MIN_INTENSITY = 10
        const val MAX_MINUTES = 60
        val DEFAULT_FADE: Duration = Duration.ofSeconds(30)
        val MANUAL_FADE: Duration = Duration.ofSeconds(3)
        val MAX_SLEEP_FADE: Duration = Duration.ofMinutes(5)

        fun fadeFor(program: MassageProgram, duration: Duration): Duration = when (program.type) {
            MassageProgramType.SLEEP -> minOf(duration.dividedBy(2), MAX_SLEEP_FADE)
            else -> minOf(DEFAULT_FADE, duration.dividedBy(4))
        }
    }
}
