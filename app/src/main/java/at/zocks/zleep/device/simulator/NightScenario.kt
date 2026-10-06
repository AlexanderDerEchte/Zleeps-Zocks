package at.zocks.zleep.device.simulator

import at.zocks.zleep.domain.model.EPOCH_LENGTH
import at.zocks.zleep.domain.model.SleepStage
import java.time.Duration
import java.time.Instant

/** Physiologie eines Menschen in einer Epoche – die „Wahrheit“ hinter den simulierten Sensoren. */
data class Physiology(
    val stage: SleepStage,
    val heartRateBpm: Double,
    val hrvRmssdMs: Double,
    val spo2Percent: Double,
    /** Hauttemperatur am Fuß ohne Heizung. */
    val skinTemperatureC: Double,
    /** Bewegung 0..1. */
    val motion: Double,
)

/** Eine simulierte Nacht in 30-Sekunden-Epochen. */
class NightScenario(
    val start: Instant,
    val epochs: List<Physiology>,
) {
    val end: Instant = start.plus(EPOCH_LENGTH.multipliedBy(epochs.size.toLong()))

    fun epochStart(index: Int): Instant = start.plus(EPOCH_LENGTH.multipliedBy(index.toLong()))

    fun epochAt(time: Instant): Physiology? {
        if (time.isBefore(start) || !time.isBefore(end)) return null
        val index = Duration.between(start, time).toMillis() / EPOCH_LENGTH.toMillis()
        return epochs[index.toInt()]
    }

    /** Beginn der ersten Schlafepoche. */
    val sleepOnset: Instant?
        get() = epochs.indexOfFirst { it.stage != SleepStage.AWAKE }.takeIf { it >= 0 }?.let(::epochStart)

    /** Ende der letzten Schlafepoche. */
    val finalWake: Instant?
        get() = epochs.indexOfLast { it.stage != SleepStage.AWAKE }.takeIf { it >= 0 }?.let { epochStart(it + 1) }

    fun minutesIn(stage: SleepStage): Double =
        epochs.count { it.stage == stage } * EPOCH_LENGTH.seconds / 60.0
}
