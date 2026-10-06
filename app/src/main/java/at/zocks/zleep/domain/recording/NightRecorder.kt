package at.zocks.zleep.domain.recording

import at.zocks.zleep.domain.device.SockDevice
import at.zocks.zleep.domain.device.SockPair
import at.zocks.zleep.domain.device.SockPairProvider
import at.zocks.zleep.domain.heat.HeatController
import at.zocks.zleep.domain.heat.HeatNotice
import at.zocks.zleep.domain.massage.MassageController
import at.zocks.zleep.domain.model.ConnectionState
import at.zocks.zleep.domain.model.EPOCH_LENGTH
import at.zocks.zleep.domain.model.EpochMeasurement
import at.zocks.zleep.domain.model.NightEvent
import at.zocks.zleep.domain.model.NightEventType
import at.zocks.zleep.domain.model.NightSource
import at.zocks.zleep.domain.model.SensorSample
import at.zocks.zleep.domain.model.SleepStage
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.domain.repository.NightRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant

sealed interface RecordingState {
    /** Keine Aufzeichnung. [lastNightId] ist die zuletzt beendete Nacht dieser Sitzung. */
    data class Idle(
        val lastNightId: Long? = null,
        val autoStopped: Boolean = false,
        val discarded: Boolean = false,
    ) : RecordingState

    data class Active(
        val nightId: Long,
        val startedAt: Instant,
        /** Zeit des Geräts (im Simulator ggf. Zeitraffer). */
        val now: Instant,
        val epochCount: Int = 0,
        val asleep: Boolean = false,
        val sleepOnset: Instant? = null,
        val latest: Map<SockSide, SensorSample> = emptyMap(),
        val connection: Map<SockSide, ConnectionState> = emptyMap(),
        val openGaps: Set<SockSide> = emptySet(),
        val simulated: Boolean = false,
    ) : RecordingState {
        val elapsed: Duration get() = Duration.between(startedAt, now).coerceAtLeast(Duration.ZERO)
    }
}

/**
 * Zeichnet eine Nacht auf: verdichtet Messwerte zu Epochen und speichert sie, markiert
 * Verbindungslücken und verbindet automatisch neu, hält Wärme- und Massage-Zeiten fest,
 * schätzt laufend Schlafphasen und Schlaffenster und beendet die Aufzeichnung morgens
 * selbst, wenn nach mindestens [MIN_SLEEP_FOR_AUTO_STOP] Schlaf [AWAKE_FOR_AUTO_STOP] lang
 * Wachsein erkannt wurde.
 *
 * Läuft im App-Scope; der Vordergrunddienst hält nur den Prozess am Leben.
 */
class NightRecorder(
    private val pairProvider: SockPairProvider,
    private val nights: NightRepository,
    private val analyzer: NightAnalyzer,
    private val heatController: HeatController,
    private val massageController: MassageController,
    private val clock: DeviceClock,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val lifecycle = Mutex()
    private var session: Session? = null
    private var sessionJob: Job? = null

    /** Startet eine neue Nacht (oder liefert die laufende). */
    suspend fun start(): Long = lifecycle.withLock {
        session?.let { return it.nightId }
        val now = clock.now()
        val id = nights.startNight(now, if (clock.isSimulated) NightSource.SIMULATOR else NightSource.DEVICE)
        launch(Session(id, now))
        id
    }

    /** Setzt eine offene Nacht fort (z. B. nachdem das System die App beendet hatte). */
    suspend fun resumeIfNeeded(): Boolean = lifecycle.withLock {
        if (session != null) return true
        val open = nights.getRecordingNight() ?: return false
        if (Duration.between(open.start, clock.now()) > MAX_DURATION) {
            nights.finishNight(open.id, open.start.plus(MAX_DURATION))
            analyzer.analyze(open.id)
            return false
        }
        launch(Session(open.id, open.start))
        true
    }

    suspend fun stop() = stop(auto = false)

    private suspend fun stop(auto: Boolean) = lifecycle.withLock {
        val current = session ?: return
        sessionJob?.cancelAndJoin()
        session = null
        sessionJob = null
        // Nie vor dem letzten Messwert enden – z. B. wenn die Zeitraffer-Uhr schon zurückgesprungen ist.
        val end = listOfNotNull(clock.now(), current.lastSampleTime()).max()
        if (Duration.between(current.startedAt, end) < MIN_DURATION) {
            nights.deleteNight(current.nightId)
            _state.value = RecordingState.Idle(discarded = true)
            return
        }
        current.finish(end)
        _state.value = RecordingState.Idle(lastNightId = current.nightId, autoStopped = auto)
    }

    private fun launch(newSession: Session) {
        session = newSession
        _state.value = RecordingState.Active(
            nightId = newSession.nightId,
            startedAt = newSession.startedAt,
            now = clock.now(),
            simulated = clock.isSimulated,
        )
        sessionJob = scope.launch { newSession.run() }
    }

    private fun updateActive(transform: (RecordingState.Active) -> RecordingState.Active) {
        _state.update { if (it is RecordingState.Active) transform(it) else it }
    }

    private inner class Session(val nightId: Long, val startedAt: Instant) {
        private val pair: SockPair = pairProvider.pair.value
        private val aggregator = EpochAggregator()
        private val buffer = mutableListOf<EpochMeasurement>()
        private val dataLock = Mutex()
        private val openGaps = mutableMapOf<SockSide, Long>()
        private val openHeatEvents = mutableMapOf<SockSide, Long>()
        private var openMassageEvent: Long? = null
        private var lastNotice: HeatNotice? = heatController.state.value.notice
        private var epochsSinceAnalysis = 0
        private var epochCount = 0
        private var lastEpoch: Instant? = null
        private val latest = mutableMapOf<SockSide, SensorSample>()
        private var lastSampleAt: Instant? = null

        suspend fun run() = coroutineScope {
            launch { collectSamples() }
            launch {
                // Live-Werte gesammelt veröffentlichen: im Zeitraffer kommen sonst tausende
                // Änderungen pro Sekunde, die jede Anzeige neu zeichnen müsste.
                while (isActive) {
                    delay(PUBLISH_INTERVAL_MS)
                    publishLive()
                }
            }
            pair.devices().forEach { device -> launch { watchConnection(device) } }
            launch { trackHeat() }
            launch { trackMassage() }
            launch {
                while (isActive) {
                    delay(FLUSH_INTERVAL_MS)
                    flush()
                }
            }
        }

        private suspend fun collectSamples() {
            merge(pair.left.sensorData, pair.right.sensorData).collect { sample ->
                val step = dataLock.withLock {
                    val epoch = aggregator.add(sample)
                    // Neue Epoche zählen, egal von welcher Socke sie zuerst kommt.
                    val newEpoch = epoch != null && (lastEpoch == null || epoch.start.isAfter(lastEpoch))
                    if (epoch != null) buffer += epoch
                    if (newEpoch) {
                        lastEpoch = epoch!!.start
                        epochCount++
                        epochsSinceAnalysis++
                    }
                    val due = epochsSinceAnalysis >= ANALYSIS_EVERY_EPOCHS
                    if (due) epochsSinceAnalysis = 0
                    latest[sample.side] = sample
                    lastSampleAt = sample.timestamp
                    (buffer.size >= MAX_BUFFER) to due
                }
                if (step.first) flush()
                if (step.second) {
                    publishLive()
                    analyse(allowAutoStop = true)
                }
            }
        }

        suspend fun lastSampleTime(): Instant? = dataLock.withLock { lastSampleAt }

        private suspend fun publishLive() {
            val (now, samples, count) = dataLock.withLock { Triple(lastSampleAt, latest.toMap(), epochCount) }
            if (now != null) updateActive { it.copy(now = now, latest = samples, epochCount = count) }
        }

        private suspend fun flush() {
            val pending = dataLock.withLock { buffer.toList().also { buffer.clear() } }
            if (pending.isNotEmpty()) nights.saveMeasurements(nightId, pending)
        }

        private suspend fun analyse(allowAutoStop: Boolean) {
            flush()
            val result = analyzer.analyze(nightId) ?: return
            // Bezug ist der letzte verarbeitete Messwert, nicht die Uhr: Kommen Messwerte
            // verspätet (z. B. nach einem Neuverbinden), gilt die Schätzung trotzdem als aktuell.
            val now = lastSampleTime() ?: clock.now()
            val lastStage = result.stages.lastOrNull()
            val recent = lastStage != null && Duration.between(lastStage.start, now) <= RECENT
            updateActive {
                it.copy(
                    asleep = recent && lastStage!!.stage != SleepStage.AWAKE,
                    sleepOnset = result.window?.sleepOnset,
                )
            }
            if (!allowAutoStop) return
            val window = result.window
            val sleptEnough = window != null && Duration.between(window.sleepOnset, window.finalWake) >= MIN_SLEEP_FOR_AUTO_STOP
            val awakeLongEnough = window != null && Duration.between(window.finalWake, now) >= AWAKE_FOR_AUTO_STOP
            val tooLong = Duration.between(startedAt, now) >= MAX_DURATION
            if ((sleptEnough && awakeLongEnough) || tooLong) {
                // Eigene Coroutine: stop() bricht diese Sitzung ab und wartet auf sie.
                scope.launch { stop(auto = true) }
            }
        }

        private suspend fun watchConnection(device: SockDevice) {
            var reconnecting: Job? = null
            coroutineScope {
                device.connectionState.collect { connection ->
                    val side = device.side
                    val now = clock.now()
                    if (connection != ConnectionState.CONNECTED && side !in openGaps) {
                        openGaps[side] = nights.openGap(nightId, side, now)
                    }
                    if (connection == ConnectionState.CONNECTED) {
                        openGaps.remove(side)?.let { nights.closeGap(it, now) }
                        reconnecting?.cancel()
                    }
                    if (connection == ConnectionState.DISCONNECTED && reconnecting?.isActive != true) {
                        reconnecting = launch { reconnect(device) }
                    }
                    updateActive { it.copy(connection = it.connection + (side to connection), openGaps = openGaps.keys.toSet()) }
                }
            }
        }

        /** Verbindet mit wachsendem Abstand neu, bis es klappt oder die Aufzeichnung endet. */
        private suspend fun reconnect(device: SockDevice) {
            var attempt = 0
            while (device.connectionState.value != ConnectionState.CONNECTED) {
                delay(RECONNECT_BACKOFF_MS[attempt.coerceAtMost(RECONNECT_BACKOFF_MS.lastIndex)])
                attempt++
                if (device.connectionState.value == ConnectionState.DISCONNECTED) runCatching { device.connect() }
            }
        }

        private suspend fun trackHeat() {
            heatController.state.collect { control ->
                val now = clock.now()
                control.plans.forEach { (side, plan) ->
                    if (side !in openHeatEvents) {
                        val detail = plan.targetTemperatureC?.let { "target=$it" } ?: "level=${plan.level}"
                        openHeatEvents[side] = nights.addEvent(
                            nightId,
                            NightEvent(type = NightEventType.HEAT, side = side, start = now, end = null, detail = detail),
                        )
                    }
                }
                (openHeatEvents.keys - control.plans.keys).forEach { side ->
                    openHeatEvents.remove(side)?.let { nights.endEvent(it, now) }
                }
                val notice = control.notice
                if (notice is HeatNotice.SafetyShutoff && notice !== lastNotice) {
                    nights.addEvent(
                        nightId,
                        NightEvent(type = NightEventType.SAFETY_SHUTOFF, side = notice.side, start = now, end = now, detail = notice.reason.name),
                    )
                }
                lastNotice = notice
            }
        }

        private suspend fun trackMassage() {
            massageController.state.map { it.session }.distinctUntilChangedBy { it?.startedAt }.collect { massage ->
                val now = clock.now()
                openMassageEvent?.let { nights.endEvent(it, now) }
                openMassageEvent = massage?.let {
                    nights.addEvent(
                        nightId,
                        NightEvent(type = NightEventType.MASSAGE, side = it.side, start = now, end = null, detail = "program=${it.program.id}"),
                    )
                }
            }
        }

        /** Schließt alles ab; läuft nach dem Abbruch der Sitzung. */
        suspend fun finish(end: Instant) {
            dataLock.withLock { buffer += aggregator.flush() }
            flush()
            openGaps.values.forEach { nights.closeGap(it, end) }
            openHeatEvents.values.forEach { nights.endEvent(it, end) }
            openMassageEvent?.let { nights.endEvent(it, end) }
            nights.finishNight(nightId, end)
            analyse(allowAutoStop = false)
        }
    }

    companion object {
        /** Kürzere Aufzeichnungen werden verworfen (z. B. versehentlich gestartet). */
        val MIN_DURATION: Duration = Duration.ofMinutes(10)
        val MAX_DURATION: Duration = Duration.ofHours(16)
        val MIN_SLEEP_FOR_AUTO_STOP: Duration = Duration.ofHours(3)
        val AWAKE_FOR_AUTO_STOP: Duration = Duration.ofMinutes(30)

        /** Auswertung alle 10 Epochen (5 Minuten Messzeit). */
        const val ANALYSIS_EVERY_EPOCHS = 10
        const val MAX_BUFFER = 40
        const val FLUSH_INTERVAL_MS = 10_000L
        const val PUBLISH_INTERVAL_MS = 1_000L
        val RECONNECT_BACKOFF_MS = listOf(2_000L, 5_000L, 10_000L, 30_000L, 60_000L)
        private val RECENT: Duration = EPOCH_LENGTH.multipliedBy(4)
    }
}
