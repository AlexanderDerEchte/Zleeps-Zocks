package at.zocks.zleep.device.simulator

import at.zocks.zleep.domain.device.SockDevice
import at.zocks.zleep.domain.model.ConnectionState
import at.zocks.zleep.domain.model.DeviceCapabilities
import at.zocks.zleep.domain.model.HeatCommand
import at.zocks.zleep.domain.model.HeatState
import at.zocks.zleep.domain.model.MassageCommand
import at.zocks.zleep.domain.model.MassageState
import at.zocks.zleep.domain.model.MassageZone
import at.zocks.zleep.domain.model.SensorSample
import at.zocks.zleep.domain.model.SockSide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Simulierte Socke. Misst den simulierten Menschen der [SimulationEngine], fügt eigenes
 * Sensorrauschen hinzu und reagiert auf Heiz- und Massagebefehle:
 * - Heizen: Heizelement nähert sich der Zieltemperatur (Zeitkonstante 3 min), die Haut folgt zu 70 %.
 * - Massage: Vibration erscheint als leichte Bewegung, der Puls sinkt etwas.
 * - Akku: Grundverbrauch 1,5 %/h, Heizen +3 %/h je Stufe, Massage +5 %/h.
 */
class SimulatedSockDevice(
    override val side: SockSide,
    private val engine: SimulationEngine,
    private val scope: CoroutineScope,
    seed: Long,
    private val connectDelayMs: Long = 800,
    initialBattery: Int = 92,
) : SockDevice {

    init {
        require(side == SockSide.LEFT || side == SockSide.RIGHT)
    }

    override val capabilities = DeviceCapabilities(
        heartRate = true,
        hrv = true,
        spo2 = true,
        skinTemperature = true,
        motion = true,
        massageZones = MassageZone.entries.toSet(),
        heatLevels = HEAT_LEVELS,
    )

    private val random = Random(seed)
    private val sideOffsetC = if (side == SockSide.LEFT) 0.1 else -0.1

    private val _connection = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _battery = MutableStateFlow<Int?>(null)
    override val batteryPercent: StateFlow<Int?> = _battery.asStateFlow()

    private val _heat = MutableStateFlow(HeatState.Off)
    override val heatState: StateFlow<HeatState> = _heat.asStateFlow()

    private val _massage = MutableStateFlow(MassageState.Off)
    override val massageState: StateFlow<MassageState> = _massage.asStateFlow()

    private val samples = MutableSharedFlow<SensorSample>(extraBufferCapacity = 256)
    override val sensorData: Flow<SensorSample> = samples.asSharedFlow()

    private val batteryStart = initialBattery.toDouble()
    private var batteryUsed = 0.0
    private var heaterTemperature: Double? = null
    private var measuring: Job? = null
    private var massageFade: Job? = null

    override suspend fun connect() {
        if (_connection.value == ConnectionState.CONNECTED || _connection.value == ConnectionState.CONNECTING) return
        _connection.value = ConnectionState.CONNECTING
        delay(connectDelayMs)
        _battery.value = currentBattery()
        _connection.value = ConnectionState.CONNECTED
        measuring?.cancel()
        measuring = scope.launch { engine.ticks.collect(::onTick) }
    }

    override suspend fun disconnect() {
        measuring?.cancel()
        measuring = null
        _heat.value = HeatState.Off
        _massage.value = MassageState.Off
        heaterTemperature = null
        _connection.value = ConnectionState.DISCONNECTED
    }

    override suspend fun setHeat(command: HeatCommand) {
        check(_connection.value == ConnectionState.CONNECTED) { "Socke $side nicht verbunden" }
        _heat.value = HeatState(
            active = true,
            level = command.level.coerceIn(1, HEAT_LEVELS),
            targetTemperatureC = command.targetTemperatureC,
        )
    }

    override suspend fun stopHeat() {
        _heat.value = HeatState.Off
    }

    override suspend fun startMassage(command: MassageCommand) {
        check(_connection.value == ConnectionState.CONNECTED) { "Socke $side nicht verbunden" }
        massageFade?.cancel()
        _massage.value = MassageState(active = true, patternId = command.pattern.id, intensity = command.intensity.coerceIn(0, 100))
    }

    override suspend fun stopMassage(fadeOutMs: Long) {
        massageFade?.cancel()
        if (fadeOutMs <= 0 || !_massage.value.active) {
            _massage.value = MassageState.Off
            return
        }
        val startIntensity = _massage.value.intensity
        massageFade = scope.launch {
            val steps = 10
            for (step in 1..steps) {
                delay(fadeOutMs / steps)
                _massage.value = _massage.value.copy(intensity = startIntensity * (steps - step) / steps)
            }
            _massage.value = MassageState.Off
        }
    }

    private fun onTick(tick: SimTick) {
        val stepSeconds = tick.step.seconds.toDouble()
        val heat = _heat.value
        val massage = _massage.value
        val physiology = tick.physiology

        // Akku
        val drainPerHour = BASE_DRAIN + (if (heat.active) heat.level * HEAT_DRAIN_PER_LEVEL else 0.0) +
            (if (massage.active) MASSAGE_DRAIN else 0.0)
        batteryUsed += drainPerHour * stepSeconds / 3600.0
        _battery.value = currentBattery()
        if (currentBattery() == 0) {
            scope.launch { disconnect() }
            return
        }

        // Verbindungsabbruch: Socke versucht neu zu verbinden, keine Messwerte.
        if (engine.isDroppedOut(side)) {
            _connection.value = ConnectionState.RECONNECTING
            return
        }
        if (_connection.value == ConnectionState.RECONNECTING) _connection.value = ConnectionState.CONNECTED

        // Heizelement
        val skinBase = physiology.skinTemperatureC + sideOffsetC
        val heaterTarget = if (heat.active) {
            heat.targetTemperatureC ?: (skinBase + heat.level * 1.6)
        } else {
            skinBase
        }
        val tau = if (heat.active) HEAT_UP_SECONDS else COOL_DOWN_SECONDS
        val heater = heaterTemperature ?: skinBase
        val newHeater = heater + (heaterTarget - heater) * (1 - exp(-stepSeconds / tau))
        heaterTemperature = newHeater
        val skin = if (engine.consumeSensorFault(side)) {
            FAULT_TEMPERATURE_C
        } else {
            skinBase + (newHeater - skinBase).coerceAtLeast(0.0) * 0.7 + random.nextGaussian(sd = 0.04)
        }

        // Massage
        val massageLevel = if (massage.active) massage.intensity / 100.0 else 0.0
        val motion = (physiology.motion + massageLevel * 0.08 + random.nextGaussian(sd = 0.005)).coerceIn(0.0, 1.0)
        val heartRate = physiology.heartRateBpm - massageLevel * 1.5 + random.nextGaussian(sd = 0.8)

        samples.tryEmit(
            SensorSample(
                side = side,
                timestamp = tick.time,
                heartRateBpm = heartRate.roundToInt(),
                hrvRmssdMs = physiology.hrvRmssdMs * (1 + random.nextGaussian(sd = 0.04)),
                spo2Percent = (physiology.spo2Percent + random.nextGaussian(sd = 0.3)).roundToInt().coerceIn(85, 100),
                skinTemperatureC = skin,
                motion = motion,
                heaterTemperatureC = if (heat.active) newHeater else null,
            ),
        )
    }

    private fun currentBattery(): Int = (batteryStart - floor(batteryUsed)).toInt().coerceIn(0, 100)

    companion object {
        const val HEAT_LEVELS = 5

        /** Unplausibler Messwert für den simulierten Sensorfehler. */
        const val FAULT_TEMPERATURE_C = 55.0
        private const val BASE_DRAIN = 1.5
        private const val HEAT_DRAIN_PER_LEVEL = 3.0
        private const val MASSAGE_DRAIN = 5.0
        private const val HEAT_UP_SECONDS = 180.0
        private const val COOL_DOWN_SECONDS = 300.0
    }
}
