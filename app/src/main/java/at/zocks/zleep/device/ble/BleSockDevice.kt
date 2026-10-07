package at.zocks.zleep.device.ble

import at.zocks.zleep.domain.device.SockDevice
import at.zocks.zleep.domain.model.ConnectionState
import at.zocks.zleep.domain.model.DeviceCapabilities
import at.zocks.zleep.domain.model.HeatCommand
import at.zocks.zleep.domain.model.HeatState
import at.zocks.zleep.domain.model.MassageCommand
import at.zocks.zleep.domain.model.MassageState
import at.zocks.zleep.domain.model.SensorSample
import at.zocks.zleep.domain.model.SockSide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock

/**
 * Eine echte Socke über Bluetooth Low Energy. Nach [connect] hält sie die Verbindung:
 * reißt sie ab, wird mit wachsendem Abstand ([RECONNECT_BACKOFF_MS]) neu verbunden, bis
 * [disconnect] aufgerufen wird. Messwerte werden nur weitergegeben, wie sie ankommen –
 * fehlende Werte bleiben `null` ([SockBleProtocol.parseSensor]).
 *
 * Befehle, die nicht ankommen, werfen [SockCommandException]; die Regler werten das als
 * „nicht verbunden“. Die Firmware muss beim Verbindungsabbruch selbst aufhören zu heizen –
 * die App überwacht zusätzlich über den HeatSafetyGuard (kein Messwert > 30 s → aus).
 */
class BleSockDevice(
    override val side: SockSide,
    val address: String,
    private val factory: GattConnectionFactory,
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val backoffMs: List<Long> = RECONNECT_BACKOFF_MS,
) : SockDevice {

    @Volatile
    override var capabilities: DeviceCapabilities = DeviceCapabilities.None
        private set

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _battery = MutableStateFlow<Int?>(null)
    override val batteryPercent: StateFlow<Int?> = _battery.asStateFlow()

    private val _heat = MutableStateFlow(HeatState.Off)
    override val heatState: StateFlow<HeatState> = _heat.asStateFlow()

    private val _massage = MutableStateFlow(MassageState.Off)
    override val massageState: StateFlow<MassageState> = _massage.asStateFlow()

    private val samples = MutableSharedFlow<SensorSample>(extraBufferCapacity = SAMPLE_BUFFER)
    override val sensorData: SharedFlow<SensorSample> = samples.asSharedFlow()

    private val lifecycle = Mutex()
    private val commands = Mutex()
    private var job: Job? = null

    @Volatile
    private var connection: GattConnection? = null

    @Volatile
    private var mtu = DEFAULT_MTU

    /** Baut die Verbindung auf und wartet höchstens [FIRST_ATTEMPT_TIMEOUT_MS] auf den ersten Versuch. */
    override suspend fun connect() {
        lifecycle.withLock {
            if (job?.isActive != true) job = scope.launch { maintain() }
        }
        withTimeoutOrNull(FIRST_ATTEMPT_TIMEOUT_MS) {
            connectionState.first { it == ConnectionState.CONNECTED || it == ConnectionState.RECONNECTING }
        }
    }

    override suspend fun disconnect() = lifecycle.withLock {
        job?.cancelAndJoin()
        job = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    /** Verbinden, bei Abbruch mit Pause neu verbinden – bis die Coroutine abgebrochen wird. */
    private suspend fun maintain() {
        var attempt = 0
        while (scope.isActive) {
            _connectionState.value = if (attempt == 0) ConnectionState.CONNECTING else ConnectionState.RECONNECTING
            val conn = factory.open(address)
            try {
                if (setUp(conn)) {
                    attempt = 0
                    connection = conn
                    _connectionState.value = ConnectionState.CONNECTED
                    conn.events.takeWhile { it !is GattEvent.Disconnected }.collect { handle(it) }
                }
            } finally {
                connection = null
                conn.close()
            }
            _connectionState.value = ConnectionState.RECONNECTING
            delay(backoffMs[attempt.coerceAtMost(backoffMs.lastIndex)])
            attempt++
        }
    }

    private suspend fun setUp(conn: GattConnection): Boolean {
        if (!conn.connect()) return false
        if (SockBleProtocol.REQUIRES_BOND && !conn.isBonded && !conn.bond()) return false
        mtu = conn.requestMtu(SockBleProtocol.PREFERRED_MTU).coerceAtLeast(DEFAULT_MTU)
        // Ohne lesbare Fähigkeiten meldet die Socke nichts – dann wird z. B. nicht geheizt.
        capabilities = conn.read(SockBleProtocol.SERVICE, SockBleProtocol.CAPABILITIES)
            ?.let(SockBleProtocol::parseCapabilities) ?: DeviceCapabilities.None
        conn.read(SockBleProtocol.SERVICE, SockBleProtocol.STATUS)?.let(SockBleProtocol::parseStatus)?.let(::apply)
        conn.read(SockBleProtocol.BATTERY_SERVICE, SockBleProtocol.BATTERY_LEVEL)?.let(SockBleProtocol::parseBattery)
            ?.let { _battery.value = it }
        if (!conn.enableNotifications(SockBleProtocol.SERVICE, SockBleProtocol.SENSOR)) return false
        if (!conn.enableNotifications(SockBleProtocol.SERVICE, SockBleProtocol.STATUS)) return false
        // Akku-Benachrichtigungen sind nett, aber nicht nötig.
        conn.enableNotifications(SockBleProtocol.BATTERY_SERVICE, SockBleProtocol.BATTERY_LEVEL)
        return true
    }

    private fun handle(event: GattEvent) {
        if (event !is GattEvent.Notification) return
        when (event.characteristic) {
            SockBleProtocol.SENSOR -> SockBleProtocol.parseSensor(event.value, side, clock.instant())?.let { samples.tryEmit(it) }
            SockBleProtocol.STATUS -> SockBleProtocol.parseStatus(event.value)?.let(::apply)
            SockBleProtocol.BATTERY_LEVEL -> SockBleProtocol.parseBattery(event.value)?.let { _battery.value = it }
        }
    }

    private fun apply(status: SockBleProtocol.Status) {
        _heat.value = if (status.heating && !status.firmwareOverheatCutoff) {
            HeatState(active = true, level = status.heatLevel, targetTemperatureC = status.targetTemperatureC)
        } else {
            HeatState.Off
        }
        _massage.value = if (status.massaging) {
            MassageState(active = true, patternId = _massage.value.patternId, intensity = status.massageIntensity)
        } else {
            MassageState.Off
        }
    }

    override suspend fun setHeat(command: HeatCommand) {
        send(SockBleProtocol.encodeHeat(command))
        _heat.value = HeatState(active = true, level = command.level, targetTemperatureC = command.targetTemperatureC)
    }

    override suspend fun stopHeat() {
        send(SockBleProtocol.encodeHeatStop())
        _heat.value = HeatState.Off
    }

    override suspend fun startMassage(command: MassageCommand) {
        send(SockBleProtocol.encodeMassage(command))
        _massage.value = MassageState(active = true, patternId = command.pattern.id, intensity = command.intensity)
    }

    override suspend fun stopMassage(fadeOutMs: Long) {
        send(SockBleProtocol.encodeMassageStop(fadeOutMs))
        _massage.value = MassageState.Off
    }

    /** Schreibt einen Befehl Rahmen für Rahmen; Befehle derselben Socke laufen nacheinander. */
    private suspend fun send(payload: ByteArray) = commands.withLock {
        val conn = connection ?: throw SockCommandException("Socke ${side.name} nicht verbunden")
        SockBleProtocol.frames(payload, mtu).forEach { frame ->
            if (!conn.write(SockBleProtocol.SERVICE, SockBleProtocol.CONTROL, frame)) {
                throw SockCommandException("Befehl an ${side.name} nicht angekommen")
            }
        }
    }

    companion object {
        /** Wartezeiten zwischen Verbindungsversuchen: 2 s, 5 s, 10 s, 30 s, dann jede Minute. */
        val RECONNECT_BACKOFF_MS = listOf(2_000L, 5_000L, 10_000L, 30_000L, 60_000L)
        const val FIRST_ATTEMPT_TIMEOUT_MS = 20_000L
        const val DEFAULT_MTU = 23
        private const val SAMPLE_BUFFER = 64
    }
}
