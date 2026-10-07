package at.zocks.zleep.data.settings

import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.domain.routine.EveningRoutine
import at.zocks.zleep.domain.routine.RoutineHeat
import at.zocks.zleep.domain.routine.RoutineMassage
import at.zocks.zleep.domain.routine.RoutineStep
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Speicherform der Abendroutine in DataStore (JSON). Unlesbares fällt auf den Vorschlag zurück. */
internal object RoutineJson {

    @Serializable
    private data class StepDto(val minutes: Int, val heatLevel: Int? = null, val massageProgram: String? = null, val massageIntensity: Int? = null)

    @Serializable
    private data class RoutineDto(val steps: List<StepDto>, val endWhenAsleep: Boolean = true, val side: String = SockSide.BOTH.name)

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(routine: EveningRoutine): String = json.encodeToString(
        RoutineDto.serializer(),
        RoutineDto(
            steps = routine.steps.map { StepDto(it.durationMinutes, it.heat?.level, it.massage?.programId, it.massage?.intensity) },
            endWhenAsleep = routine.endWhenAsleep,
            side = routine.side.name,
        ),
    )

    fun decode(value: String?): EveningRoutine {
        if (value == null) return EveningRoutine.Default
        return runCatching {
            val dto = json.decodeFromString(RoutineDto.serializer(), value)
            EveningRoutine(
                steps = dto.steps.map { step ->
                    RoutineStep(
                        durationMinutes = step.minutes,
                        heat = step.heatLevel?.let { RoutineHeat(it) },
                        massage = step.massageProgram?.let { RoutineMassage(it, step.massageIntensity ?: 40) },
                    )
                },
                endWhenAsleep = dto.endWhenAsleep,
                side = SockSide.entries.firstOrNull { it.name == dto.side } ?: SockSide.BOTH,
            ).normalized()
        }.getOrDefault(EveningRoutine.Default)
    }
}
