package at.zocks.zleep.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.zocks.zleep.data.db.ZocksDatabase
import at.zocks.zleep.data.repository.RoomMassageProgramRepository
import at.zocks.zleep.domain.massage.BuiltInMassagePrograms
import at.zocks.zleep.domain.massage.MassageProgram
import at.zocks.zleep.domain.massage.MassageProgramType
import at.zocks.zleep.domain.massage.MassageTempo
import at.zocks.zleep.domain.model.MassageZone
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomMassageProgramRepositoryTest {

    private lateinit var database: ZocksDatabase
    private lateinit var repository: RoomMassageProgramRepository

    private val draft = MassageProgram(
        id = MassageProgram.CUSTOM_PREFIX,
        type = MassageProgramType.KNEAD,
        name = "  Nach dem Laufen ",
        intensity = 75,
        durationMinutes = 12,
        tempo = MassageTempo.FAST,
        zones = setOf(MassageZone.HEEL, MassageZone.ARCH),
    )

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ZocksDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomMassageProgramRepository(database.massageProgramDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `save, update and delete a custom program`() = runTest {
        val id = repository.save(draft)
        val saved = repository.observeCustomPrograms().first().single()
        assertThat(saved.id).isEqualTo(id)
        assertThat(saved.name).isEqualTo("Nach dem Laufen")
        assertThat(saved.zones).containsExactly(MassageZone.HEEL, MassageZone.ARCH)
        assertThat(saved.isBuiltIn).isFalse()

        assertThat(repository.save(saved.copy(intensity = 40))).isEqualTo(id)
        assertThat(repository.observeCustomPrograms().first().single().intensity).isEqualTo(40)

        repository.delete(id)
        assertThat(repository.observeCustomPrograms().first()).isEmpty()
    }

    @Test
    fun `invalid programs are rejected`() = runTest {
        assertThat(runCatching { repository.save(draft.copy(name = " ")) }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(runCatching { repository.save(draft.copy(zones = emptySet())) }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(runCatching { repository.save(BuiltInMassagePrograms.WAVE) }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
