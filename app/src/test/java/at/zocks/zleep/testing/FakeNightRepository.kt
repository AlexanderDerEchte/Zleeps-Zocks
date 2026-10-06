package at.zocks.zleep.testing

import at.zocks.zleep.domain.model.ConnectionGap
import at.zocks.zleep.domain.model.EpochMeasurement
import at.zocks.zleep.domain.model.Night
import at.zocks.zleep.domain.model.NightData
import at.zocks.zleep.domain.model.NightEvent
import at.zocks.zleep.domain.model.NightSource
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.domain.model.StageEpoch
import at.zocks.zleep.domain.repository.NightRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

/** Einfaches Nacht-Repository im Speicher für Tests ohne Datenbank. */
class FakeNightRepository : NightRepository {

    private val nights = MutableStateFlow<Map<Long, Night>>(emptyMap())
    val measurements = mutableMapOf<Long, MutableMap<Pair<SockSide, Instant>, EpochMeasurement>>()
    val stages = mutableMapOf<Long, List<StageEpoch>>()
    val events = mutableMapOf<Long, MutableList<NightEvent>>()
    val gaps = mutableMapOf<Long, MutableList<ConnectionGap>>()
    private var nextId = 1L

    fun night(id: Long): Night? = nights.value[id]

    private fun change(id: Long, transform: (Night) -> Night) {
        nights.value = nights.value.mapValues { (key, night) -> if (key == id) transform(night) else night }
    }

    override fun observeNights(): Flow<List<Night>> = nights.map { it.values.sortedByDescending { n -> n.start } }
    override fun observeNight(id: Long): Flow<Night?> = nights.map { it[id] }
    override fun observeLatestCompletedNight(): Flow<Night?> =
        nights.map { all -> all.values.filter { it.end != null }.maxByOrNull { it.start } }

    override suspend fun getNightData(id: Long): NightData? {
        val night = nights.value[id] ?: return null
        return NightData(
            night,
            measurements[id].orEmpty().values.sortedBy { it.start },
            stages[id].orEmpty(),
            events[id].orEmpty().toList(),
            gaps[id].orEmpty().toList(),
        )
    }

    override suspend fun startNight(start: Instant, source: NightSource): Long {
        val id = nextId++
        nights.value = nights.value + (id to Night(id, start, null, null, null, source, null, emptyList()))
        return id
    }

    override suspend fun finishNight(id: Long, end: Instant) = change(id) { it.copy(end = end) }

    override suspend fun updateSleepWindow(id: Long, sleepOnset: Instant?, finalWake: Instant?, manual: Boolean) {
        require(sleepOnset == null || finalWake == null || sleepOnset.isBefore(finalWake))
        change(id) {
            if (it.sleepWindowManual && !manual) it else it.copy(sleepOnset = sleepOnset, finalWake = finalWake, sleepWindowManual = it.sleepWindowManual || manual)
        }
    }

    override suspend fun resetSleepWindowCorrection(id: Long) = change(id) { it.copy(sleepWindowManual = false) }

    override suspend fun getRecordingNight(): Night? = nights.value.values.filter { it.end == null }.maxByOrNull { it.start }

    override suspend fun setNote(id: Long, note: String?) = change(id) { it.copy(note = note) }
    override suspend fun setTags(id: Long, tagIds: Set<Long>) = Unit

    override suspend fun saveMeasurements(nightId: Long, measurements: List<EpochMeasurement>) {
        val map = this.measurements.getOrPut(nightId) { mutableMapOf() }
        measurements.forEach { map[it.side to it.start] = it }
    }

    override suspend fun saveStages(nightId: Long, stages: List<StageEpoch>) {
        this.stages[nightId] = stages
    }

    override suspend fun addEvent(nightId: Long, event: NightEvent): Long {
        val list = events.getOrPut(nightId) { mutableListOf() }
        val id = (events.values.sumOf { it.size } + 1).toLong()
        list += event.copy(id = id)
        return id
    }

    override suspend fun endEvent(eventId: Long, end: Instant) {
        events.values.forEach { list ->
            val index = list.indexOfFirst { it.id == eventId }
            if (index >= 0) list[index] = list[index].copy(end = end)
        }
    }

    override suspend fun openGap(nightId: Long, side: SockSide, start: Instant): Long {
        val list = gaps.getOrPut(nightId) { mutableListOf() }
        val id = (gaps.values.sumOf { it.size } + 1).toLong()
        list += ConnectionGap(id, side, start, null)
        return id
    }

    override suspend fun closeGap(gapId: Long, end: Instant) {
        gaps.values.forEach { list ->
            val index = list.indexOfFirst { it.id == gapId }
            if (index >= 0) list[index] = list[index].copy(end = end)
        }
    }

    override suspend fun insertCompleteNight(data: NightData): Long {
        val id = nextId++
        nights.value = nights.value + (id to data.night.copy(id = id))
        saveMeasurements(id, data.measurements)
        stages[id] = data.stages
        return id
    }

    override fun observeCount(source: NightSource): Flow<Int> = nights.map { all -> all.values.count { it.source == source } }

    override suspend fun deleteNight(id: Long) {
        nights.value = nights.value - id
        measurements.remove(id)
        stages.remove(id)
    }

    override suspend fun deleteBySource(source: NightSource) {
        nights.value = nights.value.filterValues { it.source != source }
    }

    override suspend fun deleteAll() {
        nights.value = emptyMap()
    }
}
