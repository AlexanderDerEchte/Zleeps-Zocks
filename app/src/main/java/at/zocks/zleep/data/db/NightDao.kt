package at.zocks.zleep.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import at.zocks.zleep.domain.model.NightSource
import kotlinx.coroutines.flow.Flow

@Dao
interface NightDao {

    @Transaction
    @Query("SELECT * FROM nights ORDER BY start_ms DESC")
    fun observeNights(): Flow<List<NightWithTags>>

    @Transaction
    @Query("SELECT * FROM nights WHERE id = :id")
    fun observeNight(id: Long): Flow<NightWithTags?>

    @Transaction
    @Query("SELECT * FROM nights WHERE end_ms IS NOT NULL ORDER BY start_ms DESC LIMIT 1")
    fun observeLatestCompleted(): Flow<NightWithTags?>

    @Transaction
    @Query("SELECT * FROM nights WHERE id = :id")
    suspend fun getNight(id: Long): NightWithTags?

    @Query("SELECT COUNT(*) FROM nights WHERE source = :source")
    fun observeCount(source: NightSource): Flow<Int>

    @Insert
    suspend fun insertNight(night: NightEntity): Long

    @Query("UPDATE nights SET end_ms = :endMs WHERE id = :id")
    suspend fun setEnd(id: Long, endMs: Long)

    @Query("UPDATE nights SET sleep_onset_ms = :onsetMs, final_wake_ms = :wakeMs WHERE id = :id")
    suspend fun setSleepWindow(id: Long, onsetMs: Long?, wakeMs: Long?)

    @Query("UPDATE nights SET note = :note WHERE id = :id")
    suspend fun setNote(id: Long, note: String?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeasurements(measurements: List<EpochMeasurementEntity>)

    @Query("SELECT * FROM epoch_measurements WHERE night_id = :nightId ORDER BY start_ms, side")
    suspend fun getMeasurements(nightId: Long): List<EpochMeasurementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStages(stages: List<SleepStageEntity>)

    @Query("DELETE FROM sleep_stages WHERE night_id = :nightId")
    suspend fun deleteStages(nightId: Long)

    @Query("SELECT * FROM sleep_stages WHERE night_id = :nightId ORDER BY start_ms")
    suspend fun getStages(nightId: Long): List<SleepStageEntity>

    @Insert
    suspend fun insertEvent(event: NightEventEntity): Long

    @Insert
    suspend fun insertEvents(events: List<NightEventEntity>)

    @Query("UPDATE night_events SET end_ms = :endMs WHERE id = :id")
    suspend fun setEventEnd(id: Long, endMs: Long)

    @Query("SELECT * FROM night_events WHERE night_id = :nightId ORDER BY start_ms")
    suspend fun getEvents(nightId: Long): List<NightEventEntity>

    @Insert
    suspend fun insertGap(gap: ConnectionGapEntity): Long

    @Insert
    suspend fun insertGaps(gaps: List<ConnectionGapEntity>)

    @Query("UPDATE connection_gaps SET end_ms = :endMs WHERE id = :id")
    suspend fun setGapEnd(id: Long, endMs: Long)

    @Query("SELECT * FROM connection_gaps WHERE night_id = :nightId ORDER BY start_ms")
    suspend fun getGaps(nightId: Long): List<ConnectionGapEntity>

    @Query("DELETE FROM night_tags WHERE night_id = :nightId")
    suspend fun clearTags(nightId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNightTags(refs: List<NightTagCrossRef>)

    @Transaction
    suspend fun replaceTags(nightId: Long, tagIds: Set<Long>) {
        clearTags(nightId)
        insertNightTags(tagIds.map { NightTagCrossRef(nightId, it) })
    }

    @Query("DELETE FROM nights WHERE id = :id")
    suspend fun deleteNight(id: Long)

    @Query("DELETE FROM nights WHERE source = :source")
    suspend fun deleteBySource(source: NightSource)

    @Query("DELETE FROM nights")
    suspend fun deleteAll()
}
