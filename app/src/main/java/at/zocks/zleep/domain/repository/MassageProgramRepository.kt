package at.zocks.zleep.domain.repository

import at.zocks.zleep.domain.massage.MassageProgram
import kotlinx.coroutines.flow.Flow

/** Eigene Massageprogramme. Eingebaute Programme kommen aus BuiltInMassagePrograms. */
interface MassageProgramRepository {
    fun observeCustomPrograms(): Flow<List<MassageProgram>>

    /** Speichert ein eigenes Programm und liefert seine ID („custom:…“). */
    suspend fun save(program: MassageProgram): String
    suspend fun delete(id: String)
}
