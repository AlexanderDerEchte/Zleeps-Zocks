package at.zocks.zleep.domain.alarm

import at.zocks.zleep.domain.massage.MassageController
import at.zocks.zleep.domain.model.AlarmPreferences
import at.zocks.zleep.domain.model.NightEventType
import at.zocks.zleep.domain.model.NightSource
import at.zocks.zleep.domain.model.SleepStage
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.domain.model.UserSettings
import at.zocks.zleep.domain.recording.LiveRecording
import at.zocks.zleep.domain.recording.NightRecorder
import at.zocks.zleep.domain.recording.RecordingState
import at.zocks.zleep.testing.FakeNightRepository
import at.zocks.zleep.testing.FakePairProvider
import at.zocks.zleep.testing.FakeSettingsRepository
import at.zocks.zleep.testing.FakeSockDevice
import at.zocks.zleep.testing.FullCapabilities
import at.zocks.zleep.testing.SchedulerClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class SmartAlarmControllerTest {

    private val zone = ZoneId.of("Europe/Vienna")
    private fun at(text: String): Instant = LocalDateTime.parse(text).atZone(zone).toInstant()

    private class FakeRecording : LiveRecording {
        override val state = MutableStateFlow<RecordingState>(RecordingState.Idle())
        var interval = NightRecorder.ANALYSIS_EVERY_EPOCHS
        override fun setAnalysisInterval(epochs: Int) {
            interval = epochs
        }
    }

    private class FakeNotifier : AlarmNotifier {
        val calls = mutableListOf<String>()
        override fun show(sound: Boolean) {
            calls += if (sound) "sound" else "silent"
        }
        override fun dismiss() {
            calls += "dismiss"
        }
    }

    private class FakeScheduler : WakeAlarmScheduler {
        var scheduled: Instant? = null
        override fun schedule(at: Instant) {
            scheduled = at
        }
        override fun cancel() {
            scheduled = null
        }
        override fun canScheduleExact() = true
    }

    private val left = FakeSockDevice(SockSide.LEFT, FullCapabilities)
    private val right = FakeSockDevice(SockSide.RIGHT, FullCapabilities)
    private val recording = FakeRecording()
    private val notifier = FakeNotifier()
    private val scheduler = FakeScheduler()
    private val nights = FakeNightRepository()
    private val settings = FakeSettingsRepository(
        UserSettings(wakeTime = LocalTime.of(6, 45), alarm = AlarmPreferences(enabled = true, windowMinutes = 30, method = AlarmMethod.MASSAGE_THEN_SOUND)),
    )
    private var nightId = 0L

    private suspend fun TestScope.controller(connect: Boolean = true): Pair<SmartAlarmController, MassageController> {
        if (connect) {
            left.connect()
            right.connect()
        }
        nightId = nights.startNight(at("2026-10-05T22:30"), NightSource.DEVICE)
        val clock = SchedulerClock(testScheduler, zoneId = zone)
        val massage = MassageController(FakePairProvider(left, right), backgroundScope, clock)
        val controller = SmartAlarmController(recording, settings, nights, massage, notifier, scheduler, clock, backgroundScope)
        controller.start()
        runCurrent()
        return controller to massage
    }

    private fun TestScope.night(now: String, stage: SleepStage? = null, stageAt: String? = null, simulated: Boolean = false) {
        recording.state.value = RecordingState.Active(
            nightId = nightId,
            startedAt = at("2026-10-05T22:30"),
            now = at(now),
            latestStage = stage,
            latestStageAt = stageAt?.let(::at),
            simulated = simulated,
        )
        runCurrent()
    }

    @Test
    fun `arms for the night with a fallback on the system clock`() = runTest {
        val (controller, _) = controller()
        night("2026-10-06T02:00", SleepStage.DEEP, "2026-10-06T02:00")
        assertThat(controller.state.value).isEqualTo(AlarmState.Armed(nightId, WakeWindow(at("2026-10-06T06:15"), at("2026-10-06T06:45"))))
        assertThat(scheduler.scheduled).isEqualTo(at("2026-10-06T06:45"))
        assertThat(recording.interval).isEqualTo(NightRecorder.ANALYSIS_EVERY_EPOCHS)
    }

    @Test
    fun `simulated nights get no fallback`() = runTest {
        controller()
        night("2026-10-06T02:00", simulated = true)
        assertThat(scheduler.scheduled).isNull()
    }

    @Test
    fun `in the window the recorder analyses every minute`() = runTest {
        controller()
        night("2026-10-06T06:16", SleepStage.DEEP, "2026-10-06T06:16")
        assertThat(recording.interval).isEqualTo(SmartAlarmController.FINE_ANALYSIS_EPOCHS)
    }

    @Test
    fun `wakes with massage in light sleep and adds sound after three minutes`() = runTest {
        val (controller, massage) = controller()
        night("2026-10-06T06:20", SleepStage.DEEP, "2026-10-06T06:19")
        assertThat(controller.state.value).isInstanceOf(AlarmState.Armed::class.java)

        night("2026-10-06T06:24", SleepStage.LIGHT, "2026-10-06T06:23")
        val ringing = controller.state.value as AlarmState.Ringing
        assertThat(ringing.reason).isEqualTo(WakeReason.LIGHT_SLEEP)
        assertThat(ringing.sound).isFalse()
        assertThat(massage.state.value.session).isNotNull()
        assertThat(notifier.calls).containsExactly("silent")
        assertThat(scheduler.scheduled).isNull()
        assertThat(nights.events[nightId]!!.single().type).isEqualTo(NightEventType.ALARM)
        assertThat(recording.interval).isEqualTo(NightRecorder.ANALYSIS_EVERY_EPOCHS)

        advanceTimeBy(Duration.ofMinutes(3).toMillis() - 1)
        runCurrent()
        assertThat(notifier.calls).containsExactly("silent")
        advanceTimeBy(2)
        runCurrent()
        assertThat(notifier.calls).containsExactly("silent", "sound").inOrder()
        assertThat((controller.state.value as AlarmState.Ringing).sound).isTrue()
    }

    @Test
    fun `wakes at the deadline even in deep sleep`() = runTest {
        val (controller, _) = controller()
        night("2026-10-06T06:44", SleepStage.DEEP, "2026-10-06T06:44")
        assertThat(controller.state.value).isInstanceOf(AlarmState.Armed::class.java)
        night("2026-10-06T06:45", SleepStage.DEEP, "2026-10-06T06:44")
        assertThat((controller.state.value as AlarmState.Ringing).reason).isEqualTo(WakeReason.DEADLINE)
    }

    @Test
    fun `without connected socks it rings with sound right away`() = runTest {
        val (controller, _) = controller(connect = false)
        night("2026-10-06T06:45")
        assertThat((controller.state.value as AlarmState.Ringing).sound).isTrue()
        assertThat(notifier.calls).containsExactly("sound")
    }

    @Test
    fun `dismissing stops everything and does not ring again this night`() = runTest {
        val (controller, massage) = controller()
        night("2026-10-06T06:45")
        controller.dismiss()
        runCurrent()
        assertThat(controller.state.value).isEqualTo(AlarmState.Done(nightId))
        assertThat(notifier.calls.last()).isEqualTo("dismiss")
        advanceTimeBy(5_000)
        assertThat(massage.state.value.session).isNull()
        assertThat(nights.events[nightId]!!.single().end).isNotNull()

        night("2026-10-06T06:50", SleepStage.LIGHT, "2026-10-06T06:50")
        assertThat(controller.state.value).isEqualTo(AlarmState.Done(nightId))
        // Auch die Eskalation zum Ton kommt nicht mehr.
        advanceTimeBy(Duration.ofMinutes(5).toMillis())
        assertThat(notifier.calls).doesNotContain("sound")
    }

    @Test
    fun `snooze rings again through the system alarm`() = runTest {
        val (controller, _) = controller()
        night("2026-10-06T06:45")
        controller.snooze()
        val snoozed = controller.state.value as AlarmState.Snoozed
        assertThat(scheduler.scheduled).isEqualTo(snoozed.until)

        controller.onSystemAlarm()
        assertThat((controller.state.value as AlarmState.Ringing).reason).isEqualTo(WakeReason.SNOOZE)
    }

    @Test
    fun `the fallback rings when the app missed the window`() = runTest {
        val (controller, _) = controller()
        controller.onSystemAlarm()
        assertThat((controller.state.value as AlarmState.Ringing).reason).isEqualTo(WakeReason.FALLBACK)
    }

    @Test
    fun `ending the night or switching the alarm off disarms`() = runTest {
        val (controller, _) = controller()
        night("2026-10-06T02:00")
        recording.state.value = RecordingState.Idle(lastNightId = nightId)
        runCurrent()
        assertThat(controller.state.value).isEqualTo(AlarmState.Off)
        assertThat(scheduler.scheduled).isNull()

        night("2026-10-06T02:10")
        assertThat(controller.state.value).isInstanceOf(AlarmState.Armed::class.java)
        settings.update { it.copy(alarm = it.alarm.copy(enabled = false)) }
        runCurrent()
        assertThat(controller.state.value).isEqualTo(AlarmState.Off)
        assertThat(scheduler.scheduled).isNull()
    }
}
