package at.zocks.zleep.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.zocks.zleep.data.db.ZocksDatabase
import at.zocks.zleep.data.repository.RoomNightRepository
import at.zocks.zleep.data.repository.RoomTagRepository
import at.zocks.zleep.domain.model.ConnectionGap
import at.zocks.zleep.domain.model.EpochMeasurement
import at.zocks.zleep.domain.model.Night
import at.zocks.zleep.domain.model.NightData
import at.zocks.zleep.domain.model.NightEvent
import at.zocks.zleep.domain.model.NightEventType
import at.zocks.zleep.domain.model.NightSource
import at.zocks.zleep.domain.model.SleepStage
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.domain.model.StageEpoch
import at.zocks.zleep.domain.model.Tag
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class RoomNightRepositoryTest {

    private lateinit var database: ZocksDatabase
    private lateinit var nights: RoomNightRepository
    private lateinit var tags: RoomTagRepository

    private val start = Instant.parse("2026-10-05T20:45:00Z")
    private fun at(minutes: Long) = start.plus(Duration.ofMinutes(minutes))

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ZocksDatabase::class.java)
            .addCallback(ZocksDatabase.SeedBuiltInTags)
            .allowMainThreadQueries()
            .build()
        nights = RoomNightRepository(database)
        tags = RoomTagRepository(database.tagDao())
    }

    @After
    fun tearDown() = database.close()

    private fun measurement(side: SockSide, minute: Long, hr: Double? = 55.0) =
        EpochMeasurement(side, at(minute), hr, 48.0, null, 33.1, 0.01, 6)

    @Test
    fun `built in tags are seeded`() = runTest {
        assertThat(tags.observeTags().first().map { it.key }).containsExactlyElementsIn(Tag.BuiltInKeys)
        val custom = tags.addCustomTag("  Lesen  ")
        assertThat(tags.observeTags().first().single { it.id == custom }.label).isEqualTo("Lesen")
    }

    @Test
    fun `recording lifecycle`() = runTest {
        val id = nights.startNight(start, NightSource.SIMULATOR)
        assertThat(nights.observeNights().first().single().isRecording).isTrue()
        assertThat(nights.observeLatestCompletedNight().first()).isNull()

        nights.saveMeasurements(id, listOf(measurement(SockSide.LEFT, 0), measurement(SockSide.RIGHT, 0, hr = null)))
        val gap = nights.openGap(id, SockSide.LEFT, at(60))
        nights.closeGap(gap, at(64))
        val heat = nights.addEvent(id, NightEvent(type = NightEventType.HEAT, side = SockSide.BOTH, start = at(0), end = null))
        nights.endEvent(heat, at(20))
        nights.saveStages(id, listOf(StageEpoch(at(0), SleepStage.AWAKE), StageEpoch(at(1), SleepStage.LIGHT)))
        nights.updateSleepWindow(id, at(15), at(450))
        nights.finishNight(id, at(470))

        val latest = nights.observeLatestCompletedNight().first()!!
        assertThat(latest.id).isEqualTo(id)
        assertThat(latest.sleepWindow).isEqualTo(Duration.ofMinutes(435))

        val data = nights.getNightData(id)!!
        assertThat(data.measurements).hasSize(2)
        assertThat(data.measurements.single { it.side == SockSide.RIGHT }.heartRateBpm).isNull()
        assertThat(data.gaps.single().end).isEqualTo(at(64))
        assertThat(data.events.single().end).isEqualTo(at(20))
        assertThat(data.stages.map { it.stage }).containsExactly(SleepStage.AWAKE, SleepStage.LIGHT).inOrder()
    }

    @Test
    fun `measurements are upserted per sock and epoch`() = runTest {
        val id = nights.startNight(start, NightSource.DEVICE)
        nights.saveMeasurements(id, listOf(measurement(SockSide.LEFT, 0, hr = 60.0)))
        nights.saveMeasurements(id, listOf(measurement(SockSide.LEFT, 0, hr = 58.0)))
        assertThat(nights.getNightData(id)!!.measurements.single().heartRateBpm).isWithin(0.01).of(58.0)
    }

    @Test
    fun `measurements for both socks at once are rejected`() = runTest {
        val id = nights.startNight(start, NightSource.DEVICE)
        val result = runCatching { nights.saveMeasurements(id, listOf(measurement(SockSide.BOTH, 0))) }
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `sleep window must be ordered`() = runTest {
        val id = nights.startNight(start, NightSource.DEVICE)
        val result = runCatching { nights.updateSleepWindow(id, at(100), at(50)) }
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `complete nights round trip with tags`() = runTest {
        val caffeine = tags.getByKey(Tag.CAFFEINE)!!
        val sport = tags.getByKey(Tag.SPORT)!!
        val data = NightData(
            night = Night(0, start, at(480), at(12), at(470), NightSource.DEMO, "ruhig", listOf(caffeine, sport)),
            measurements = listOf(measurement(SockSide.LEFT, 0), measurement(SockSide.RIGHT, 0)),
            stages = listOf(StageEpoch(at(0), SleepStage.AWAKE)),
            events = listOf(NightEvent(type = NightEventType.MASSAGE, side = SockSide.BOTH, start = at(2), end = at(12))),
            gaps = listOf(ConnectionGap(side = SockSide.RIGHT, start = at(100), end = at(103))),
        )
        val id = nights.insertCompleteNight(data)
        val loaded = nights.getNightData(id)!!
        assertThat(loaded.night.tags.map { it.key }).containsExactly(Tag.CAFFEINE, Tag.SPORT)
        assertThat(loaded.night.note).isEqualTo("ruhig")
        assertThat(loaded.measurements).hasSize(2)
        assertThat(loaded.events).hasSize(1)
        assertThat(loaded.gaps).hasSize(1)

        nights.setTags(id, setOf(sport.id))
        assertThat(nights.observeNight(id).first()!!.tags.map { it.key }).containsExactly(Tag.SPORT)
        nights.setNote(id, "   ")
        assertThat(nights.observeNight(id).first()!!.note).isNull()
    }

    @Test
    fun `deleting removes dependent data and only the chosen source`() = runTest {
        val demo = nights.insertCompleteNight(
            NightData(
                Night(0, start, at(400), null, null, NightSource.DEMO, null, emptyList()),
                listOf(measurement(SockSide.LEFT, 0)),
                listOf(StageEpoch(at(0), SleepStage.LIGHT)),
                emptyList(),
                emptyList(),
            ),
        )
        val real = nights.startNight(at(1440), NightSource.DEVICE)
        assertThat(nights.observeCount(NightSource.DEMO).first()).isEqualTo(1)

        nights.deleteBySource(NightSource.DEMO)
        assertThat(nights.getNightData(demo)).isNull()
        assertThat(database.nightDao().getMeasurements(demo)).isEmpty()
        assertThat(database.nightDao().getStages(demo)).isEmpty()
        assertThat(nights.getNightData(real)).isNotNull()

        nights.deleteAll()
        assertThat(nights.observeNights().first()).isEmpty()
    }
}
