package at.zocks.zleep.domain.health

import at.zocks.zleep.domain.model.EPOCH_LENGTH
import at.zocks.zleep.domain.model.NightData
import at.zocks.zleep.domain.model.NightSource
import at.zocks.zleep.domain.model.SleepStage
import java.time.Instant

/** Abschnitt einer Schlafphase für Health Connect. */
data class HealthStage(val start: Instant, val end: Instant, val stage: SleepStage)

/** Ein Pulswert (Mittel beider Socken über eine Epoche). */
data class HealthHeartRate(val time: Instant, val bpm: Long)

/** Was von einer Nacht nach Health Connect geht. [id] macht erneutes Übertragen idempotent. */
data class HealthSleepSession(
    val id: String,
    val start: Instant,
    val end: Instant,
    val stages: List<HealthStage>,
    val heartRate: List<HealthHeartRate>,
    val notes: String?,
)

enum class HealthAvailability { AVAILABLE, NOT_INSTALLED, UPDATE_REQUIRED, NOT_SUPPORTED }

/** Zugang zu Health Connect (Umsetzung in `data`). */
interface HealthConnectGateway {
    /** Benötigte Berechtigungen (Schreiben von Schlaf und Puls), für die Freigabe-Anfrage. */
    val requiredPermissions: Set<String>

    fun availability(): HealthAvailability
    suspend fun hasPermissions(): Boolean

    /** Schreibt oder ersetzt die Nacht (gleiche [HealthSleepSession.id]). */
    suspend fun write(session: HealthSleepSession)
}

object HealthExportMapper {

    /** Präfix der eigenen Kennung in Health Connect. */
    const val ID_PREFIX = "zocks-night-"

    /**
     * Nur abgeschlossene Nächte vom echten Gerät – Demo- und Simulator-Nächte sind keine
     * echten Messungen und gehören nicht in Gesundheitsdaten.
     */
    fun isExportable(data: NightData): Boolean = data.night.end != null && data.night.source == NightSource.DEVICE

    fun map(data: NightData): HealthSleepSession? {
        if (!isExportable(data)) return null
        val night = data.night
        val stages = mutableListOf<HealthStage>()
        data.stages.sortedBy { it.start }.forEach { epoch ->
            val end = epoch.start.plus(EPOCH_LENGTH)
            val last = stages.lastOrNull()
            if (last != null && last.stage == epoch.stage && last.end == epoch.start) {
                stages[stages.lastIndex] = last.copy(end = end)
            } else {
                stages += HealthStage(epoch.start, end, epoch.stage)
            }
        }
        // Puls je Epoche über beide Socken mitteln; Epochen ohne Puls fehlen einfach.
        val heartRate = data.measurements
            .groupBy { it.start }
            .toSortedMap()
            .mapNotNull { (time, sides) ->
                sides.mapNotNull { it.heartRateBpm }.takeIf { it.isNotEmpty() }?.let { HealthHeartRate(time, Math.round(it.average())) }
            }
        val end = night.end!!
        return HealthSleepSession(
            id = ID_PREFIX + night.id,
            start = night.start,
            end = end,
            stages = stages.map { it.copy(end = minOf(it.end, end)) }.filter { it.start.isBefore(it.end) },
            heartRate = heartRate.filter { it.time.isBefore(end) },
            notes = night.note,
        )
    }
}
