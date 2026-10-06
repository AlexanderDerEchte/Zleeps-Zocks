package at.zocks.zleep.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Länge einer Auswertungs-Epoche. Alle gespeicherten Messwerte sind auf Epochen verdichtet. */
val EPOCH_LENGTH: Duration = Duration.ofSeconds(30)

enum class NightSource { DEVICE, SIMULATOR, DEMO }

data class Night(
    val id: Long,
    val start: Instant,
    val end: Instant?,
    /** Einschlafzeitpunkt (automatisch erkannt oder manuell korrigiert). */
    val sleepOnset: Instant?,
    /** Endgültiges Aufwachen. */
    val finalWake: Instant?,
    val source: NightSource,
    val note: String?,
    val tags: List<Tag>,
    /** Einschlafen/Aufwachen wurden von Hand korrigiert und werden nicht mehr automatisch überschrieben. */
    val sleepWindowManual: Boolean = false,
) {
    val isRecording: Boolean get() = end == null

    /** Schlafdauer von Einschlafen bis Aufwachen (inkl. kurzer Wachphasen), falls bekannt. */
    val sleepWindow: Duration?
        get() = if (sleepOnset != null && finalWake != null) Duration.between(sleepOnset, finalWake) else null

    /**
     * Kalendertag, dem die Nacht zugeordnet wird: Beginn vor 12 Uhr zählt zum Vortag
     * (01:30 am 6. Oktober = Nacht vom 5. Oktober).
     */
    fun nightOf(zone: ZoneId): LocalDate = start.atZone(zone).minusHours(12).toLocalDate()
}

/** Messwerte einer Socke, gemittelt über eine Epoche. `null` = nicht gemessen. */
data class EpochMeasurement(
    val side: SockSide,
    val start: Instant,
    val heartRateBpm: Double?,
    val hrvRmssdMs: Double?,
    val spo2Percent: Double?,
    val skinTemperatureC: Double?,
    val motion: Double?,
    val sampleCount: Int,
)

data class StageEpoch(
    val start: Instant,
    val stage: SleepStage,
)

enum class NightEventType { HEAT, MASSAGE, ROUTINE, ALARM, SAFETY_SHUTOFF }

/** Etwas, das während der Nacht passiert ist, z. B. Heizen von 22:40 bis 23:05. */
data class NightEvent(
    val id: Long = 0,
    val type: NightEventType,
    val side: SockSide,
    val start: Instant,
    val end: Instant?,
    val detail: String? = null,
)

/** Zeitraum ohne Daten, weil die Verbindung zu einer Socke unterbrochen war. */
data class ConnectionGap(
    val id: Long = 0,
    val side: SockSide,
    val start: Instant,
    val end: Instant?,
)

/**
 * Tag einer Nacht. Eingebaute Tags haben einen [key] und werden in der UI übersetzt,
 * eigene Tags haben ein [label].
 */
data class Tag(
    val id: Long,
    val key: String?,
    val label: String?,
) {
    companion object {
        const val CAFFEINE = "caffeine"
        const val SPORT = "sport"
        const val ALCOHOL = "alcohol"
        const val STRESS = "stress"
        const val LATE_MEAL = "late_meal"
        const val SCREEN_TIME = "screen_time"

        val BuiltInKeys = listOf(CAFFEINE, SPORT, ALCOHOL, STRESS, LATE_MEAL, SCREEN_TIME)
    }
}

/** Alle Daten einer Nacht für Auswertung und Detailansicht. */
data class NightData(
    val night: Night,
    val measurements: List<EpochMeasurement>,
    val stages: List<StageEpoch>,
    val events: List<NightEvent>,
    val gaps: List<ConnectionGap>,
)
