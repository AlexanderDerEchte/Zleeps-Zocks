package at.zocks.zleep.domain.massage

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
class MassageControllerTest {

    private val left = FakeSockDevice(SockSide.LEFT, FullCapabilities)
    private val right = FakeSockDevice(SockSide.RIGHT, FullCapabilities)

    private suspend fun TestScope.controller(connect: Boolean = true): MassageController {
        if (connect) {
            left.connect()
            right.connect()
        }
        return MassageController(FakePairProvider(left, right), backgroundScope, SchedulerClock(testScheduler))
    }

    @Test
    fun `starts on both socks with a pattern per side`() = runTest {
        val controller = controller()
        assertThat(controller.start(BuiltInMassagePrograms.RECOVERY, intensity = 70, durationMinutes = 10)).isTrue()
        assertThat(left.massageState.value.intensity).isEqualTo(70)
        assertThat(left.massageCommands.single().pattern).isNotEqualTo(right.massageCommands.single().pattern)
        val session = controller.state.value.session!!
        assertThat(Duration.between(session.startedAt, session.endsAt)).isEqualTo(Duration.ofMinutes(10))
    }

    @Test
    fun `nothing happens without connected socks`() = runTest {
        val controller = controller(connect = false)
        assertThat(controller.start(BuiltInMassagePrograms.WAVE, 50, 10)).isFalse()
        assertThat(controller.state.value.session).isNull()
    }

    @Test
    fun `skips a disconnected sock and reports it`() = runTest {
        val controller = controller()
        right.disconnect()
        controller.start(BuiltInMassagePrograms.WAVE, 50, 10)
        assertThat(left.massageState.value.active).isTrue()
        assertThat(controller.state.value.skipped).containsExactly(SockSide.RIGHT)
    }

    @Test
    fun `fades out softly at the end of the timer`() = runTest {
        val controller = controller()
        controller.start(BuiltInMassagePrograms.WAVE, 60, durationMinutes = 10)
        advanceTimeBy(Duration.ofMinutes(10).minus(MassageController.DEFAULT_FADE).toMillis() + 1)
        runCurrent()
        assertThat(controller.state.value.session!!.fadingOut).isTrue()
        assertThat(left.commands.last()).isEqualTo("stopMassage ${MassageController.DEFAULT_FADE.toMillis()}")

        advanceTimeBy(MassageController.DEFAULT_FADE.toMillis())
        runCurrent()
        assertThat(controller.state.value.session).isNull()
    }

    @Test
    fun `sleep mode fades out over a long time`() {
        val fade = MassageController.fadeFor(BuiltInMassagePrograms.SLEEP, Duration.ofMinutes(10))
        assertThat(fade).isEqualTo(Duration.ofMinutes(5))
        assertThat(MassageController.fadeFor(BuiltInMassagePrograms.SLEEP, Duration.ofMinutes(4))).isEqualTo(Duration.ofMinutes(2))
        assertThat(MassageController.fadeFor(BuiltInMassagePrograms.WAVE, Duration.ofMinutes(1))).isEqualTo(Duration.ofSeconds(15))
    }

    @Test
    fun `intensity can be changed while running and is bounded`() = runTest {
        val controller = controller()
        controller.start(BuiltInMassagePrograms.KNEAD, 50, 15)
        controller.setIntensity(5)
        assertThat(left.massageState.value.intensity).isEqualTo(MassageController.MIN_INTENSITY)
        assertThat(controller.state.value.session!!.intensity).isEqualTo(MassageController.MIN_INTENSITY)
    }

    @Test
    fun `manual stop fades briefly, hard stop ends at once`() = runTest {
        val controller = controller()
        controller.start(BuiltInMassagePrograms.PULSE, 60, 10)
        controller.stop(soft = true)
        runCurrent()
        assertThat(controller.state.value.session!!.fadingOut).isTrue()
        advanceTimeBy(MassageController.MANUAL_FADE.toMillis() + 1)
        runCurrent()
        assertThat(controller.state.value.session).isNull()

        controller.start(BuiltInMassagePrograms.PULSE, 60, 10)
        controller.stop(soft = false)
        assertThat(controller.state.value.session).isNull()
        assertThat(left.massageState.value.active).isFalse()
    }
}
