package at.zocks.zleep.domain.alarm

import at.zocks.zleep.domain.massage.BuiltInMassagePrograms
import at.zocks.zleep.domain.massage.MassageController
import at.zocks.zleep.domain.model.AlarmPreferences
import at.zocks.zleep.domain.model.NightEvent
import at.zocks.zleep.domain.model.NightEventType
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.domain.recording.LiveRecording
import at.zocks.zleep.domain.recording.NightRecorder
import at.zocks.zleep.domain.recording.RecordingState
import at.zocks.zleep.domain.repository.NightRepository
import at.zocks.zleep.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicBoolean

sealed interface AlarmState {
    /** Kein Wecker für die laufende Nacht (oder keine Nacht). */
    data object Off : AlarmState

    /** Wecker gestellt; geweckt wird im [window]. */
    data class Armed(val nightId: Long, val window: WakeWindow) : AlarmState

    data class Ringing(val nightId: Long?, val reason: WakeReason, val since: Instant, val sound: Boolean) : AlarmState

    data class Snoozed(val nightId: Long?, val until: Instant) : AlarmState

    /** Für diese Nacht schon geweckt – nicht noch einmal. */
    data class Done(val nightId: Long?) : AlarmState
}

/**
 * Smarter Wecker während einer Aufzeichnung: berechnet das Weckfenster aus der gewünschten
 * Aufstehzeit, lässt den Recorder im Fenster jede Minute auswerten und weckt bei leichtem
 * Schlaf, spätestens am Fensterende. Als Ausfallsicherung steht für echte Nächte ein exakter
 * Systemwecker auf dem Fensterende ([WakeAlarmScheduler]). Geweckt wird per Ton, sanfter
 * Massage oder erst Massage und nach [SmartAlarmDecider.ESCALATION] zusätzlich Ton. Ohne
 * verbundene Socken wird immer mit Ton geweckt.
 */
class SmartAlarmController(
    private val recorder: LiveRecording,
    private val settings: SettingsRepository,
    private val nights: NightRepository,
    private val massage: MassageController,
    private val notifier: AlarmNotifier,
    private val scheduler: WakeAlarmScheduler,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<AlarmState>(AlarmState.Off)
    val state: StateFlow<AlarmState> = _state.asStateFlow()

    private val mutex = Mutex()
    private val started = AtomicBoolean(false)
    private var escalation: Job? = null
    private var alarmEventId: Long? = null

    private data class Plan(val wakeTime: LocalTime, val alarm: AlarmPreferences)

    /** Beobachtet Aufzeichnung und Einstellungen im App-Scope; mehrfacher Aufruf ist harmlos. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            val plans = settings.settings.map { Plan(it.wakeTime, it.alarm) }.distinctUntilChanged()
            combine(recorder.state, plans) { recording, plan -> recording to plan }.collect { (recording, plan) ->
                mutex.withLock { onUpdate(recording, plan) }
            }
        }
    }

    private suspend fun onUpdate(recording: RecordingState, plan: Plan) {
        val current = _state.value
        if (current is AlarmState.Ringing || current is AlarmState.Snoozed) return
        if (recording !is RecordingState.Active || !plan.alarm.enabled) {
            if (current is AlarmState.Armed) disarm()
            if (current !is AlarmState.Done || recording !is RecordingState.Active) _state.value = AlarmState.Off
            return
        }
        if (current is AlarmState.Done && current.nightId == recording.nightId) return

        val window = SmartAlarmPlanner.window(recording.startedAt, plan.wakeTime, plan.alarm.windowMinutes, clock.zone)
        if (current !is AlarmState.Armed || current.nightId != recording.nightId || current.window != window) {
            _state.value = AlarmState.Armed(recording.nightId, window)
            // Im Zeitraffer passt die echte Uhr nicht zur Nacht – dort keine Ausfallsicherung.
            if (recording.simulated) scheduler.cancel() else scheduler.schedule(window.end)
        }
        if (!recording.now.isBefore(window.start)) recorder.setAnalysisInterval(FINE_ANALYSIS_EPOCHS)
        val reason = SmartAlarmDecider.decide(recording.now, window, recording.latestStage, recording.latestStageAt) ?: return
        ring(recording.nightId, recording.now, reason, plan.alarm.method)
    }

    private fun disarm() {
        scheduler.cancel()
        recorder.setAnalysisInterval(NightRecorder.ANALYSIS_EVERY_EPOCHS)
    }

    private suspend fun ring(nightId: Long?, deviceTime: Instant?, reason: WakeReason, method: AlarmMethod) {
        disarm()
        if (nightId != null && deviceTime != null) {
            alarmEventId = runCatching {
                nights.addEvent(
                    nightId,
                    NightEvent(type = NightEventType.ALARM, side = SockSide.BOTH, start = deviceTime, end = null, detail = reason.name.lowercase()),
                )
            }.getOrNull()
        }
        val massageStarted = method != AlarmMethod.SOUND &&
            massage.start(BuiltInMassagePrograms.WAVE, WAKE_INTENSITY, WAKE_MINUTES)
        val sound = method == AlarmMethod.SOUND || !massageStarted
        notifier.show(sound)
        _state.value = AlarmState.Ringing(nightId, reason, clock.instant(), sound)
        escalation?.cancel()
        if (massageStarted && method == AlarmMethod.MASSAGE_THEN_SOUND) {
            escalation = scope.launch {
                delay(SmartAlarmDecider.ESCALATION.toMillis())
                mutex.withLock {
                    val ringing = _state.value as? AlarmState.Ringing ?: return@withLock
                    notifier.show(sound = true)
                    _state.value = ringing.copy(sound = true)
                }
            }
        }
    }

    /** Wecker aus. */
    suspend fun dismiss() = mutex.withLock {
        val nightId = silence() ?: return@withLock
        _state.value = AlarmState.Done(nightId.value)
    }

    /** Schlummern: aus und in [SNOOZE] noch einmal (über den exakten Systemwecker). */
    suspend fun snooze() = mutex.withLock {
        val nightId = silence() ?: return@withLock
        val until = clock.instant().plus(SNOOZE)
        scheduler.schedule(until)
        _state.value = AlarmState.Snoozed(nightId.value, until)
    }

    /** Der exakte Systemwecker hat ausgelöst (Fensterende oder Ende des Schlummerns). */
    suspend fun onSystemAlarm() = mutex.withLock {
        val current = _state.value
        if (current is AlarmState.Ringing || current is AlarmState.Done) return@withLock
        val recording = recorder.state.value as? RecordingState.Active
        val reason = if (current is AlarmState.Snoozed) WakeReason.SNOOZE else WakeReason.FALLBACK
        val nightId = (current as? AlarmState.Snoozed)?.nightId ?: recording?.nightId
        ring(nightId, recording?.now, reason, settings.settings.first().alarm.method)
    }

    /** Stoppt Ton, Massage und Anzeige; liefert die Nacht (verpackt, weil sie `null` sein kann). */
    private suspend fun silence(): NightRef? {
        val ringing = _state.value as? AlarmState.Ringing ?: return null
        escalation?.cancel()
        escalation = null
        notifier.dismiss()
        if (massage.state.value.session != null) massage.stop(soft = true)
        val recording = recorder.state.value as? RecordingState.Active
        val eventId = alarmEventId
        if (eventId != null && recording != null) runCatching { nights.endEvent(eventId, recording.now) }
        alarmEventId = null
        return NightRef(ringing.nightId)
    }

    @JvmInline
    private value class NightRef(val value: Long?)

    companion object {
        /** Im Weckfenster jede Minute auswerten (2 Epochen). */
        const val FINE_ANALYSIS_EPOCHS = 2
        const val WAKE_INTENSITY = 50
        const val WAKE_MINUTES = 15
        val SNOOZE: Duration = Duration.ofMinutes(9)
    }
}
