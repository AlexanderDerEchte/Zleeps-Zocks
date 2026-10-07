package at.zocks.zleep.testing

import at.zocks.zleep.domain.model.NightSummary
import at.zocks.zleep.domain.repository.NightRepository
import at.zocks.zleep.domain.repository.NightSummaryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Zusammenfassungen im Speicher; „fehlend“ bezieht sich auf die Nächte in [nights]. */
class FakeNightSummaryRepository(private val nights: NightRepository) : NightSummaryRepository {

    val summaries = MutableStateFlow<Map<Long, NightSummary>>(emptyMap())

    override fun observeSummaries(): Flow<List<NightSummary>> = summaries.map { it.values.sortedByDescending { s -> s.start } }

    override fun observeSummary(nightId: Long): Flow<NightSummary?> = summaries.map { it[nightId] }

    override suspend fun save(summary: NightSummary) {
        summaries.value += summary.nightId to summary
    }

    override suspend fun nightsWithoutSummary(): List<Long> =
        nights.observeNights().first().filter { it.end != null && it.id !in summaries.value }.map { it.id }
}
