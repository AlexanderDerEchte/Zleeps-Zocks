package at.zocks.zleep.testing

import at.zocks.zleep.device.ble.GattConnection
import at.zocks.zleep.device.ble.GattEvent
import at.zocks.zleep.device.ble.SockBleProtocol
import at.zocks.zleep.domain.model.DeviceCapabilities
import at.zocks.zleep.domain.model.MassageZone
import at.zocks.zleep.domain.model.SensorSample
import at.zocks.zleep.domain.model.SockSide
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.time.Instant
import java.util.UUID

/**
 * Attrappe der Socken-Firmware: spricht [SockBleProtocol] über eine [GattConnection] und
 * merkt sich, was ankam. Verbindungsfehler, abgerissene Verbindungen und fehlende
 * Fähigkeiten lassen sich gezielt auslösen.
 */
class FakeSockFirmware(
    val address: String,
    val side: SockSide,
    var capabilities: DeviceCapabilities? = FullBleCapabilities,
    var battery: Int = 80,
    var maxMtu: Int = 185,
) {
    var heating = false
    var heatLevel = 0
    var target: Double? = null
    var massaging = false
    var intensity = 0
    var overheatCutoff = false

    /** So viele Verbindungsversuche schlagen noch fehl. */
    var failConnects = 0
    var failWrites = false
    var bonded = false

    /** Vollständige Befehle (nach dem Zusammensetzen der Rahmen). */
    val commands = mutableListOf<ByteArray>()
    val framesReceived = mutableListOf<ByteArray>()
    var opened = 0
        private set

    var current: Connection? = null
        private set

    fun open(): GattConnection = Connection().also {
        opened++
        current = it
    }

    fun status() = SockBleProtocol.Status(heating, heatLevel, target, massaging, intensity, overheatCutoff)

    /** Sendet einen Messwert, wie ihn der Sensor liefern würde. */
    fun sendSample(heartRate: Int? = 58, skin: Double? = 33.0, motion: Double? = 0.02, rmssd: Double? = 48.0) {
        val sample = SensorSample(side, Instant.EPOCH, heartRate, rmssd, null, skin, motion, null)
        current?.notify(SockBleProtocol.SENSOR, SockBleProtocol.encodeSensor(sample))
    }

    fun sendStatus() = current?.notify(SockBleProtocol.STATUS, SockBleProtocol.encodeStatus(status()))

    /** Funkabbruch. */
    fun drop() = current?.drop()

    private fun onCommand(bytes: ByteArray) {
        commands += bytes
        when (bytes[0].toInt() and 0xFF) {
            SockBleProtocol.OP_HEAT -> {
                heating = true
                heatLevel = bytes[1].toInt() and 0xFF
            }
            SockBleProtocol.OP_HEAT_STOP -> heating = false
            SockBleProtocol.OP_MASSAGE -> {
                massaging = true
                intensity = bytes[1].toInt() and 0xFF
            }
            SockBleProtocol.OP_MASSAGE_STOP -> massaging = false
        }
        sendStatus()
    }

    inner class Connection : GattConnection {
        private val channel = Channel<GattEvent>(Channel.UNLIMITED)
        private val pending = mutableListOf<ByteArray>()
        @Volatile
        var connected = false
            private set
        val notifying = mutableSetOf<UUID>()
        var mtu = SockBleProtocol.PREFERRED_MTU
            private set

        override val events = channel.receiveAsFlow()
        override val isBonded: Boolean get() = bonded

        override suspend fun connect(): Boolean {
            if (failConnects > 0) {
                failConnects--
                return false
            }
            connected = true
            return true
        }

        override suspend fun bond(): Boolean {
            bonded = true
            return true
        }

        override suspend fun requestMtu(mtu: Int): Int = minOf(mtu, maxMtu).also { this.mtu = it }

        override suspend fun read(service: UUID, characteristic: UUID): ByteArray? {
            if (!connected) return null
            return when (characteristic) {
                SockBleProtocol.CAPABILITIES -> capabilities?.let(SockBleProtocol::encodeCapabilities)
                SockBleProtocol.STATUS -> SockBleProtocol.encodeStatus(status())
                SockBleProtocol.BATTERY_LEVEL -> byteArrayOf(battery.toByte())
                else -> null
            }
        }

        override suspend fun write(service: UUID, characteristic: UUID, value: ByteArray): Boolean {
            if (!connected || failWrites || characteristic != SockBleProtocol.CONTROL) return false
            framesReceived += value
            pending += value
            if (value[0].toInt() and 0x80 != 0) {
                SockBleProtocol.reassemble(pending.toList())?.let(::onCommand)
                pending.clear()
            }
            return true
        }

        override suspend fun enableNotifications(service: UUID, characteristic: UUID): Boolean {
            if (!connected) return false
            notifying += characteristic
            return true
        }

        override fun close() {
            connected = false
            channel.close()
        }

        fun notify(characteristic: UUID, value: ByteArray) {
            if (connected && characteristic in notifying) channel.trySend(GattEvent.Notification(characteristic, value))
        }

        fun drop() {
            connected = false
            channel.trySend(GattEvent.Disconnected(status = 8))
        }
    }
}

/** Socke mit allen Sensoren, vier Massagezonen und fünf Heizstufen. */
val FullBleCapabilities = DeviceCapabilities(
    heartRate = true,
    hrv = true,
    spo2 = false,
    skinTemperature = true,
    motion = true,
    massageZones = MassageZone.entries.toSet(),
    heatLevels = 5,
)
