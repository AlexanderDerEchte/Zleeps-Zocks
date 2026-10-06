package at.zocks.zleep.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MassageProgramDao {
    @Query("SELECT * FROM massage_programs ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<MassageProgramEntity>>

    @Upsert
    suspend fun upsert(program: MassageProgramEntity): Long

    @Query("DELETE FROM massage_programs WHERE id = :id")
    suspend fun delete(id: Long)
}
