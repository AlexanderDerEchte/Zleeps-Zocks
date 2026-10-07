package at.zocks.zleep.data.db

import at.zocks.zleep.domain.model.ConnectionGap
import at.zocks.zleep.domain.model.EpochMeasurement
import at.zocks.zleep.domain.model.Night
import at.zocks.zleep.domain.model.NightEvent
import at.zocks.zleep.domain.model.NightSummary
import at.zocks.zleep.domain.model.SleepStage
import at.zocks.zleep.domain.model.StageEpoch
import at.zocks.zleep.domain.model.Tag
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

private fun Long.toInstant(): Instant = Instant.ofEpochMilli(this)

fun NightWithTags.toDomain(): Night = Night(
    id = night.id,
    start = night.startMs.toInstant(),
    end = night.endMs?.toInstant(),
    sleepOnset = night.sleepOnsetMs?.toInstant(),
    finalWake = night.finalWakeMs?.toInstant(),
    source = night.source,
    note = night.note,
    tags = tags.map { it.toDomain() },
    sleepWindowManual = night.sleepWindowManual,
)

fun Night.toEntity(): NightEntity = NightEntity(
    id = id,
    startMs = start.toEpochMilli(),
    endMs = end?.toEpochMilli(),
    sleepOnsetMs = sleepOnset?.toEpochMilli(),
    finalWakeMs = finalWake?.toEpochMilli(),
    source = source,
    note = note,
    sleepWindowManual = sleepWindowManual,
)

fun TagEntity.toDomain(): Tag = Tag(id, key, label)

fun EpochMeasurementEntity.toDomain(): EpochMeasurement = EpochMeasurement(
    side = side,
    start = startMs.toInstant(),
    heartRateBpm = heartRate?.toDouble(),
    hrvRmssdMs = hrvRmssd?.toDouble(),
    spo2Percent = spo2?.toDouble(),
    skinTemperatureC = skinTemperature?.toDouble(),
    motion = motion?.toDouble(),
    sampleCount = sampleCount,
)

fun EpochMeasurement.toEntity(nightId: Long): EpochMeasurementEntity = EpochMeasurementEntity(
    nightId = nightId,
    side = side,
    startMs = start.toEpochMilli(),
    heartRate = heartRateBpm?.toFloat(),
    hrvRmssd = hrvRmssdMs?.toFloat(),
    spo2 = spo2Percent?.toFloat(),
    skinTemperature = skinTemperatureC?.toFloat(),
    motion = motion?.toFloat(),
    sampleCount = sampleCount,
)

fun SleepStageEntity.toDomain(): StageEpoch = StageEpoch(startMs.toInstant(), stage)

fun StageEpoch.toEntity(nightId: Long): SleepStageEntity = SleepStageEntity(nightId, start.toEpochMilli(), stage)

fun NightEventEntity.toDomain(): NightEvent =
    NightEvent(id, type, side, startMs.toInstant(), endMs?.toInstant(), detail)

fun NightEvent.toEntity(nightId: Long): NightEventEntity =
    NightEventEntity(id, nightId, type, side, start.toEpochMilli(), end?.toEpochMilli(), detail)

fun ConnectionGapEntity.toDomain(): ConnectionGap = ConnectionGap(id, side, startMs.toInstant(), endMs?.toInstant())

fun ConnectionGap.toEntity(nightId: Long): ConnectionGapEntity =
    ConnectionGapEntity(id, nightId, side, start.toEpochMilli(), end?.toEpochMilli())

fun NightSummaryEntity.toDomain(): NightSummary = NightSummary(
    nightId = nightId,
    nightDate = LocalDate.ofEpochDay(nightDate),
    start = startMs.toInstant(),
    end = endMs?.toInstant(),
    sleepOnset = sleepOnsetMs?.toInstant(),
    finalWake = finalWakeMs?.toInstant(),
    timeInBed = Duration.ofSeconds(timeInBedSeconds),
    totalSleep = totalSleepSeconds?.let(Duration::ofSeconds),
    sleepLatency = sleepLatencySeconds?.let(Duration::ofSeconds),
    efficiency = efficiency,
    wakeAfterOnset = wakeAfterOnsetSeconds?.let(Duration::ofSeconds),
    awakenings = awakenings,
    stageMinutes = mapOf(
        SleepStage.AWAKE to awakeMinutes,
        SleepStage.LIGHT to lightMinutes,
        SleepStage.DEEP to deepMinutes,
        SleepStage.REM to remMinutes,
    ).filterValues { it > 0 },
    restingHeartRateBpm = restingHeartRate,
    avgHeartRateBpm = avgHeartRate,
    avgHrvRmssdMs = avgHrvRmssd,
    avgSpo2Percent = avgSpo2,
    avgSkinTemperatureC = avgSkinTemperature,
    heatUsed = heatUsed,
    massageUsed = massageUsed,
    gapDuration = Duration.ofSeconds(gapSeconds),
)

fun NightSummary.toEntity(): NightSummaryEntity = NightSummaryEntity(
    nightId = nightId,
    nightDate = nightDate.toEpochDay(),
    startMs = start.toEpochMilli(),
    endMs = end?.toEpochMilli(),
    sleepOnsetMs = sleepOnset?.toEpochMilli(),
    finalWakeMs = finalWake?.toEpochMilli(),
    timeInBedSeconds = timeInBed.seconds,
    totalSleepSeconds = totalSleep?.seconds,
    sleepLatencySeconds = sleepLatency?.seconds,
    efficiency = efficiency,
    wakeAfterOnsetSeconds = wakeAfterOnset?.seconds,
    awakenings = awakenings,
    awakeMinutes = stageMinutes[SleepStage.AWAKE] ?: 0,
    lightMinutes = stageMinutes[SleepStage.LIGHT] ?: 0,
    deepMinutes = stageMinutes[SleepStage.DEEP] ?: 0,
    remMinutes = stageMinutes[SleepStage.REM] ?: 0,
    restingHeartRate = restingHeartRateBpm,
    avgHeartRate = avgHeartRateBpm,
    avgHrvRmssd = avgHrvRmssdMs,
    avgSpo2 = avgSpo2Percent,
    avgSkinTemperature = avgSkinTemperatureC,
    heatUsed = heatUsed,
    massageUsed = massageUsed,
    gapSeconds = gapDuration.seconds,
)
