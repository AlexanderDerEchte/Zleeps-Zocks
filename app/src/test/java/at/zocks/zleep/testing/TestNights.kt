package at.zocks.zleep.testing

import at.zocks.zleep.domain.model.ConnectionGap
import at.zocks.zleep.domain.model.EPOCH_LENGTH
import at.zocks.zleep.domain.model.EpochMeasurement
import at.zocks.zleep.domain.model.Night
import at.zocks.zleep.domain.model.NightData
import at.zocks.zleep.domain.model.NightEvent
import at.zocks.zleep.domain.model.NightSource
import at.zocks.zleep.domain.model.NightSummary
import at.zocks.zleep.domain.model.SleepStage
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.domain.model.StageEpoch
import at.zocks.zleep.domain.model.Tag
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

val TEST_NIGHT_START: Instant = Instant.parse("2026-10-05T20:30:00Z")

/**
 * Baut eine Nacht aus Abschnitten (Phase zu Anzahl Epochen). Beide Socken liefern pro Epoche
 * Werte; die rechte Socke misst 2 bpm mehr. Das Schlaffenster reicht vom ersten bis zum
 * letzten Schlaf-Epoche, wenn [withWindow] gesetzt ist.
 */
fun testNightData(
    plan: List<Pair<SleepStage, Int>>,
    id: Long = 1,
    start: Instant = TEST_NIGHT_START,
    withWindow: Boolean = true,
    heartRate: (SleepStage) -> Double? = { if (it == SleepStage.AWAKE) 70.0 else 55.0 },
    events: List<NightEvent> = emptyList(),
    gaps: List<ConnectionGap> = emptyList(),
    tags: List<Tag> = emptyList(),
): NightData {
    val stages = mutableListOf<StageEpoch>()
    var t = start
    plan.forEach { (stage, epochs) ->
        repeat(epochs) {
            stages += StageEpoch(t, stage)
            t = t.plus(EPOCH_LENGTH)
        }
    }
    val end = t
    val asleep = stages.filter { it.stage != SleepStage.AWAKE }
    val onset = asleep.firstOrNull()?.start?.takeIf { withWindow }
    val wake = asleep.lastOrNull()?.start?.plus(EPOCH_LENGTH)?.takeIf { withWindow }
    val measurements = stages.flatMap { epoch ->
        val hr = heartRate(epoch.stage)
        listOf(
            EpochMeasurement(SockSide.LEFT, epoch.start, hr, 50.0, 97.0, 33.0, 0.01, 6),
            EpochMeasurement(SockSide.RIGHT, epoch.start, hr?.plus(2), 50.0, 97.0, 33.4, 0.01, 6),
        )
    }
    return NightData(
        night = Night(id, start, end, onset, wake, NightSource.DEVICE, null, tags),
        measurements = measurements,
        stages = stages,
        events = events,
        gaps = gaps,
    )
}

/** Minuten in Epochen. */
fun minutes(value: Int): Int = value * 2

/** Eine Zusammenfassung mit runden, einfach nachrechenbaren Werten. */
fun testSummary(
    nightId: Long = 1,
    nightDate: LocalDate = LocalDate.of(2026, 10, 5),
    totalSleepMinutes: Long? = 480,
    latencyMinutes: Long? = 10,
    efficiency: Double? = 0.92,
    wakeAfterOnsetMinutes: Long? = 5,
    stageMinutes: Map<SleepStage, Int> = mapOf(
        SleepStage.LIGHT to 240,
        SleepStage.DEEP to 120,
        SleepStage.REM to 120,
        SleepStage.AWAKE to 15,
    ),
    sleepOnset: Instant? = null,
    finalWake: Instant? = null,
    restingHeartRate: Double? = 52.0,
    heatUsed: Boolean = false,
    massageUsed: Boolean = false,
): NightSummary {
    val start = nightDate.atTime(22, 0).toInstant(java.time.ZoneOffset.UTC)
    return NightSummary(
        nightId = nightId,
        nightDate = nightDate,
        start = start,
        end = start.plus(Duration.ofHours(9)),
        sleepOnset = sleepOnset,
        finalWake = finalWake,
        timeInBed = Duration.ofHours(9),
        totalSleep = totalSleepMinutes?.let(Duration::ofMinutes),
        sleepLatency = latencyMinutes?.let(Duration::ofMinutes),
        efficiency = efficiency,
        wakeAfterOnset = wakeAfterOnsetMinutes?.let(Duration::ofMinutes),
        awakenings = 1,
        stageMinutes = stageMinutes,
        restingHeartRateBpm = restingHeartRate,
        avgHeartRateBpm = restingHeartRate?.plus(4),
        avgHrvRmssdMs = 48.0,
        avgSpo2Percent = 97.0,
        avgSkinTemperatureC = 33.2,
        heatUsed = heatUsed,
        massageUsed = massageUsed,
        gapDuration = Duration.ZERO,
    )
}
