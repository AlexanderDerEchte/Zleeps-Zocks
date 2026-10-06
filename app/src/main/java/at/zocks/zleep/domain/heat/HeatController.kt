package at.zocks.zleep.domain.heat

import at.zocks.zleep.domain.device.SockDevice
import at.zocks.zleep.domain.device.SockPair
import at.zocks.zleep.domain.device.SockPairProvider
import at.zocks.zleep.domain.model.ConnectionState
import at.zocks.zleep.domain.model.HeatCommand
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.domain.sleep.MotionSleepDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration

/**
 * Steuert das Heizen beider Socken: Start/Stopp (gemeinsam oder je Seite), Timer,
 * automatisches Abschalten beim Einschlafen und Vorwärmen. Jeder Befehl und jeder Messwert
 * läuft durch den [HeatSafetyGuard].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HeatController(
    private val pairProvider: SockPairProvider,
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val guard: HeatSafetyGuard = HeatSafetyGuard(),
    private val sleepDetector: MotionSleepDetector = MotionSleepDetector(),
) {
    private val _state = MutableStateFlow(HeatControlState())
    val state: StateFlow<HeatControlState> = _state.asStateFlow()

    private val mutex = Mutex()
    private val timers = mutableMapOf<SockSide, Job>()

    init {
        scope.launch {
            pairProvider.pair.collectLatest { pair ->
                stopAllLocally()
                merge(pair.left.sensorData, pair.right.sensorData).collect { sample ->
                    val reason = mutex.withLock { guard.onSample(sample, clock.instant()) }
                    if (reason != null) shutOff(pair, sample.side, HeatNotice.SafetyShutoff(sample.side, reason))
                    val asleep = sleepDetector.onSample(sample)
                    if (asleep && _state.value.autoOffWhenAsleep && _state.value.isHeating) {
                        _state.value.plans.keys.forEach { side -> shutOff(pair, side, HeatNotice.FellAsleep(side)) }
                    }
                }
            }
        }
    }

    fun setAutoOffWhenAsleep(enabled: Boolean) {
        _state.update { it.copy(autoOffWhenAsleep = enabled) }
    }

    /** Startet das Heizen. Liefert die Socken, deren Start abgelehnt wurde. */
    suspend fun start(request: HeatRequest, preheat: Boolean = false): Map<SockSide, HeatRejection> {
        val pair = pairProvider.pair.value
        val duration = request.duration.coerceIn(Duration.ofMinutes(1), HeatSafetyGuard.MAX_CONTINUOUS)
        val rejections = mutableMapOf<SockSide, HeatRejection>()
        if (preheat) sleepDetector.reset()

        for (device in pair.devices(request.side)) {
            val side = device.side
            val now = clock.instant()
            val rejection = when {
                device.connectionState.value != ConnectionState.CONNECTED -> HeatRejection.NOT_CONNECTED
                else -> mutex.withLock { guard.checkStart(side, device.capabilities.skinTemperature, now) }
            }
            if (rejection != null) {
                rejections[side] = rejection
                _state.update { it.copy(notice = HeatNotice.Rejected(side, rejection)) }
                continue
            }
            val target = if (request.mode == HeatMode.TARGET) guard.clampTarget(request.targetTemperatureC) else null
            val level = request.level.coerceIn(1, device.capabilities.heatLevels.coerceAtLeast(1))
            device.setHeat(HeatCommand(level, target))
            mutex.withLock { guard.onHeatingStarted(side, now) }
            val plan = SideHeatPlan(level, target, now, now.plus(duration), preheat)
            _state.update {
                it.copy(
                    plans = it.plans + (side to plan),
                    notice = if (preheat) HeatNotice.PreheatStarted(side) else it.notice,
                )
            }
            scheduleTimer(pair, device, duration)
        }
        return rejections
    }

    suspend fun stop(side: SockSide = SockSide.BOTH) {
        val pair = pairProvider.pair.value
        side.feet.forEach { foot -> shutOff(pair, foot, notice = null) }
    }

    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun cooldownUntil(side: SockSide) = guard.cooldownUntil(side, clock.instant())

    private fun scheduleTimer(pair: SockPair, device: SockDevice, duration: Duration) {
        synchronized(timers) { timers.remove(device.side)?.cancel() }
        val job = scope.launch {
            delay(duration.toMillis())
            val reason = mutex.withLock { guard.checkRuntime(device.side, clock.instant()) }
            shutOff(
                pair,
                device.side,
                if (reason != null) HeatNotice.SafetyShutoff(device.side, reason) else HeatNotice.TimerFinished(device.side),
            )
        }
        synchronized(timers) { timers[device.side] = job }
    }

    private suspend fun shutOff(pair: SockPair, side: SockSide, notice: HeatNotice?) {
        val device = pair.devices(side).single()
        val wasPlanned = _state.value.plans.containsKey(side)
        if (!wasPlanned && !device.heatState.value.active) return
        // Läuft das Abschalten im Timer selbst, darf er sich nicht vorher abbrechen.
        val self = currentCoroutineContext()[Job]
        synchronized(timers) { timers.remove(side) }?.takeIf { it != self }?.cancel()
        mutex.withLock { guard.onHeatingStopped(side) }
        _state.update { it.copy(plans = it.plans - side, notice = notice ?: it.notice) }
        runCatching { device.stopHeat() }
    }

    private fun stopAllLocally() {
        synchronized(timers) {
            timers.values.forEach { it.cancel() }
            timers.clear()
        }
        _state.update { it.copy(plans = emptyMap()) }
    }
}
