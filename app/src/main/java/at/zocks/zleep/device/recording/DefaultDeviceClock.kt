package at.zocks.zleep.device.recording

import at.zocks.zleep.device.simulator.SimulatedSockDevice
import at.zocks.zleep.device.simulator.SimulationEngine
import at.zocks.zleep.domain.device.SockPairProvider
import at.zocks.zleep.domain.recording.DeviceClock
import at.zocks.zleep.domain.simulator.SimulationMode
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Echtzeit – außer der Simulator spielt gerade eine Nacht im Zeitraffer ab. */
@Singleton
class DefaultDeviceClock @Inject constructor(
    private val pairProvider: SockPairProvider,
    private val engine: SimulationEngine,
    private val clock: Clock,
) : DeviceClock {

    override val isSimulated: Boolean
        get() = pairProvider.pair.value.left is SimulatedSockDevice

    override fun now(): Instant {
        val simulation = engine.state.value
        return if (isSimulated && simulation.mode != SimulationMode.AWAKE) simulation.simulatedTime else clock.instant()
    }
}
