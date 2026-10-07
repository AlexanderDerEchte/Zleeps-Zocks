package at.zocks.zleep.data.repository

import at.zocks.zleep.data.db.NightSummaryDao
import at.zocks.zleep.data.db.toDomain
import at.zocks.zleep.data.db.toEntity
import at.zocks.zleep.domain.model.NightSummary
import at.zocks.zleep.domain.repository.NightSummaryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomNightSummaryRepository @Inject constructor(
    private val dao: NightSummaryDao,
) : NightSummaryRepository {

    override fun observeSummaries(): Flow<List<NightSummary>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeSummary(nightId: Long): Flow<NightSummary?> = dao.observe(nightId).map { it?.toDomain() }

    override suspend fun save(summary: NightSummary) = dao.upsert(summary.toEntity())

    override suspend fun nightsWithoutSummary(): List<Long> = dao.nightsWithoutSummary()
}
