package at.zocks.zleep.device.simulator

import at.zocks.zleep.di.ApplicationScope
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.domain.simulator.SimulationMode
import at.zocks.zleep.domain.simulator.SimulatorController
import at.zocks.zleep.domain.simulator.SimulatorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** Ein Simulationsschritt: simulierte Zeit, Schrittweite und Physiologie des Menschen. */
data class SimTick(
    val time: Instant,
    val step: Duration,
    val physiology: Physiology,
)

/**
 * Taktgeber des Simulators. Im Wach-Modus läuft er in Echtzeit, beim Abspielen einer
 * Nacht im Zeitraffer (ein Schritt = [STEP] simulierte Zeit, Wartezeit = STEP / speed).
 * Beide simulierten Socken hängen am selben Takt, weil sie denselben Menschen messen.
 */
@Singleton
class SimulationEngine @Inject constructor(
    @param:ApplicationScope private val scope: CoroutineScope,
    private val clock: Clock,
    private val generator: NightScenarioGenerator,
) : SimulatorController {

    private val random = Random(clock.millis())
    private val awakeProfile = NightProfile()
    private var scenario: NightScenario? = null

    private val _state = MutableStateFlow(
        SimulatorState(SimulationMode.AWAKE, clock.instant(), speed = 1, trueStage = null, progress = 0f),
    )
    override val state: StateFlow<SimulatorState> = _state.asStateFlow()

    private val _dropouts = MutableStateFlow<Map<SockSide, Instant>>(emptyMap())

    /** Bis wann (Echtzeit) die Verbindung einer Socke unterbrochen ist. */
    val dropouts: StateFlow<Map<SockSide, Instant>> = _dropouts.asStateFlow()

    /** Läuft nur, solange jemand zuhört (z. B. eine verbundene simulierte Socke). */
    val ticks: SharedFlow<SimTick> = flow {
        while (true) {
            val current = _state.value
            // Nach der Nacht läuft der Zeitraffer weiter (wach), bis „Stoppen“ gedrückt wird.
            val speed = if (current.mode == SimulationMode.AWAKE) 1 else current.speed
            delay((STEP.toMillis() / speed).coerceAtLeast(1))
            emit(advance())
        }
    }.shareIn(scope, SharingStarted.WhileSubscribed())

    override fun playNight(speed: Int) {
        // Die Nacht beginnt jetzt, damit eine laufende Aufzeichnung lückenlos weiterläuft.
        val start = clock.instant().truncatedTo(ChronoUnit.SECONDS)
        val newScenario = generator.generate(start, NightProfile(), Random(random.nextLong()))
        scenario = newScenario
        _state.value = SimulatorState(SimulationMode.NIGHT, start, speed.coerceIn(1, MAX_SPEED), null, 0f)
    }

    override fun stopNight() {
        scenario = null
        _state.value = SimulatorState(SimulationMode.AWAKE, clock.instant(), 1, null, 0f)
    }

    override fun simulateDropout(side: SockSide, seconds: Int) {
        val until = clock.instant().plusSeconds(seconds.toLong())
        _dropouts.update { current -> current + side.feet.associateWith { until } }
    }

    private val pendingFaults = mutableSetOf<SockSide>()

    override fun simulateSensorFault(side: SockSide) {
        synchronized(pendingFaults) { pendingFaults += side.feet }
    }

    /** Liefert `true` genau einmal nach [simulateSensorFault]. */
    fun consumeSensorFault(side: SockSide): Boolean = synchronized(pendingFaults) { pendingFaults.remove(side) }

    fun isDroppedOut(side: SockSide): Boolean =
        _dropouts.value[side]?.let { clock.instant().isBefore(it) } == true

    private fun advance(): SimTick {
        val current = _state.value
        val night = scenario
        return when {
            current.mode == SimulationMode.NIGHT && night != null -> {
                val time = current.simulatedTime.plus(STEP)
                val physiology = night.epochAt(time)
                if (physiology == null) {
                    _state.value = current.copy(mode = SimulationMode.FINISHED, simulatedTime = time, trueStage = null, progress = 1f)
                    SimTick(time, STEP, generator.awake(awakeProfile, random))
                } else {
                    val progress = Duration.between(night.start, time).toMillis().toFloat() /
                        Duration.between(night.start, night.end).toMillis()
                    _state.value = current.copy(simulatedTime = time, trueStage = physiology.stage, progress = progress)
                    SimTick(time, STEP, physiology)
                }
            }
            current.mode == SimulationMode.FINISHED -> {
                val time = current.simulatedTime.plus(STEP)
                _state.value = current.copy(simulatedTime = time)
                SimTick(time, STEP, generator.awake(awakeProfile, random))
            }
            else -> {
                val time = clock.instant()
                _state.value = current.copy(simulatedTime = time)
                SimTick(time, STEP, generator.awake(awakeProfile, random))
            }
        }
    }

    companion object {
        /** Simulierte Zeit zwischen zwei Messpunkten. */
        val STEP: Duration = Duration.ofSeconds(5)
        const val MAX_SPEED = 3600
    }
}
