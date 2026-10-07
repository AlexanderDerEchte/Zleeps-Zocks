package at.zocks.zleep.domain.analysis

import at.zocks.zleep.domain.model.EPOCH_LENGTH
import at.zocks.zleep.domain.model.NightData
import at.zocks.zleep.domain.model.NightEventType
import at.zocks.zleep.domain.model.NightSummary
import at.zocks.zleep.domain.model.SleepStage
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Berechnet die Kennzahlen einer Nacht aus allen gespeicherten Daten. */
object NightSummarizer {

    /** Epochen für den Ruhepuls (5 Minuten). */
    private const val RESTING_WINDOW_EPOCHS = 10

    /** Kürzere Wachphasen zählen nicht als Aufwachen (1 Minute). */
    private const val MIN_AWAKENING_EPOCHS = 2

    fun summarize(data: NightData, zone: ZoneId, now: Instant): NightSummary {
        val night = data.night
        val end = night.end ?: now
        val onset = night.sleepOnset
        val wake = night.finalWake
        val inWindow: (Instant) -> Boolean = { t -> onset != null && wake != null && !t.isBefore(onset) && t.isBefore(wake) }

        val epochMinutes = EPOCH_LENGTH.seconds / 60.0
        val stageMinutes = data.stages.groupingBy { it.stage }.eachCount().mapValues { (_, n) -> Math.round(n * epochMinutes).toInt() }

        val windowStages = data.stages.filter { inWindow(it.start) }
        val window = if (onset != null && wake != null) Duration.between(onset, wake) else null
        val awakeInWindow = windowStages.count { it.stage == SleepStage.AWAKE }
        val wakeAfterOnset = if (window != null && windowStages.isNotEmpty()) EPOCH_LENGTH.multipliedBy(awakeInWindow.toLong()) else null
        val totalSleep = when {
            window == null -> null
            windowStages.isNotEmpty() -> EPOCH_LENGTH.multipliedBy(windowStages.count { it.stage != SleepStage.AWAKE }.toLong())
            else -> window
        }
        val timeInBed = Duration.between(night.start, end).coerceAtLeast(Duration.ZERO)

        // Pro Epoche beide Socken mitteln, dann den ruhigsten 5-Minuten-Abschnitt im Schlaf suchen.
        val sleepingHeartRate = data.measurements
            .filter { inWindow(it.start) }
            .groupBy { it.start }
            .toSortedMap()
            .values
            .mapNotNull { sides -> sides.mapNotNull { it.heartRateBpm }.takeIf { it.isNotEmpty() }?.average() }
        val resting = sleepingHeartRate.windowed(RESTING_WINDOW_EPOCHS).minOfOrNull { it.average() }

        val inSleep = data.measurements.filter { onset == null || inWindow(it.start) }

        return NightSummary(
            nightId = night.id,
            nightDate = night.nightOf(zone),
            start = night.start,
            end = night.end,
            sleepOnset = onset,
            finalWake = wake,
            timeInBed = timeInBed,
            totalSleep = totalSleep,
            sleepLatency = onset?.let { Duration.between(night.start, it).coerceAtLeast(Duration.ZERO) },
            efficiency = totalSleep?.takeIf { !timeInBed.isZero }?.let { it.toMillis().toDouble() / timeInBed.toMillis() }?.coerceIn(0.0, 1.0),
            wakeAfterOnset = wakeAfterOnset,
            awakenings = if (windowStages.isEmpty()) null else countAwakenings(windowStages.map { it.stage }),
            stageMinutes = stageMinutes,
            restingHeartRateBpm = resting,
            avgHeartRateBpm = inSleep.mapNotNull { it.heartRateBpm }.averageOrNull(),
            avgHrvRmssdMs = inSleep.mapNotNull { it.hrvRmssdMs }.averageOrNull(),
            avgSpo2Percent = inSleep.mapNotNull { it.spo2Percent }.averageOrNull(),
            avgSkinTemperatureC = inSleep.mapNotNull { it.skinTemperatureC }.averageOrNull(),
            heatUsed = data.events.any { it.type == NightEventType.HEAT },
            massageUsed = data.events.any { it.type == NightEventType.MASSAGE },
            gapDuration = data.gaps.fold(Duration.ZERO) { sum, gap -> sum + Duration.between(gap.start, gap.end ?: end) },
        )
    }

    private fun countAwakenings(stages: List<SleepStage>): Int {
        var count = 0
        var run = 0
        stages.forEach { stage ->
            if (stage == SleepStage.AWAKE) {
                run++
            } else {
                if (run >= MIN_AWAKENING_EPOCHS) count++
                run = 0
            }
        }
        return count + if (run >= MIN_AWAKENING_EPOCHS) 1 else 0
    }

    private fun List<Double>.averageOrNull() = if (isEmpty()) null else average()
}
