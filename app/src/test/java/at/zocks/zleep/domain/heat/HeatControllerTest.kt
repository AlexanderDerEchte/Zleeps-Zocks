package at.zocks.zleep.domain.heat

import at.zocks.zleep.domain.model.ConnectionState
import at.zocks.zleep.domain.model.DeviceCapabilities
import at.zocks.zleep.domain.model.SensorSample
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.testing.FakePairProvider
import at.zocks.zleep.testing.FakeSockDevice
import at.zocks.zleep.testing.FullCapabilities
import at.zocks.zleep.testing.SchedulerClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class HeatControllerTest {

    private val left = FakeSockDevice(SockSide.LEFT, FullCapabilities)
    private val right = FakeSockDevice(SockSide.RIGHT, FullCapabilities)

    private suspend fun TestScope.controller(): Pair<HeatController, SchedulerClock> {
        val clock = SchedulerClock(testScheduler)
        left.connect()
        right.connect()
        val controller = HeatController(FakePairProvider(left, right), backgroundScope, clock)
        runCurrent()
        return controller to clock
    }

    private suspend fun TestScope.emit(device: FakeSockDevice, clock: SchedulerClock, skin: Double?, motion: Double = 0.3) {
        device.sensorData.emit(SensorSample(device.side, clock.instant(), 60, 40.0, 97, skin, motion, null))
        runCurrent()
    }

    @Test
    fun `heats both socks by default and clamps the target`() = runTest {
        val (controller, _) = controller()
        val rejected = controller.start(HeatRequest(mode = HeatMode.TARGET, targetTemperatureC = 45.0))
        assertThat(rejected).isEmpty()
        assertThat(left.heatState.value.targetTemperatureC).isEqualTo(40.0)
        assertThat(right.heatState.value.active).isTrue()
        assertThat(controller.state.value.plans.keys).containsExactly(SockSide.LEFT, SockSide.RIGHT)
    }

    @Test
    fun `single side and level mode`() = runTest {
        val (controller, _) = controller()
        controller.start(HeatRequest(side = SockSide.RIGHT, mode = HeatMode.LEVEL, level = 4))
        assertThat(left.heatState.value.active).isFalse()
        assertThat(right.heatState.value.level).isEqualTo(4)
        assertThat(right.heatState.value.targetTemperatureC).isNull()

        controller.stop(SockSide.RIGHT)
        assertThat(right.heatState.value.active).isFalse()
        assertThat(controller.state.value.isHeating).isFalse()
    }

    @Test
    fun `timer switches off automatically`() = runTest {
        val (controller, _) = controller()
        controller.start(HeatRequest(duration = Duration.ofMinutes(30)))
        advanceTimeBy(Duration.ofMinutes(29).toMillis())
        assertThat(left.heatState.value.active).isTrue()
        advanceTimeBy(Duration.ofMinutes(2).toMillis())
        assertThat(left.heatState.value.active).isFalse()
        assertThat(right.heatState.value.active).isFalse()
        assertThat(controller.state.value.notice).isInstanceOf(HeatNotice.TimerFinished::class.java)
    }

    @Test
    fun `timer is capped at the maximum runtime and followed by a cooldown`() = runTest {
        val (controller, _) = controller()
        controller.start(HeatRequest(side = SockSide.LEFT, duration = Duration.ofHours(3)))
        advanceTimeBy(HeatSafetyGuard.MAX_CONTINUOUS.toMillis() + 1)
        assertThat(left.heatState.value.active).isFalse()
        assertThat(controller.state.value.notice).isEqualTo(HeatNotice.SafetyShutoff(SockSide.LEFT, ShutoffReason.MAX_RUNTIME))

        val rejected = controller.start(HeatRequest(side = SockSide.LEFT))
        assertThat(rejected).containsExactly(SockSide.LEFT, HeatRejection.COOLDOWN)
        assertThat(left.heatState.value.active).isFalse()

        advanceTimeBy(HeatSafetyGuard.COOLDOWN.toMillis())
        assertThat(controller.start(HeatRequest(side = SockSide.LEFT))).isEmpty()
    }

    @Test
    fun `overheating one sock switches off only that sock`() = runTest {
        val (controller, clock) = controller()
        controller.start(HeatRequest())
        emit(left, clock, 36.0)
        advanceTimeBy(20_000)
        emit(left, clock, 42.5)
        assertThat(left.heatState.value.active).isFalse()
        assertThat(right.heatState.value.active).isTrue()
        assertThat(controller.state.value.notice).isEqualTo(HeatNotice.SafetyShutoff(SockSide.LEFT, ShutoffReason.OVER_TEMPERATURE))
    }

    @Test
    fun `implausible sensor values switch off`() = runTest {
        val (controller, clock) = controller()
        controller.start(HeatRequest(side = SockSide.RIGHT))
        emit(right, clock, 33.0)
        advanceTimeBy(5_000)
        emit(right, clock, 55.0)
        assertThat(right.heatState.value.active).isFalse()
        assertThat(controller.state.value.notice).isEqualTo(HeatNotice.SafetyShutoff(SockSide.RIGHT, ShutoffReason.IMPLAUSIBLE_HIGH))
    }

    @Test
    fun `rejects disconnected socks and socks without temperature sensor`() = runTest {
        val (controller, _) = controller()
        right.disconnect()
        assertThat(controller.start(HeatRequest())).containsExactly(SockSide.RIGHT, HeatRejection.NOT_CONNECTED)
        assertThat(left.heatState.value.active).isTrue()

        val blind = FakeSockDevice(SockSide.LEFT, DeviceCapabilities.None).apply { connectionState.value = ConnectionState.CONNECTED }
        val other = FakeSockDevice(SockSide.RIGHT, FullCapabilities).apply { connectionState.value = ConnectionState.CONNECTED }
        val blindController = HeatController(FakePairProvider(blind, other), backgroundScope, SchedulerClock(testScheduler))
        assertThat(blindController.start(HeatRequest(side = SockSide.LEFT)))
            .containsExactly(SockSide.LEFT, HeatRejection.NO_TEMPERATURE_SENSOR)
        assertThat(blind.heatState.value.active).isFalse()
    }

    @Test
    fun `switches off after falling asleep when enabled`() = runTest {
        val (controller, clock) = controller()
        controller.setAutoOffWhenAsleep(true)
        controller.start(HeatRequest(duration = Duration.ofMinutes(60)))
        repeat(130) { // knapp 11 Minuten ruhig liegen
            emit(left, clock, 34.0, motion = 0.01)
            advanceTimeBy(5_000)
        }
        assertThat(left.heatState.value.active).isFalse()
        assertThat(right.heatState.value.active).isFalse()
        assertThat(controller.state.value.notice).isInstanceOf(HeatNotice.FellAsleep::class.java)
    }

    @Test
    fun `keeps heating while asleep when auto off is disabled`() = runTest {
        val (controller, clock) = controller()
        controller.setAutoOffWhenAsleep(false)
        controller.start(HeatRequest(duration = Duration.ofMinutes(60)))
        repeat(130) {
            emit(left, clock, 34.0, motion = 0.01)
            advanceTimeBy(5_000)
        }
        assertThat(left.heatState.value.active).isTrue()
    }
}
