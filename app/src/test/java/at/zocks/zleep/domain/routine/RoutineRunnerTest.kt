package at.zocks.zleep.domain.routine

import at.zocks.zleep.domain.heat.HeatController
import at.zocks.zleep.domain.heat.HeatNotice
import at.zocks.zleep.domain.heat.ShutoffReason
import at.zocks.zleep.domain.massage.BuiltInMassagePrograms
import at.zocks.zleep.domain.massage.MassageController
import at.zocks.zleep.domain.massage.MassagePatterns
import at.zocks.zleep.domain.model.SensorSample
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.testing.FakeMassagePrograms
import at.zocks.zleep.testing.FakePairProvider
import at.zocks.zleep.testing.FakeSockDevice
import at.zocks.zleep.testing.FullCapabilities
import at.zocks.zleep.testing.SchedulerClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineRunnerTest {

    private val left = FakeSockDevice(SockSide.LEFT, FullCapabilities)
    private val right = FakeSockDevice(SockSide.RIGHT, FullCapabilities)

    private class Setup(val runner: RoutineRunner, val heat: HeatController, val massage: MassageController, val clock: SchedulerClock)

    private suspend fun TestScope.setUp(connect: Boolean = true): Setup {
        if (connect) {
            left.connect()
            right.connect()
        }
        val pairs = FakePairProvider(left, right)
        val clock = SchedulerClock(testScheduler)
        val heat = HeatController(pairs, backgroundScope, clock)
        val massage = MassageController(pairs, backgroundScope, clock)
        return Setup(RoutineRunner(pairs, heat, massage, FakeMassagePrograms(), backgroundScope, clock), heat, massage, clock)
    }

    /** [minutes] Minuten Messwerte beider Socken alle 5 s; [motion] klein = ruhig. */
    private suspend fun TestScope.feed(clock: SchedulerClock, minutes: Int, motion: Double) {
        repeat(minutes * 12) {
            testScheduler.advanceTimeBy(5_000)
            listOf(left, right).forEach { it.sensorData.emit(SensorSample(it.side, clock.instant(), 62, 45.0, 97, 33.0, motion, null)) }
            runCurrent()
        }
    }

    private val awake = 0.3
    private val quiet = 0.01

    private val routine = EveningRoutine(
        steps = listOf(
            RoutineStep(5, heat = RoutineHeat(3)),
            RoutineStep(5, heat = RoutineHeat(2), massage = RoutineMassage(BuiltInMassagePrograms.RELAX.id, 40)),
            RoutineStep(5, massage = RoutineMassage(BuiltInMassagePrograms.SLEEP.id, 30)),
        ),
        endWhenAsleep = false,
    )

    @Test
    fun `plays the steps in order and then ends`() = runTest {
        val s = setUp()
        assertThat(s.runner.start(routine)).isTrue()
        runCurrent()
        assertThat((s.runner.state.value as RoutineState.Running).stepIndex).isEqualTo(0)
        assertThat(left.heatState.value.level).isEqualTo(3)
        assertThat(left.massageState.value.active).isFalse()

        feed(s.clock, minutes = 5, motion = awake)
        assertThat((s.runner.state.value as RoutineState.Running).stepIndex).isEqualTo(1)
        assertThat(left.heatState.value.level).isEqualTo(2)
        assertThat(s.massage.state.value.session!!.program).isEqualTo(BuiltInMassagePrograms.RELAX)

        feed(s.clock, minutes = 5, motion = awake)
        val running = s.runner.state.value as RoutineState.Running
        assertThat(running.stepIndex).isEqualTo(2)
        assertThat(left.heatState.value.active).isFalse()
        assertThat(s.massage.state.value.session!!.program).isEqualTo(BuiltInMassagePrograms.SLEEP)
        assertThat(left.massageCommands.last().pattern).isEqualTo(MassagePatterns.patternFor(BuiltInMassagePrograms.SLEEP, SockSide.LEFT))

        feed(s.clock, minutes = 6, motion = awake)
        assertThat(s.runner.state.value).isEqualTo(RoutineState.Idle(RoutineEndReason.COMPLETED))
        assertThat(left.massageState.value.active).isFalse()
    }

    @Test
    fun `ends gently as soon as sleep is detected`() = runTest {
        val s = setUp()
        s.runner.start(routine.copy(steps = listOf(RoutineStep(60, heat = RoutineHeat(2), massage = RoutineMassage(BuiltInMassagePrograms.RELAX.id, 40))), endWhenAsleep = true))
        feed(s.clock, minutes = 5, motion = awake)
        assertThat(s.runner.state.value).isInstanceOf(RoutineState.Running::class.java)

        feed(s.clock, minutes = 12, motion = quiet)
        assertThat(s.runner.state.value).isEqualTo(RoutineState.Idle(RoutineEndReason.FELL_ASLEEP))
        assertThat(left.heatState.value.active).isFalse()
        assertThat(s.massage.state.value.session).isNull()
    }

    @Test
    fun `without the option sleep does not end the routine`() = runTest {
        val s = setUp()
        s.runner.start(routine.copy(steps = listOf(RoutineStep(30, massage = RoutineMassage(BuiltInMassagePrograms.RELAX.id, 40)))))
        feed(s.clock, minutes = 20, motion = quiet)
        assertThat(s.runner.state.value).isInstanceOf(RoutineState.Running::class.java)
    }

    @Test
    fun `stopping by hand switches everything off`() = runTest {
        val s = setUp()
        s.runner.start(routine)
        runCurrent()
        assertThat(s.heat.state.value.isHeating).isTrue()
        feed(s.clock, minutes = 6, motion = awake)
        s.runner.stop()
        runCurrent()
        assertThat(s.runner.state.value).isEqualTo(RoutineState.Idle(RoutineEndReason.STOPPED))
        assertThat(left.heatState.value.active).isFalse()
        assertThat(s.heat.state.value.isHeating).isFalse()
        s.runner.consumeEnd()
        assertThat(s.runner.state.value).isEqualTo(RoutineState.Idle())
    }

    @Test
    fun `refuses to start without socks or steps`() = runTest {
        val s = setUp(connect = false)
        assertThat(s.runner.start(routine)).isFalse()
        left.connect()
        assertThat(s.runner.start(routine.copy(steps = emptyList()))).isFalse()
        assertThat(s.runner.start(routine)).isTrue()
    }

    @Test
    fun `consecutive heat steps do not reset the safety limit`() = runTest {
        val s = setUp()
        s.runner.start(EveningRoutine(listOf(RoutineStep(60, heat = RoutineHeat(2)), RoutineStep(60, heat = RoutineHeat(3))), endWhenAsleep = false))
        // Knapp unter 90 Minuten Heizen: läuft noch (zweiter Schritt mit Stufe 3).
        feed(s.clock, minutes = 89, motion = awake)
        assertThat(left.heatState.value.level).isEqualTo(3)
        // Knapp darüber: Sicherheitsabschaltung, obwohl der Schritt noch 30 Minuten hätte.
        feed(s.clock, minutes = 2, motion = awake)
        assertThat(left.heatState.value.active).isFalse()
        val notice = s.heat.state.value.notice
        assertThat(notice).isInstanceOf(HeatNotice.SafetyShutoff::class.java)
        assertThat((notice as HeatNotice.SafetyShutoff).reason).isEqualTo(ShutoffReason.MAX_RUNTIME)
        assertThat(s.runner.state.value).isInstanceOf(RoutineState.Running::class.java)
    }

    @Test
    fun `normalizing keeps steps within limits`() {
        val wild = EveningRoutine(
            List(12) { RoutineStep(500, heat = RoutineHeat(9), massage = RoutineMassage("builtin:wave", 1)) },
        ).normalized()
        assertThat(wild.steps).hasSize(EveningRoutine.MAX_STEPS)
        assertThat(wild.steps.first().durationMinutes).isEqualTo(EveningRoutine.MAX_STEP_MINUTES)
        assertThat(wild.steps.first().heat!!.level).isEqualTo(EveningRoutine.MAX_HEAT_LEVEL)
        assertThat(wild.steps.first().massage!!.intensity).isEqualTo(EveningRoutine.MIN_INTENSITY)
        assertThat(RoutineStep(5).isPause).isTrue()
        assertThat(EveningRoutine.Default.totalMinutes).isEqualTo(50)
    }
}
