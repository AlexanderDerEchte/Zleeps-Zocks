package at.zocks.zleep.domain.repository

import at.zocks.zleep.domain.model.NightSummary
import kotlinx.coroutines.flow.Flow

/** Zwischengespeicherte Kennzahlen abgeschlossener Nächte (für Liste, Kalender und Trends). */
interface NightSummaryRepository {
    /** Alle Zusammenfassungen, neueste Nacht zuerst. */
    fun observeSummaries(): Flow<List<NightSummary>>
    fun observeSummary(nightId: Long): Flow<NightSummary?>
    suspend fun save(summary: NightSummary)

    /** Abgeschlossene Nächte, für die noch keine Zusammenfassung existiert. */
    suspend fun nightsWithoutSummary(): List<Long>
}
