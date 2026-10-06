package at.zocks.zleep.domain.recording

import at.zocks.zleep.domain.analysis.HeuristicSleepStageClassifier
import at.zocks.zleep.domain.heat.HeatController
import at.zocks.zleep.domain.heat.HeatRequest
import at.zocks.zleep.domain.massage.BuiltInMassagePrograms
import at.zocks.zleep.domain.massage.MassageController
import at.zocks.zleep.domain.model.ConnectionState
import at.zocks.zleep.domain.model.NightEventType
import at.zocks.zleep.domain.model.NightSource
import at.zocks.zleep.domain.model.SensorSample
import at.zocks.zleep.domain.model.SleepStage
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.testing.FakeNightRepository
import at.zocks.zleep.testing.FakePairProvider
import at.zocks.zleep.testing.FakeSockDevice
import at.zocks.zleep.testing.FullCapabilities
import at.zocks.zleep.testing.SchedulerClock
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class NightRecorderTest {

    /** Gerätezeit, die der Test selbst vorstellt (wie beim echten Gerät = Messzeit). */
    private class TestDeviceClock(var now: Instant) : DeviceClock {
        override val isSimulated = false
        override fun now() = now
    }

    private val left = FakeSockDevice(SockSide.LEFT, FullCapabilities)
    private val right = FakeSockDevice(SockSide.RIGHT, FullCapabilities)
    private val nights = FakeNightRepository()
    private val clock = TestDeviceClock(Instant.parse("2026-10-05T20:30:00Z"))

    private class Setup(val recorder: NightRecorder, val heat: HeatController, val massage: MassageController)

    private suspend fun TestScope.setUp(): Setup {
        left.connect()
        right.connect()
        val pairs = FakePairProvider(left, right)
        val heat = HeatController(pairs, backgroundScope, SchedulerClock(testScheduler))
        val massage = MassageController(pairs, backgroundScope, SchedulerClock(testScheduler))
        val analyzer = NightAnalyzer(nights, HeuristicSleepStageClassifier(), StandardTestDispatcher(testScheduler))
        return Setup(NightRecorder(pairs, nights, analyzer, heat, massage, clock, backgroundScope), heat, massage)
    }

    /** Liefert [minutes] Minuten Messwerte beider Socken (alle 5 s). */
    private suspend fun TestScope.feed(minutes: Int, heartRate: Double, motion: Double, rmssd: Double = 45.0) {
        repeat(minutes * 12) { step ->
            clock.now = clock.now.plusSeconds(5)
            val wobble = if (step % 7 == 0) 1.0 else 0.0
            listOf(left, right).forEach { sock ->
                if (sock.connectionState.value == ConnectionState.CONNECTED) {
                    sock.sensorData.emit(SensorSample(sock.side, clock.now, (heartRate + wobble).toInt(), rmssd, 97, 33.0, motion, null))
                }
            }
            runCurrent()
        }
    }

    @Test
    fun `records epochs of both socks and finishes the night`() = runTest {
        val recorder = setUp().recorder
        val id = recorder.start()
        assertThat(recorder.state.value).isInstanceOf(RecordingState.Active::class.java)
        feed(minutes = 15, heartRate = 60.0, motion = 0.3)

        recorder.stop()
        val night = nights.night(id)!!
        assertThat(night.end).isEqualTo(clock.now)
        assertThat(night.source).isEqualTo(NightSource.DEVICE)
        val data = nights.getNightData(id)!!
        assertThat(data.measurements.map { it.side }.toSet()).containsExactly(SockSide.LEFT, SockSide.RIGHT)
        assertThat(data.measurements.size).isAtLeast(58) // 2 × ~30 Epochen
        assertThat(data.stages).isNotEmpty()
        assertThat(recorder.state.value).isEqualTo(RecordingState.Idle(lastNightId = id))
    }

    @Test
    fun `the night never ends before its last measurement`() = runTest {
        val recorder = setUp().recorder
        val id = recorder.start()
        feed(minutes = 30, heartRate = 60.0, motion = 0.3)
        val lastSample = clock.now
        // z. B. Zeitraffer gestoppt: die Gerätezeit springt zurück.
        clock.now = clock.now.minus(Duration.ofMinutes(29))
        recorder.stop()
        assertThat(nights.night(id)!!.end).isEqualTo(lastSample)
    }

    @Test
    fun `very short recordings are discarded`() = runTest {
        val recorder = setUp().recorder
        val id = recorder.start()
        feed(minutes = 3, heartRate = 60.0, motion = 0.3)
        recorder.stop()
        assertThat(nights.night(id)).isNull()
        assertThat(recorder.state.value).isEqualTo(RecordingState.Idle(discarded = true))
    }

    @Test
    fun `connection loss opens a gap and reconnects automatically`() = runTest {
        val recorder = setUp().recorder
        val id = recorder.start()
        feed(minutes = 2, heartRate = 60.0, motion = 0.3)

        right.connectionState.value = ConnectionState.DISCONNECTED
        runCurrent()
        val gapStart = clock.now
        assertThat((recorder.state.value as RecordingState.Active).openGaps).containsExactly(SockSide.RIGHT)
        assertThat(nights.gaps[id]!!.single().start).isEqualTo(gapStart)

        // Erster Wiederverbindungsversuch nach 2 s.
        feed(minutes = 1, heartRate = 60.0, motion = 0.3)
        advanceTimeBy(NightRecorder.RECONNECT_BACKOFF_MS.first() + 1)
        runCurrent()
        assertThat(right.commands.last()).isEqualTo("connect")
        assertThat(right.connectionState.value).isEqualTo(ConnectionState.CONNECTED)
        assertThat(nights.gaps[id]!!.single().end).isNotNull()
        assertThat((recorder.state.value as RecordingState.Active).openGaps).isEmpty()
    }

    @Test
    fun `heat and massage are marked in the night`() = runTest {
        val setup = setUp()
        val id = setup.recorder.start()
        feed(minutes = 1, heartRate = 60.0, motion = 0.3)
        setup.heat.start(HeatRequest(side = SockSide.LEFT))
        setup.massage.start(BuiltInMassagePrograms.RELAX, 40, 10)
        runCurrent()
        feed(minutes = 2, heartRate = 60.0, motion = 0.3)
        setup.heat.stop()
        runCurrent()
        feed(minutes = 10, heartRate = 60.0, motion = 0.3)
        setup.recorder.stop()

        val events = nights.events[id]!!
        val heat = events.single { it.type == NightEventType.HEAT }
        assertThat(heat.side).isEqualTo(SockSide.LEFT)
        assertThat(Duration.between(heat.start, heat.end)).isEqualTo(Duration.ofMinutes(2))
        val massage = events.single { it.type == NightEventType.MASSAGE }
        assertThat(massage.end).isNotNull() // spätestens beim Beenden abgeschlossen
    }

    @Test
    fun `detects the sleep window and stops by itself in the morning`() = runTest {
        val recorder = setUp().recorder
        val id = recorder.start()
        val start = clock.now
        feed(minutes = 20, heartRate = 70.0, motion = 0.4) // wach im Bett
        feed(minutes = 4 * 60, heartRate = 55.0, motion = 0.005) // Schlaf
        assertThat((recorder.state.value as RecordingState.Active).asleep).isTrue()
        feed(minutes = 40, heartRate = 72.0, motion = 0.4) // morgens wach

        val state = recorder.state.value
        assertThat(state).isEqualTo(RecordingState.Idle(lastNightId = id, autoStopped = true))
        val night = nights.night(id)!!
        assertThat(Duration.between(start, night.sleepOnset).toMinutes()).isIn(Range.closed(18L, 23L))
        assertThat(Duration.between(start, night.finalWake).toMinutes()).isIn(Range.closed(258L, 263L))
        assertThat(nights.stages[id]!!.count { it.stage != SleepStage.AWAKE }).isAtLeast(4 * 60 * 2 - 10)
    }

    @Test
    fun `a manual correction is not overwritten by later analysis`() = runTest {
        val recorder = setUp().recorder
        val id = recorder.start()
        feed(minutes = 20, heartRate = 70.0, motion = 0.4)
        val corrected = clock.now.minusSeconds(600)
        nights.updateSleepWindow(id, corrected, clock.now.plusSeconds(36_000), manual = true)
        feed(minutes = 60, heartRate = 55.0, motion = 0.005)
        assertThat(nights.night(id)!!.sleepOnset).isEqualTo(corrected)
    }

    @Test
    fun `resumes an open night after a restart`() = runTest {
        val recorder = setUp().recorder
        val open = nights.startNight(clock.now.minusSeconds(3_600), NightSource.DEVICE)
        assertThat(recorder.resumeIfNeeded()).isTrue()
        assertThat((recorder.state.value as RecordingState.Active).nightId).isEqualTo(open)
        assertThat(recorder.start()).isEqualTo(open)
    }

    @Test
    fun `an abandoned night older than the maximum is closed instead of resumed`() = runTest {
        val recorder = setUp().recorder
        val open = nights.startNight(clock.now.minus(Duration.ofHours(20)), NightSource.DEVICE)
        assertThat(recorder.resumeIfNeeded()).isFalse()
        assertThat(nights.night(open)!!.end).isNotNull()
        assertThat(recorder.state.value).isInstanceOf(RecordingState.Idle::class.java)
    }
}
