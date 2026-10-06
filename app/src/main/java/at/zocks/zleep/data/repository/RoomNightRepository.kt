package at.zocks.zleep.data.repository

import androidx.room.withTransaction
import at.zocks.zleep.data.db.NightDao
import at.zocks.zleep.data.db.NightEntity
import at.zocks.zleep.data.db.ZocksDatabase
import at.zocks.zleep.data.db.toDomain
import at.zocks.zleep.data.db.toEntity
import at.zocks.zleep.domain.model.EpochMeasurement
import at.zocks.zleep.domain.model.Night
import at.zocks.zleep.domain.model.NightData
import at.zocks.zleep.domain.model.NightEvent
import at.zocks.zleep.domain.model.NightSource
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.domain.model.StageEpoch
import at.zocks.zleep.domain.repository.NightRepository
import at.zocks.zleep.data.db.ConnectionGapEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomNightRepository @Inject constructor(
    private val database: ZocksDatabase,
) : NightRepository {

    private val dao: NightDao = database.nightDao()

    override fun observeNights(): Flow<List<Night>> = dao.observeNights().map { list -> list.map { it.toDomain() } }

    override fun observeNight(id: Long): Flow<Night?> = dao.observeNight(id).map { it?.toDomain() }

    override fun observeLatestCompletedNight(): Flow<Night?> = dao.observeLatestCompleted().map { it?.toDomain() }

    override suspend fun getNightData(id: Long): NightData? = database.withTransaction {
        val night = dao.getNight(id)?.toDomain() ?: return@withTransaction null
        NightData(
            night = night,
            measurements = dao.getMeasurements(id).map { it.toDomain() },
            stages = dao.getStages(id).map { it.toDomain() },
            events = dao.getEvents(id).map { it.toDomain() },
            gaps = dao.getGaps(id).map { it.toDomain() },
        )
    }

    override suspend fun startNight(start: Instant, source: NightSource): Long = dao.insertNight(
        NightEntity(startMs = start.toEpochMilli(), endMs = null, sleepOnsetMs = null, finalWakeMs = null, source = source, note = null),
    )

    override suspend fun finishNight(id: Long, end: Instant) = dao.setEnd(id, end.toEpochMilli())

    override suspend fun updateSleepWindow(id: Long, sleepOnset: Instant?, finalWake: Instant?) {
        require(sleepOnset == null || finalWake == null || sleepOnset.isBefore(finalWake)) {
            "Einschlafen muss vor dem Aufwachen liegen"
        }
        dao.setSleepWindow(id, sleepOnset?.toEpochMilli(), finalWake?.toEpochMilli())
    }

    override suspend fun setNote(id: Long, note: String?) = dao.setNote(id, note?.trim()?.takeIf { it.isNotEmpty() })

    override suspend fun setTags(id: Long, tagIds: Set<Long>) = dao.replaceTags(id, tagIds)

    override suspend fun saveMeasurements(nightId: Long, measurements: List<EpochMeasurement>) {
        require(measurements.none { it.side == SockSide.BOTH }) { "Messwerte gehören zu genau einer Socke" }
        dao.upsertMeasurements(measurements.map { it.toEntity(nightId) })
    }

    override suspend fun saveStages(nightId: Long, stages: List<StageEpoch>) = database.withTransaction {
        dao.deleteStages(nightId)
        dao.upsertStages(stages.map { it.toEntity(nightId) })
    }

    override suspend fun addEvent(nightId: Long, event: NightEvent): Long = dao.insertEvent(event.toEntity(nightId).copy(id = 0))

    override suspend fun endEvent(eventId: Long, end: Instant) = dao.setEventEnd(eventId, end.toEpochMilli())

    override suspend fun openGap(nightId: Long, side: SockSide, start: Instant): Long =
        dao.insertGap(ConnectionGapEntity(nightId = nightId, side = side, startMs = start.toEpochMilli(), endMs = null))

    override suspend fun closeGap(gapId: Long, end: Instant) = dao.setGapEnd(gapId, end.toEpochMilli())

    override suspend fun insertCompleteNight(data: NightData): Long = database.withTransaction {
        val id = dao.insertNight(data.night.toEntity().copy(id = 0))
        dao.upsertMeasurements(data.measurements.map { it.toEntity(id) })
        dao.upsertStages(data.stages.map { it.toEntity(id) })
        dao.insertEvents(data.events.map { it.toEntity(id).copy(id = 0) })
        dao.insertGaps(data.gaps.map { it.toEntity(id).copy(id = 0) })
        dao.replaceTags(id, data.night.tags.map { it.id }.toSet())
        id
    }

    override fun observeCount(source: NightSource): Flow<Int> = dao.observeCount(source)

    override suspend fun deleteNight(id: Long) = dao.deleteNight(id)

    override suspend fun deleteBySource(source: NightSource) = dao.deleteBySource(source)

    override suspend fun deleteAll() = dao.deleteAll()
}
