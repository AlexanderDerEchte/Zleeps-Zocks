package at.zocks.zleep.domain.export

import at.zocks.zleep.domain.analysis.SleepScoreCalculator
import at.zocks.zleep.domain.model.Night
import at.zocks.zleep.domain.model.NightData
import at.zocks.zleep.domain.model.NightSummary
import at.zocks.zleep.domain.model.SleepStage
import at.zocks.zleep.domain.model.Tag
import at.zocks.zleep.domain.repository.NightRepository
import at.zocks.zleep.domain.repository.NightSummaryRepository
import at.zocks.zleep.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * CSV nach RFC 4180: Komma als Trenner, Punkt als Dezimalzeichen, Zeitpunkte als lokale
 * Zeit mit Offset (ISO 8601). Fehlende Werte bleiben leer – nichts wird geschätzt.
 */
object CsvFormat {

    val NIGHT_COLUMNS = listOf(
        "night_date", "start", "end", "sleep_onset", "final_wake", "source",
        "time_in_bed_min", "total_sleep_min", "sleep_latency_min", "efficiency_pct", "wake_after_onset_min",
        "awakenings", "deep_min", "light_min", "rem_min", "awake_min",
        "resting_hr_bpm", "avg_hr_bpm", "avg_hrv_rmssd_ms", "avg_spo2_pct", "avg_skin_temp_c",
        "score", "heat", "massage", "tags", "note",
    )

    val MEASUREMENT_COLUMNS = listOf(
        "night_date", "epoch_start", "side", "stage", "heart_rate_bpm", "hrv_rmssd_ms", "spo2_pct",
        "skin_temp_c", "motion", "sample_count",
    )

    private val TIME: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun row(fields: List<String?>): String = fields.joinToString(",") { escape(it.orEmpty()) } + "\r\n"

    /** Felder mit Komma, Anführungszeichen oder Zeilenumbruch in Anführungszeichen, `"` verdoppelt. */
    fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

    fun nightRow(night: Night, summary: NightSummary?, goal: Duration, tagLabels: List<String>, zone: ZoneId): List<String?> {
        val score = summary?.let { SleepScoreCalculator.calculate(it, goal)?.value }
        return listOf(
            (summary?.nightDate ?: night.nightOf(zone)).toString(),
            time(night.start, zone),
            night.end?.let { time(it, zone) },
            night.sleepOnset?.let { time(it, zone) },
            night.finalWake?.let { time(it, zone) },
            night.source.name.lowercase(),
            summary?.timeInBed?.let { minutes(it) },
            summary?.totalSleep?.let { minutes(it) },
            summary?.sleepLatency?.let { minutes(it) },
            summary?.efficiency?.let { decimal(it * 100, 1) },
            summary?.wakeAfterOnset?.let { minutes(it) },
            summary?.awakenings?.toString(),
            summary?.stageMinutes?.get(SleepStage.DEEP)?.toString(),
            summary?.stageMinutes?.get(SleepStage.LIGHT)?.toString(),
            summary?.stageMinutes?.get(SleepStage.REM)?.toString(),
            summary?.stageMinutes?.get(SleepStage.AWAKE)?.toString(),
            summary?.restingHeartRateBpm?.let { decimal(it, 1) },
            summary?.avgHeartRateBpm?.let { decimal(it, 1) },
            summary?.avgHrvRmssdMs?.let { decimal(it, 1) },
            summary?.avgSpo2Percent?.let { decimal(it, 1) },
            summary?.avgSkinTemperatureC?.let { decimal(it, 2) },
            score?.toString(),
            summary?.heatUsed?.let { if (it) "1" else "0" },
            summary?.massageUsed?.let { if (it) "1" else "0" },
            tagLabels.joinToString(";"),
            night.note,
        )
    }

    /** Eine Zeile je Socke und Epoche, mit der geschätzten Phase der Epoche. */
    fun measurementRows(data: NightData, zone: ZoneId): List<List<String?>> {
        val nightDate = data.night.nightOf(zone).toString()
        val stages = data.stages.associate { it.start to it.stage }
        return data.measurements.sortedWith(compareBy({ it.start }, { it.side })).map { m ->
            listOf(
                nightDate,
                time(m.start, zone),
                m.side.name.lowercase(),
                stages[m.start]?.name?.lowercase(),
                m.heartRateBpm?.let { decimal(it, 1) },
                m.hrvRmssdMs?.let { decimal(it, 1) },
                m.spo2Percent?.let { decimal(it, 1) },
                m.skinTemperatureC?.let { decimal(it, 2) },
                m.motion?.let { decimal(it, 3) },
                m.sampleCount.toString(),
            )
        }
    }

    private fun time(instant: Instant, zone: ZoneId) = TIME.format(instant.atZone(zone).toOffsetDateTime())

    private fun minutes(duration: Duration) = duration.toMinutes().toString()

    private fun decimal(value: Double, digits: Int) = String.format(Locale.ROOT, "%.${digits}f", value)
}

/** Schreibt alle Nächte bzw. alle Messwerte als CSV. [tagLabel] übersetzt eingebaute Tags. */
class CsvExporter(
    private val nights: NightRepository,
    private val summaries: NightSummaryRepository,
    private val settings: SettingsRepository,
) {
    /** Liefert die Anzahl der geschriebenen Nächte. */
    suspend fun writeNights(out: Appendable, zone: ZoneId, tagLabel: (Tag) -> String): Int {
        val goal = Duration.ofMinutes(settings.settings.first().sleepGoalMinutes.toLong())
        val byId = summaries.observeSummaries().first().associateBy { it.nightId }
        val all = nights.observeNights().first().filter { it.end != null }.sortedBy { it.start }
        out.append(CsvFormat.row(CsvFormat.NIGHT_COLUMNS))
        all.forEach { night ->
            out.append(CsvFormat.row(CsvFormat.nightRow(night, byId[night.id], goal, night.tags.map(tagLabel), zone)))
        }
        return all.size
    }

    /** Liefert die Anzahl der geschriebenen Nächte. Nacht für Nacht, damit der Speicher klein bleibt. */
    suspend fun writeMeasurements(out: Appendable, zone: ZoneId): Int {
        val all = nights.observeNights().first().filter { it.end != null }.sortedBy { it.start }
        out.append(CsvFormat.row(CsvFormat.MEASUREMENT_COLUMNS))
        all.forEach { night ->
            val data = nights.getNightData(night.id) ?: return@forEach
            CsvFormat.measurementRows(data, zone).forEach { out.append(CsvFormat.row(it)) }
        }
        return all.size
    }
}
