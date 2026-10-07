package at.zocks.zleep.domain.routine

import at.zocks.zleep.domain.massage.BuiltInMassagePrograms
import at.zocks.zleep.domain.model.SockSide

/** Wärme in einem Routinenschritt (Heizstufe). */
data class RoutineHeat(val level: Int)

/** Massage in einem Routinenschritt. */
data class RoutineMassage(val programId: String, val intensity: Int)

/**
 * Ein Schritt der Abendroutine: Wärme, Massage, beides gleichzeitig oder eine Pause,
 * jeweils für [durationMinutes].
 */
data class RoutineStep(
    val durationMinutes: Int,
    val heat: RoutineHeat? = null,
    val massage: RoutineMassage? = null,
) {
    val isPause: Boolean get() = heat == null && massage == null
}

/**
 * Frei kombinierbare Abfolge aus Wärme und Massage vor dem Einschlafen. Mit
 * [endWhenAsleep] endet sie, sobald Schlaf erkannt wird.
 */
data class EveningRoutine(
    val steps: List<RoutineStep>,
    val endWhenAsleep: Boolean = true,
    val side: SockSide = SockSide.BOTH,
) {
    val totalMinutes: Int get() = steps.sumOf { it.durationMinutes }

    /** Grenzen einhalten: Schrittzahl, Schrittdauer, Stufe und Intensität. */
    fun normalized(maxHeatLevel: Int = MAX_HEAT_LEVEL): EveningRoutine = copy(
        steps = steps.take(MAX_STEPS).map { step ->
            step.copy(
                durationMinutes = step.durationMinutes.coerceIn(MIN_STEP_MINUTES, MAX_STEP_MINUTES),
                heat = step.heat?.let { it.copy(level = it.level.coerceIn(1, maxHeatLevel)) },
                massage = step.massage?.let { it.copy(intensity = it.intensity.coerceIn(MIN_INTENSITY, 100)) },
            )
        },
    )

    companion object {
        const val MAX_STEPS = 8
        const val MIN_STEP_MINUTES = 1
        const val MAX_STEP_MINUTES = 60
        const val MAX_HEAT_LEVEL = 5
        const val MIN_INTENSITY = 10

        /** Vorschlag: vorwärmen, dann Wärme mit sanfter Massage, zuletzt die Schlafmassage. */
        val Default = EveningRoutine(
            steps = listOf(
                RoutineStep(15, heat = RoutineHeat(3)),
                RoutineStep(15, heat = RoutineHeat(2), massage = RoutineMassage(BuiltInMassagePrograms.RELAX.id, 40)),
                RoutineStep(20, massage = RoutineMassage(BuiltInMassagePrograms.SLEEP.id, 30)),
            ),
        )
    }
}
