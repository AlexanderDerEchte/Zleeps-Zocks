package at.zocks.zleep.data.repository

import at.zocks.zleep.data.db.MassageProgramDao
import at.zocks.zleep.data.db.MassageProgramEntity
import at.zocks.zleep.domain.massage.MassageProgram
import at.zocks.zleep.domain.model.MassageZone
import at.zocks.zleep.domain.repository.MassageProgramRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomMassageProgramRepository @Inject constructor(
    private val dao: MassageProgramDao,
) : MassageProgramRepository {

    override fun observeCustomPrograms(): Flow<List<MassageProgram>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun save(program: MassageProgram): String {
        require(!program.isBuiltIn) { "Eingebaute Programme sind nicht änderbar" }
        val name = program.name?.trim().orEmpty()
        require(name.isNotEmpty()) { "Ein eigenes Programm braucht einen Namen" }
        require(program.zones.isNotEmpty()) { "Mindestens eine Zone" }
        val existingId = MassageProgram.databaseIdOf(program.id) ?: 0
        val entity = MassageProgramEntity(
            id = existingId,
            name = name,
            type = program.type,
            intensity = program.intensity.coerceIn(10, 100),
            durationMinutes = program.durationMinutes.coerceIn(1, 60),
            tempo = program.tempo,
            zones = program.zones.joinToString(",") { it.name },
        )
        val rowId = dao.upsert(entity)
        // Upsert liefert bei einem Update -1.
        return MassageProgram.customId(if (existingId != 0L) existingId else rowId)
    }

    override suspend fun delete(id: String) {
        MassageProgram.databaseIdOf(id)?.let { dao.delete(it) }
    }

    private fun MassageProgramEntity.toDomain() = MassageProgram(
        id = MassageProgram.customId(id),
        type = type,
        name = name,
        intensity = intensity,
        durationMinutes = durationMinutes,
        tempo = tempo,
        zones = zones.split(",").mapNotNull { name -> MassageZone.entries.firstOrNull { it.name == name } }.toSet(),
    )
}
