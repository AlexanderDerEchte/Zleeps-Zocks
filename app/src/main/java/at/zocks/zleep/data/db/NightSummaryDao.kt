package at.zocks.zleep.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NightSummaryDao {

    @Query("SELECT * FROM night_summaries ORDER BY start_ms DESC")
    fun observeAll(): Flow<List<NightSummaryEntity>>

    @Query("SELECT * FROM night_summaries WHERE night_id = :nightId")
    fun observe(nightId: Long): Flow<NightSummaryEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: NightSummaryEntity)

    /** Abgeschlossene Nächte, für die noch keine Zusammenfassung gespeichert ist. */
    @Query(
        "SELECT id FROM nights WHERE end_ms IS NOT NULL " +
            "AND id NOT IN (SELECT night_id FROM night_summaries) ORDER BY start_ms",
    )
    suspend fun nightsWithoutSummary(): List<Long>
}
