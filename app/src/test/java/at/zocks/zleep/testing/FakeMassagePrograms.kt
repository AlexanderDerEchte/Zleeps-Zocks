package at.zocks.zleep.testing

import at.zocks.zleep.domain.massage.MassageProgram
import at.zocks.zleep.domain.repository.MassageProgramRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** Eigene Massageprogramme im Speicher. */
class FakeMassagePrograms(programs: List<MassageProgram> = emptyList()) : MassageProgramRepository {
    val programs = MutableStateFlow(programs)

    override fun observeCustomPrograms() = programs

    override suspend fun save(program: MassageProgram): String {
        programs.value += program
        return program.id
    }

    override suspend fun delete(id: String) {
        programs.value = programs.value.filterNot { it.id == id }
    }
}
