package at.zocks.zleep.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Kennzahlen einer Nacht, einmal berechnet und gespeichert, damit Trends und Erkenntnisse
 * nicht jedes Mal alle Messwerte lesen müssen. `null` = nicht bestimmbar (fehlende Daten).
 */
data class NightSummary(
    val nightId: Long,
    val nightDate: LocalDate,
    val start: Instant,
    val end: Instant?,
    val sleepOnset: Instant?,
    val finalWake: Instant?,
    /** Gesamte Aufzeichnung (im Bett). */
    val timeInBed: Duration,
    /** Geschlafene Zeit im Schlaffenster (ohne Wachphasen). */
    val totalSleep: Duration?,
    /** Zeit vom Beginn der Aufzeichnung bis zum Einschlafen. */
    val sleepLatency: Duration?,
    /** Anteil geschlafener Zeit an der Zeit im Bett (0..1). */
    val efficiency: Double?,
    /** Wachzeit zwischen Einschlafen und Aufwachen. */
    val wakeAfterOnset: Duration?,
    /** Wachphasen von mindestens einer Minute zwischen Einschlafen und Aufwachen. */
    val awakenings: Int?,
    val stageMinutes: Map<SleepStage, Int>,
    /** Niedrigster 5-Minuten-Mittelwert des Pulses im Schlaf. */
    val restingHeartRateBpm: Double?,
    val avgHeartRateBpm: Double?,
    val avgHrvRmssdMs: Double?,
    val avgSpo2Percent: Double?,
    val avgSkinTemperatureC: Double?,
    val heatUsed: Boolean,
    val massageUsed: Boolean,
    val gapDuration: Duration,
) {
    val asleepStageMinutes: Int get() = stageMinutes.filterKeys { it != SleepStage.AWAKE }.values.sum()

    /** Anteil einer Phase an der Schlafzeit in Prozent (ohne Wach), `null` ohne Phasen. */
    fun stageShare(stage: SleepStage): Int? {
        val asleep = asleepStageMinutes
        if (asleep == 0 || stage == SleepStage.AWAKE) return null
        return Math.round((stageMinutes[stage] ?: 0) * 100.0 / asleep).toInt()
    }

    /** Tief- plus REM-Anteil (0..1), `null` ohne Phasen. */
    val restorativeShare: Double?
        get() = asleepStageMinutes.takeIf { it > 0 }
            ?.let { ((stageMinutes[SleepStage.DEEP] ?: 0) + (stageMinutes[SleepStage.REM] ?: 0)).toDouble() / it }
}
