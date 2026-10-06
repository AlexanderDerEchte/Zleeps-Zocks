package at.zocks.zleep.domain.simulator

import at.zocks.zleep.domain.model.SleepStage
import at.zocks.zleep.domain.model.SockSide
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

enum class SimulationMode {
    /** Wacher Mensch in Echtzeit, z. B. abends auf dem Sofa. */
    AWAKE,

    /** Eine Nacht wird im Zeitraffer abgespielt. */
    NIGHT,

    /** Die abgespielte Nacht ist zu Ende. */
    FINISHED,
}

data class SimulatorState(
    val mode: SimulationMode,
    val simulatedTime: Instant,
    val speed: Int,
    /** Wahre Schlafphase des simulierten Menschen (nur im Simulator bekannt). */
    val trueStage: SleepStage?,
    /** Fortschritt der Nacht 0..1. */
    val progress: Float,
)

/** Steuerung des Simulators für die Entwickleroptionen. */
interface SimulatorController {
    val state: StateFlow<SimulatorState>

    /** Spielt eine neue, zufällige Nacht mit [speed]-facher Geschwindigkeit ab. */
    fun playNight(speed: Int)

    /** Zurück in den Wach-Modus in Echtzeit. */
    fun stopNight()

    /** Simuliert einen Verbindungsabbruch der Socke [side] für [seconds] Sekunden (Echtzeit). */
    fun simulateDropout(side: SockSide, seconds: Int)

    /** Lässt den Temperatursensor der Socke [side] einmal einen unplausiblen Wert liefern. */
    fun simulateSensorFault(side: SockSide)
}

data class DemoDataStatus(
    val demoNightCount: Int,
    val loading: Boolean,
    val progress: Float,
)

/** Lädt bzw. entfernt den Demo-Datensatz (30 Nächte). */
interface DemoDataController {
    val status: StateFlow<DemoDataStatus>
    suspend fun loadDemoNights()
    suspend fun clearDemoNights()
}
