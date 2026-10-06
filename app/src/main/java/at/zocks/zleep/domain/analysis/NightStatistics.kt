package at.zocks.zleep.domain.analysis

import at.zocks.zleep.domain.model.EPOCH_LENGTH
import at.zocks.zleep.domain.model.EpochMeasurement
import at.zocks.zleep.domain.model.NightData
import at.zocks.zleep.domain.model.SleepStage
import java.time.Duration
import java.time.Instant

/** Einfache Kennzahlen einer Nacht. Fehlende Messwerte bleiben `null`. */
data class NightStatistics(
    val sleepWindow: Duration?,
    val stageMinutes: Map<SleepStage, Int>,
    val avgHeartRateBpm: Double?,
    val avgHrvRmssdMs: Double?,
    val avgSpo2Percent: Double?,
    val avgSkinTemperatureC: Double?,
    val gapDuration: Duration,
) {
    /** Anteil einer Phase an der Schlafzeit (ohne Wach) in Prozent, `null` ohne Phasendaten. */
    fun stageShare(stage: SleepStage): Int? {
        val asleep = stageMinutes.filterKeys { it != SleepStage.AWAKE }.values.sum()
        if (asleep == 0 || stage == SleepStage.AWAKE) return null
        return ((stageMinutes[stage] ?: 0) * 100.0 / asleep).toInt()
    }
}

object NightStatisticsCalculator {

    fun calculate(data: NightData, now: Instant): NightStatistics {
        val night = data.night
        val onset = night.sleepOnset
        val wake = night.finalWake

        // Messwerte nur aus dem Schlaffenster; ohne Fenster die ganze Nacht.
        val inWindow: (EpochMeasurement) -> Boolean = { m ->
            (onset == null || !m.start.isBefore(onset)) && (wake == null || m.start.isBefore(wake))
        }
        val relevant = data.measurements.filter(inWindow)

        val epochMinutes = EPOCH_LENGTH.seconds / 60.0
        val stageMinutes = data.stages
            .groupingBy { it.stage }
            .eachCount()
            .mapValues { (_, count) -> (count * epochMinutes).toInt() }

        val gapDuration = data.gaps.fold(Duration.ZERO) { sum, gap ->
            sum + Duration.between(gap.start, gap.end ?: night.end ?: now)
        }

        return NightStatistics(
            sleepWindow = night.sleepWindow,
            stageMinutes = stageMinutes,
            avgHeartRateBpm = relevant.averageOf { it.heartRateBpm },
            avgHrvRmssdMs = relevant.averageOf { it.hrvRmssdMs },
            avgSpo2Percent = relevant.averageOf { it.spo2Percent },
            avgSkinTemperatureC = relevant.averageOf { it.skinTemperatureC },
            gapDuration = gapDuration,
        )
    }

    private inline fun List<EpochMeasurement>.averageOf(selector: (EpochMeasurement) -> Double?): Double? =
        mapNotNull(selector).takeIf { it.isNotEmpty() }?.average()
}
