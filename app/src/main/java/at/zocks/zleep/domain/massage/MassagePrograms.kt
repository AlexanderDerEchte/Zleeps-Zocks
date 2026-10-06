package at.zocks.zleep.domain.massage

import at.zocks.zleep.domain.model.MassagePattern
import at.zocks.zleep.domain.model.MassageStep
import at.zocks.zleep.domain.model.MassageZone
import at.zocks.zleep.domain.model.SockSide

/** Grundform eines Massageprogramms. */
enum class MassageProgramType {
    /** Welle von der Ferse zu den Zehen. */
    WAVE,

    /** Alle Zonen gemeinsam an/aus. */
    PULSE,

    /** Ferse+Ballen und Gewölbe+Zehen im Wechsel. */
    KNEAD,

    /** Langsame, sanfte Welle. */
    RELAX,

    /** Links und rechts abwechselnd. */
    ALTERNATE,

    /** Sehr sanfte Welle von den Zehen zur Ferse, klingt lange aus. */
    SLEEP,
}

enum class MassageTempo(val stepMs: Long) { SLOW(1_200), MEDIUM(700), FAST(400) }

data class MassageProgram(
    /** „builtin:wave“ oder „custom:12“. */
    val id: String,
    val type: MassageProgramType,
    /** Nur bei eigenen Programmen; eingebaute werden in der UI übersetzt. */
    val name: String?,
    val intensity: Int,
    val durationMinutes: Int,
    val tempo: MassageTempo,
    val zones: Set<MassageZone> = MassageZone.entries.toSet(),
) {
    val isBuiltIn: Boolean get() = id.startsWith(BUILT_IN_PREFIX)

    companion object {
        const val BUILT_IN_PREFIX = "builtin:"
        const val CUSTOM_PREFIX = "custom:"

        fun customId(databaseId: Long) = "$CUSTOM_PREFIX$databaseId"
        fun databaseIdOf(id: String): Long? = id.removePrefix(CUSTOM_PREFIX).takeIf { id.startsWith(CUSTOM_PREFIX) }?.toLongOrNull()
    }
}

object BuiltInMassagePrograms {
    val WAVE = MassageProgram("builtin:wave", MassageProgramType.WAVE, null, 60, 15, MassageTempo.MEDIUM)
    val PULSE = MassageProgram("builtin:pulse", MassageProgramType.PULSE, null, 60, 10, MassageTempo.MEDIUM)
    val KNEAD = MassageProgram("builtin:knead", MassageProgramType.KNEAD, null, 70, 15, MassageTempo.MEDIUM)
    val RELAX = MassageProgram("builtin:relax", MassageProgramType.RELAX, null, 40, 20, MassageTempo.SLOW)
    val RECOVERY = MassageProgram("builtin:recovery", MassageProgramType.ALTERNATE, null, 55, 15, MassageTempo.MEDIUM)
    val INTENSE = MassageProgram("builtin:intense", MassageProgramType.PULSE, null, 90, 5, MassageTempo.FAST)
    val SLEEP = MassageProgram("builtin:sleep", MassageProgramType.SLEEP, null, 35, 10, MassageTempo.SLOW)

    val all = listOf(RELAX, WAVE, KNEAD, PULSE, RECOVERY, INTENSE, SLEEP)

    fun byId(id: String): MassageProgram? = all.firstOrNull { it.id == id }
}

/**
 * Übersetzt ein Programm in ein Vibrationsmuster je Socke. Zonen, die das Programm nicht
 * nutzt, bleiben aus. Bei [MassageProgramType.ALTERNATE] ist die rechte Socke gegenphasig.
 */
object MassagePatterns {

    private val heelToToes = listOf(MassageZone.HEEL, MassageZone.ARCH, MassageZone.BALL, MassageZone.TOES)

    fun patternFor(program: MassageProgram, side: SockSide): MassagePattern {
        val step = program.tempo.stepMs
        val steps = when (program.type) {
            MassageProgramType.WAVE -> wave(heelToToes, step, peak = 1f, neighbour = 0.4f)
            MassageProgramType.RELAX -> wave(heelToToes, step * 2, peak = 0.6f, neighbour = 0.35f)
            MassageProgramType.SLEEP -> wave(heelToToes.reversed(), step * 2, peak = 0.5f, neighbour = 0.25f)
            MassageProgramType.PULSE -> listOf(all(1f, step), all(0f, step))
            MassageProgramType.KNEAD -> listOf(
                MassageStep(step, mapOf(MassageZone.HEEL to 1f, MassageZone.BALL to 1f, MassageZone.ARCH to 0f, MassageZone.TOES to 0f)),
                MassageStep(step, mapOf(MassageZone.HEEL to 0f, MassageZone.BALL to 0f, MassageZone.ARCH to 1f, MassageZone.TOES to 1f)),
            )
            MassageProgramType.ALTERNATE -> {
                val on = all(0.8f, step * 2)
                val off = all(0f, step * 2)
                if (side == SockSide.RIGHT) listOf(off, on) else listOf(on, off)
            }
        }
        return MassagePattern(
            id = program.id,
            steps = steps.map { s -> s.copy(zoneLevels = s.zoneLevels.mapValues { (zone, level) -> if (zone in program.zones) level else 0f }) },
        )
    }

    private fun all(level: Float, durationMs: Long) = MassageStep(durationMs, MassageZone.entries.associateWith { level })

    private fun wave(order: List<MassageZone>, stepMs: Long, peak: Float, neighbour: Float): List<MassageStep> =
        order.mapIndexed { index, zone ->
            MassageStep(
                stepMs,
                MassageZone.entries.associateWith { other ->
                    val distance = kotlin.math.abs(order.indexOf(other) - index)
                    when {
                        other == zone -> peak
                        distance == 1 -> neighbour
                        else -> 0f
                    }
                },
            )
        }
}
