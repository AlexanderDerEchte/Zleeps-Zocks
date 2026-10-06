package at.zocks.zleep.device.simulator

import app.cash.turbine.test
import at.zocks.zleep.domain.model.ConnectionState
import at.zocks.zleep.domain.model.HeatCommand
import at.zocks.zleep.domain.model.MassageCommand
import at.zocks.zleep.domain.model.MassagePattern
import at.zocks.zleep.domain.model.MassageState
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.testing.MutableClock
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class SimulatedSockDeviceTest {

    private val clock = MutableClock()

    private fun TestScope.setUp(side: SockSide = SockSide.LEFT, battery: Int = 92): Pair<SimulationEngine, SimulatedSockDevice> {
        val engine = SimulationEngine(backgroundScope, clock, NightScenarioGenerator())
        val device = SimulatedSockDevice(side, engine, backgroundScope, seed = 1, connectDelayMs = 100, initialBattery = battery)
        return engine to device
    }

    private val pattern = MassagePattern("test", emptyList())

    @Test
    fun `connects and delivers plausible samples`() = runTest {
        val (_, device) = setUp()
        assertThat(device.connectionState.value).isEqualTo(ConnectionState.DISCONNECTED)
        device.connect()
        assertThat(device.connectionState.value).isEqualTo(ConnectionState.CONNECTED)
        assertThat(device.batteryPercent.value).isEqualTo(92)

        device.sensorData.test {
            val sample = awaitItem()
            assertThat(sample.side).isEqualTo(SockSide.LEFT)
            assertThat(sample.heartRateBpm).isIn(Range.closed(45, 110))
            assertThat(sample.skinTemperatureC).isIn(Range.closed(28.0, 34.0))
            assertThat(sample.spo2Percent).isIn(Range.closed(90, 100))
            assertThat(sample.heaterTemperatureC).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `commands require a connection`() = runTest {
        val (_, device) = setUp()
        val result = runCatching { device.setHeat(HeatCommand(2, null)) }
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `heating warms the foot towards the target`() = runTest {
        val (_, device) = setUp()
        device.connect()
        val before = device.sensorData.take(3).toList().map { it.skinTemperatureC!! }.average()

        device.setHeat(HeatCommand(level = 3, targetTemperatureC = 38.0))
        assertThat(device.heatState.value.active).isTrue()
        val heated = device.sensorData.take(240).toList() // 20 Minuten simuliert
        val after = heated.takeLast(10).map { it.skinTemperatureC!! }.average()

        assertThat(after - before).isGreaterThan(3.0)
        assertThat(heated.last().heaterTemperatureC).isWithin(0.5).of(38.0)

        device.stopHeat()
        val cooled = device.sensorData.take(240).toList().takeLast(10).map { it.skinTemperatureC!! }.average()
        assertThat(cooled).isLessThan(after - 2.0)
    }

    @Test
    fun `massage fades out softly`() = runTest {
        val (_, device) = setUp()
        device.connect()
        device.startMassage(MassageCommand(pattern, intensity = 80))
        assertThat(device.massageState.value).isEqualTo(MassageState(true, "test", 80))

        device.stopMassage(fadeOutMs = 1_000)
        advanceTimeBy(500)
        runCurrent()
        val midway = device.massageState.value
        assertThat(midway.active).isTrue()
        assertThat(midway.intensity).isLessThan(80)

        advanceTimeBy(600)
        runCurrent()
        assertThat(device.massageState.value).isEqualTo(MassageState.Off)
    }

    @Test
    fun `heating drains the battery faster`() = runTest {
        val (_, device) = setUp()
        device.connect()
        device.setHeat(HeatCommand(level = 5, targetTemperatureC = null))
        device.sensorData.take(720).toList() // 1 Stunde simuliert
        // 1,5 %/h Grundverbrauch + 5 × 3 %/h Heizen
        assertThat(device.batteryPercent.value).isIn(Range.closed(75, 77))
    }

    @Test
    fun `dropout interrupts samples and reconnects`() = runTest {
        val (engine, device) = setUp(SockSide.RIGHT)
        device.connect()
        device.sensorData.test {
            awaitItem()
            engine.simulateDropout(SockSide.RIGHT, seconds = 30)
            advanceTimeBy(Duration.ofSeconds(11).toMillis())
            runCurrent()
            assertThat(device.connectionState.value).isEqualTo(ConnectionState.RECONNECTING)
            expectNoEvents()

            clock.advance(Duration.ofSeconds(31))
            val sample = awaitItem()
            assertThat(sample.side).isEqualTo(SockSide.RIGHT)
            assertThat(device.connectionState.value).isEqualTo(ConnectionState.CONNECTED)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `disconnect stops heating and massage`() = runTest {
        val (_, device) = setUp()
        device.connect()
        device.setHeat(HeatCommand(2, null))
        device.startMassage(MassageCommand(pattern, 50))
        device.disconnect()
        assertThat(device.connectionState.value).isEqualTo(ConnectionState.DISCONNECTED)
        assertThat(device.heatState.value.active).isFalse()
        assertThat(device.massageState.value.active).isFalse()
    }
}
