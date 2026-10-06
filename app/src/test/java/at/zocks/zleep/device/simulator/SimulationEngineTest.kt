package at.zocks.zleep.device.simulator

import at.zocks.zleep.domain.model.SleepStage
import at.zocks.zleep.domain.simulator.SimulationMode
import at.zocks.zleep.testing.MutableClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class SimulationEngineTest {

    private val clock = MutableClock()

    @Test
    fun `awake mode follows real time`() = runTest {
        val engine = SimulationEngine(backgroundScope, clock, NightScenarioGenerator())
        val tick = engine.ticks.first()
        assertThat(tick.time).isEqualTo(clock.now)
        assertThat(tick.physiology.stage).isEqualTo(SleepStage.AWAKE)
        assertThat(engine.state.value.mode).isEqualTo(SimulationMode.AWAKE)
    }

    @Test
    fun `playing a night runs through all stages in time lapse and finishes`() = runTest {
        val engine = SimulationEngine(backgroundScope, clock, NightScenarioGenerator())
        engine.playNight(speed = 3600)
        val start = engine.state.value.simulatedTime
        assertThat(start).isEqualTo(clock.now)

        val ticks = engine.ticks.takeWhile { engine.state.value.mode == SimulationMode.NIGHT }.toList()

        assertThat(engine.state.value.mode).isEqualTo(SimulationMode.FINISHED)
        assertThat(engine.state.value.progress).isEqualTo(1f)
        // Der Zeitraffer läuft wach weiter, bis gestoppt wird.
        assertThat(engine.state.value.speed).isEqualTo(3600)
        assertThat(ticks.map { it.physiology.stage }.toSet()).containsExactlyElementsIn(SleepStage.entries)
        // Gleichmäßige Schritte von 5 s simulierter Zeit
        ticks.zipWithNext().forEach { (a, b) -> assertThat(Duration.between(a.time, b.time)).isEqualTo(SimulationEngine.STEP) }
        // Eine Nacht dauert mehrere Stunden simuliert, aber nur Sekunden (virtuell) bei 3600×.
        assertThat(Duration.between(start, ticks.last().time).toHours()).isAtLeast(6)
        assertThat(testScheduler.currentTime).isLessThan(Duration.ofMinutes(1).toMillis())
    }

    @Test
    fun `stopNight returns to real time`() = runTest {
        val engine = SimulationEngine(backgroundScope, clock, NightScenarioGenerator())
        engine.playNight(speed = 60)
        engine.stopNight()
        assertThat(engine.state.value.mode).isEqualTo(SimulationMode.AWAKE)
        assertThat(engine.ticks.first().time).isEqualTo(clock.now)
    }
}
