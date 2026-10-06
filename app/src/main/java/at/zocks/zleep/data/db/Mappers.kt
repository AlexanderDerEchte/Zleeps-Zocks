package at.zocks.zleep.data.db

import at.zocks.zleep.domain.model.ConnectionGap
import at.zocks.zleep.domain.model.EpochMeasurement
import at.zocks.zleep.domain.model.Night
import at.zocks.zleep.domain.model.NightEvent
import at.zocks.zleep.domain.model.StageEpoch
import at.zocks.zleep.domain.model.Tag
import java.time.Instant

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
)

fun Night.toEntity(): NightEntity = NightEntity(
    id = id,
    startMs = start.toEpochMilli(),
    endMs = end?.toEpochMilli(),
    sleepOnsetMs = sleepOnset?.toEpochMilli(),
    finalWakeMs = finalWake?.toEpochMilli(),
    source = source,
    note = note,
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
