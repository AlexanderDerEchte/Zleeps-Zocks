package at.zocks.zleep.domain.model

import java.time.Instant

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING }

/** Zonen der Vibrationsmotoren, siehe Produktbild: Ferse, Fußgewölbe, Ballen, Zehen. */
enum class MassageZone { HEEL, ARCH, BALL, TOES }

/**
 * Was ein Gerät kann. Die UI blendet nur Funktionen ein, die hier gemeldet werden;
 * fehlende Messgrößen werden als „nicht verfügbar“ gezeigt.
 */
data class DeviceCapabilities(
    val heartRate: Boolean,
    val hrv: Boolean,
    val spo2: Boolean,
    val skinTemperature: Boolean,
    val motion: Boolean,
    val massageZones: Set<MassageZone>,
    /** Anzahl Heizstufen, 0 = keine Heizung. */
    val heatLevels: Int,
) {
    companion object {
        val None = DeviceCapabilities(
            heartRate = false,
            hrv = false,
            spo2 = false,
            skinTemperature = false,
            motion = false,
            massageZones = emptySet(),
            heatLevels = 0,
        )
    }
}

/**
 * Ein Messpunkt einer Socke. Jeder Wert ist `null`, wenn der Sensor ihn nicht liefert –
 * er wird nie geschätzt.
 */
data class SensorSample(
    val side: SockSide,
    val timestamp: Instant,
    val heartRateBpm: Int?,
    val hrvRmssdMs: Double?,
    val spo2Percent: Int?,
    val skinTemperatureC: Double?,
    /** Bewegungsindex 0..1 (0 = völlig ruhig). */
    val motion: Double?,
    /** Temperatur am Heizelement, nur bei aktiver Heizung sinnvoll. */
    val heaterTemperatureC: Double?,
    val rrIntervalsMs: List<Int> = emptyList(),
)

data class HeatCommand(
    /** 1..[DeviceCapabilities.heatLevels]. */
    val level: Int,
    /** Gewünschte Temperatur am Fuß; `null` = nur nach Stufe heizen. */
    val targetTemperatureC: Double?,
)

data class HeatState(
    val active: Boolean,
    val level: Int,
    val targetTemperatureC: Double?,
) {
    companion object {
        val Off = HeatState(active = false, level = 0, targetTemperatureC = null)
    }
}

/** Ein Schritt eines Massagemusters: wie stark welche Zone wie lange vibriert (0..1). */
data class MassageStep(
    val durationMs: Long,
    val zoneLevels: Map<MassageZone, Float>,
)

data class MassagePattern(
    val id: String,
    val steps: List<MassageStep>,
)

data class MassageCommand(
    val pattern: MassagePattern,
    /** Gesamtintensität 0..100, skaliert die Zonenstärken. */
    val intensity: Int,
)

data class MassageState(
    val active: Boolean,
    val patternId: String?,
    val intensity: Int,
) {
    companion object {
        val Off = MassageState(active = false, patternId = null, intensity = 0)
    }
}
