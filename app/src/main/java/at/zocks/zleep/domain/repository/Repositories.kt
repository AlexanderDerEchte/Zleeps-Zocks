package at.zocks.zleep.domain.repository

import at.zocks.zleep.domain.model.ConnectionGap
import at.zocks.zleep.domain.model.EpochMeasurement
import at.zocks.zleep.domain.model.Night
import at.zocks.zleep.domain.model.NightData
import at.zocks.zleep.domain.model.NightEvent
import at.zocks.zleep.domain.model.NightSource
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.domain.model.StageEpoch
import at.zocks.zleep.domain.model.Tag
import at.zocks.zleep.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface NightRepository {
    /** Alle Nächte, neueste zuerst. */
    fun observeNights(): Flow<List<Night>>
    fun observeNight(id: Long): Flow<Night?>

    /** Letzte abgeschlossene Nacht oder `null`. */
    fun observeLatestCompletedNight(): Flow<Night?>
    suspend fun getNightData(id: Long): NightData?

    suspend fun startNight(start: Instant, source: NightSource): Long
    suspend fun finishNight(id: Long, end: Instant)
    suspend fun updateSleepWindow(id: Long, sleepOnset: Instant?, finalWake: Instant?)
    suspend fun setNote(id: Long, note: String?)
    suspend fun setTags(id: Long, tagIds: Set<Long>)

    suspend fun saveMeasurements(nightId: Long, measurements: List<EpochMeasurement>)
    suspend fun saveStages(nightId: Long, stages: List<StageEpoch>)
    suspend fun addEvent(nightId: Long, event: NightEvent): Long
    suspend fun endEvent(eventId: Long, end: Instant)
    suspend fun openGap(nightId: Long, side: SockSide, start: Instant): Long
    suspend fun closeGap(gapId: Long, end: Instant)

    /** Speichert eine komplette Nacht in einer Transaktion (Demo-Daten, Import). */
    suspend fun insertCompleteNight(data: NightData): Long

    fun observeCount(source: NightSource): Flow<Int>
    suspend fun deleteNight(id: Long)
    suspend fun deleteBySource(source: NightSource)
    suspend fun deleteAll()
}

interface TagRepository {
    fun observeTags(): Flow<List<Tag>>
    suspend fun getByKey(key: String): Tag?
    suspend fun addCustomTag(label: String): Long
    suspend fun deleteTag(id: Long)
}

interface SettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun update(transform: (UserSettings) -> UserSettings)
}
