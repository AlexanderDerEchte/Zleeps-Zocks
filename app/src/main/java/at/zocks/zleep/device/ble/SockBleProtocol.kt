package at.zocks.zleep.device.ble

import at.zocks.zleep.domain.model.DeviceCapabilities
import at.zocks.zleep.domain.model.HeatCommand
import at.zocks.zleep.domain.model.MassageCommand
import at.zocks.zleep.domain.model.MassageZone
import at.zocks.zleep.domain.model.SensorSample
import at.zocks.zleep.domain.model.SockSide
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Das BLE-Protokoll der Socken – **die einzige Stelle** mit UUIDs und Byte-Formaten.
 * Alle Werte hier sind Platzhalter, bis die Firmware feststeht; nur diese Datei (und ihre
 * Tests) muss dann angepasst werden.
 *
 * Jede Socke ist ein eigenes BLE-Gerät. Alle Mehrbyte-Zahlen sind Little Endian.
 *
 * **Werbung (Advertising):** Dienst [SERVICE] und Herstellerdaten unter [MANUFACTURER_ID]:
 * `[Protokollversion u8][Seite u8: 0 = links, 1 = rechts, 0xFF = unbekannt][Akku u8 %, 0xFF = unbekannt]`.
 *
 * **Messwerte** ([SENSOR], Notify), Version 1:
 * ```
 * 0      Version u8 (= 1)
 * 1      Puls u8 bpm (0 = nicht verfügbar)
 * 2–3    RMSSD u16 in 0,1 ms (0xFFFF = nicht verfügbar)
 * 4      SpO2 u8 % (0xFF = nicht verfügbar)
 * 5–6    Hauttemperatur i16 in 0,01 °C (0x7FFF = nicht verfügbar)
 * 7      Bewegung u8 0..200 → 0..1 (0xFF = nicht verfügbar)
 * 8–9    Temperatur am Heizelement i16 in 0,01 °C (0x7FFF = nicht verfügbar)
 * 10     Anzahl RR-Intervalle n u8
 * 11…    n × RR-Intervall u16 in ms
 * ```
 * Der Zeitpunkt eines Messwerts ist die Empfangszeit am Handy (die Socke hat keine Uhr).
 *
 * **Zustand** ([STATUS], Read + Notify): `[Version][Heizt u8][Stufe u8][Ziel i16 0,01 °C, 0x7FFF = keins][Massage aktiv u8][Intensität u8][Fehler u8]`;
 * Fehler-Bit 0 = Überhitzungsschutz der Firmware hat abgeschaltet.
 *
 * **Fähigkeiten** ([CAPABILITIES], Read): `[Version][Sensoren-Bits][Massagezonen-Bits][Heizstufen u8]`;
 * Sensoren: Bit 0 Puls, 1 HRV, 2 SpO2, 3 Hauttemperatur, 4 Bewegung. Zonen: Bit 0 Ferse,
 * 1 Gewölbe, 2 Ballen, 3 Zehen.
 *
 * **Befehle** ([CONTROL], Write mit Antwort): Opcode + Nutzdaten, in Rahmen zerlegt
 * ([frames]): jeder Rahmen beginnt mit `[Bit 7 = letzter Rahmen | Bits 0–6 = laufende Nummer]`.
 * - `0x01` Heizen: `[Stufe u8][Ziel i16 0,01 °C, 0x7FFF = nur Stufe]`
 * - `0x02` Heizen aus
 * - `0x10` Massage: `[Intensität u8 0..100][Schritte n u8]` + n × `[Dauer u16 in 10 ms][Ferse][Gewölbe][Ballen][Zehen]` (je u8 0..255)
 * - `0x11` Massage aus: `[Ausklingen u16 in 100 ms]`
 *
 * **Akku:** Standard-Battery-Service ([BATTERY_SERVICE] / [BATTERY_LEVEL]).
 */
object SockBleProtocol {

    val SERVICE: UUID = UUID.fromString("7a0c1000-5a6b-4c3d-9e2f-0123456789ab")
    val SENSOR: UUID = UUID.fromString("7a0c1001-5a6b-4c3d-9e2f-0123456789ab")
    val STATUS: UUID = UUID.fromString("7a0c1002-5a6b-4c3d-9e2f-0123456789ab")
    val CONTROL: UUID = UUID.fromString("7a0c1003-5a6b-4c3d-9e2f-0123456789ab")
    val CAPABILITIES: UUID = UUID.fromString("7a0c1004-5a6b-4c3d-9e2f-0123456789ab")

    val BATTERY_SERVICE: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    val BATTERY_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    /** Client Characteristic Configuration Descriptor zum Einschalten von Notify. */
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** 0xFFFF ist die Test-ID der Bluetooth SIG – durch die eigene Hersteller-ID ersetzen. */
    const val MANUFACTURER_ID = 0xFFFF

    const val VERSION = 1

    /** Verlangt die Firmware eine Kopplung (Bonding) vor dem Zugriff? */
    const val REQUIRES_BOND = false

    /** Gewünschte MTU; die Rahmen passen sich der ausgehandelten an. */
    const val PREFERRED_MTU = 185

    const val OP_HEAT = 0x01
    const val OP_HEAT_STOP = 0x02
    const val OP_MASSAGE = 0x10
    const val OP_MASSAGE_STOP = 0x11

    private const val NO_I16 = 0x7FFF
    private const val NO_U16 = 0xFFFF
    private const val NO_U8 = 0xFF
    private const val MOTION_SCALE = 200.0

    /** Was eine Socke in ihrer Werbung verrät. */
    data class Advertisement(val side: SockSide?, val batteryPercent: Int?)

    data class Status(
        val heating: Boolean,
        val heatLevel: Int,
        val targetTemperatureC: Double?,
        val massaging: Boolean,
        val massageIntensity: Int,
        val firmwareOverheatCutoff: Boolean,
    )

    fun parseAdvertisement(data: ByteArray?): Advertisement? {
        if (data == null || data.size < 3 || data.u8(0) != VERSION) return null
        val side = when (data.u8(1)) {
            0 -> SockSide.LEFT
            1 -> SockSide.RIGHT
            else -> null
        }
        return Advertisement(side, data.u8(2).takeIf { it in 0..100 })
    }

    /** Messwert-Paket; `null`, wenn es zu kurz ist oder eine unbekannte Version hat. */
    fun parseSensor(data: ByteArray, side: SockSide, receivedAt: Instant): SensorSample? {
        if (data.size < 11 || data.u8(0) != VERSION) return null
        val rrCount = data.u8(10)
        if (data.size < 11 + rrCount * 2) return null
        return SensorSample(
            side = side,
            timestamp = receivedAt,
            heartRateBpm = data.u8(1).takeIf { it != 0 },
            hrvRmssdMs = data.u16(2).takeIf { it != NO_U16 }?.let { it / 10.0 },
            spo2Percent = data.u8(4).takeIf { it != NO_U8 },
            skinTemperatureC = data.i16(5).takeIf { it != NO_I16 }?.let { it / 100.0 },
            motion = data.u8(7).takeIf { it != NO_U8 }?.let { (it / MOTION_SCALE).coerceAtMost(1.0) },
            heaterTemperatureC = data.i16(8).takeIf { it != NO_I16 }?.let { it / 100.0 },
            rrIntervalsMs = List(rrCount) { data.u16(11 + it * 2) },
        )
    }

    fun parseStatus(data: ByteArray): Status? {
        if (data.size < 8 || data.u8(0) != VERSION) return null
        return Status(
            heating = data.u8(1) != 0,
            heatLevel = data.u8(2),
            targetTemperatureC = data.i16(3).takeIf { it != NO_I16 }?.let { it / 100.0 },
            massaging = data.u8(5) != 0,
            massageIntensity = data.u8(6),
            firmwareOverheatCutoff = data.u8(7) and 0x01 != 0,
        )
    }

    fun parseCapabilities(data: ByteArray): DeviceCapabilities? {
        if (data.size < 4 || data.u8(0) != VERSION) return null
        val sensors = data.u8(1)
        val zones = data.u8(2)
        return DeviceCapabilities(
            heartRate = sensors bit 0,
            hrv = sensors bit 1,
            spo2 = sensors bit 2,
            skinTemperature = sensors bit 3,
            motion = sensors bit 4,
            massageZones = ZONE_ORDER.filterIndexed { index, _ -> zones bit index }.toSet(),
            heatLevels = data.u8(3),
        )
    }

    fun parseBattery(data: ByteArray): Int? = data.firstOrNull()?.toInt()?.and(0xFF)?.takeIf { it in 0..100 }

    fun encodeHeat(command: HeatCommand): ByteArray = bytes(OP_HEAT) +
        u8(command.level.coerceIn(0, 255)) +
        i16(command.targetTemperatureC?.let { (it * 100).roundToInt() } ?: NO_I16)

    fun encodeHeatStop(): ByteArray = bytes(OP_HEAT_STOP)

    fun encodeMassage(command: MassageCommand): ByteArray {
        val steps = command.pattern.steps.take(MAX_STEPS)
        var out = bytes(OP_MASSAGE) + u8(command.intensity.coerceIn(0, 100)) + u8(steps.size)
        steps.forEach { step ->
            out += u16((step.durationMs / 10).coerceIn(0, NO_U16.toLong()).toInt())
            ZONE_ORDER.forEach { zone -> out += u8(((step.zoneLevels[zone] ?: 0f).coerceIn(0f, 1f) * 255).roundToInt()) }
        }
        return out
    }

    fun encodeMassageStop(fadeOutMs: Long): ByteArray =
        bytes(OP_MASSAGE_STOP) + u16((fadeOutMs / 100).coerceIn(0, NO_U16.toLong()).toInt())

    /**
     * Zerlegt einen Befehl in Rahmen für eine MTU von [mtu] (3 Byte ATT-Kopf abgezogen).
     * Auch ein kurzer Befehl ist ein Rahmen (Kopf 0x80).
     */
    fun frames(payload: ByteArray, mtu: Int): List<ByteArray> {
        val chunk = (mtu - ATT_HEADER - 1).coerceAtLeast(1)
        val parts = payload.toList().chunked(chunk).ifEmpty { listOf(emptyList()) }
        require(parts.size <= MAX_FRAMES) { "Befehl zu lang" }
        return parts.mapIndexed { index, part ->
            val header = index or (if (index == parts.lastIndex) LAST_FRAME else 0)
            byteArrayOf(header.toByte()) + part.toByteArray()
        }
    }

    /** Setzt Rahmen wieder zusammen (Gegenstück zu [frames], für Tests und Fake-Geräte). */
    fun reassemble(frames: List<ByteArray>): ByteArray? {
        if (frames.isEmpty()) return null
        frames.forEachIndexed { index, frame ->
            val header = frame.u8(0)
            if (header and 0x7F != index) return null
            if ((header and LAST_FRAME != 0) != (index == frames.lastIndex)) return null
        }
        return frames.fold(ByteArray(0)) { acc, frame -> acc + frame.copyOfRange(1, frame.size) }
    }

    // Gegenstücke aus Sicht der Firmware: für Rundlauf-Tests und Geräte-Attrappen.

    fun encodeSensor(sample: SensorSample): ByteArray =
        bytes(VERSION) +
            u8(sample.heartRateBpm ?: 0) +
            u16(sample.hrvRmssdMs?.let { (it * 10).roundToInt() } ?: NO_U16) +
            u8(sample.spo2Percent ?: NO_U8) +
            i16(sample.skinTemperatureC?.let { (it * 100).roundToInt() } ?: NO_I16) +
            u8(sample.motion?.let { (it * MOTION_SCALE).roundToInt() } ?: NO_U8) +
            i16(sample.heaterTemperatureC?.let { (it * 100).roundToInt() } ?: NO_I16) +
            u8(sample.rrIntervalsMs.size) +
            sample.rrIntervalsMs.fold(ByteArray(0)) { acc, rr -> acc + u16(rr) }

    fun encodeStatus(status: Status): ByteArray =
        bytes(VERSION, if (status.heating) 1 else 0, status.heatLevel) +
            i16(status.targetTemperatureC?.let { (it * 100).roundToInt() } ?: NO_I16) +
            bytes(if (status.massaging) 1 else 0, status.massageIntensity, if (status.firmwareOverheatCutoff) 1 else 0)

    fun encodeCapabilities(capabilities: DeviceCapabilities): ByteArray {
        val sensors = listOf(
            capabilities.heartRate,
            capabilities.hrv,
            capabilities.spo2,
            capabilities.skinTemperature,
            capabilities.motion,
        ).foldIndexed(0) { index, acc, on -> if (on) acc or (1 shl index) else acc }
        val zones = ZONE_ORDER.foldIndexed(0) { index, acc, zone -> if (zone in capabilities.massageZones) acc or (1 shl index) else acc }
        return bytes(VERSION, sensors, zones, capabilities.heatLevels)
    }

    fun encodeAdvertisement(advertisement: Advertisement): ByteArray = bytes(
        VERSION,
        when (advertisement.side) {
            SockSide.LEFT -> 0
            SockSide.RIGHT -> 1
            else -> NO_U8
        },
        advertisement.batteryPercent ?: NO_U8,
    )

    /** Reihenfolge der Zonen in Fähigkeiten-Bits und Massage-Schritten. */
    val ZONE_ORDER = listOf(MassageZone.HEEL, MassageZone.ARCH, MassageZone.BALL, MassageZone.TOES)

    private const val ATT_HEADER = 3
    private const val LAST_FRAME = 0x80
    private const val MAX_FRAMES = 0x80
    private const val MAX_STEPS = 255

    private fun ByteArray.u8(index: Int) = this[index].toInt() and 0xFF
    private fun ByteArray.u16(index: Int) = u8(index) or (u8(index + 1) shl 8)
    private fun ByteArray.i16(index: Int) = u16(index).let { if (it == NO_I16) it else it.toShort().toInt() }
    private infix fun Int.bit(index: Int) = (this shr index) and 1 == 1

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
    private fun u8(value: Int) = byteArrayOf(value.toByte())
    private fun u16(value: Int) = byteArrayOf((value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte())
    private fun i16(value: Int) = u16(value and 0xFFFF)
}
