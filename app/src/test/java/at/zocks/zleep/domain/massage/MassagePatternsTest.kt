package at.zocks.zleep.domain.massage

import at.zocks.zleep.domain.model.MassageZone
import at.zocks.zleep.domain.model.SockSide
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MassagePatternsTest {

    @Test
    fun `every built in program produces a valid pattern`() {
        BuiltInMassagePrograms.all.forEach { program ->
            val pattern = MassagePatterns.patternFor(program, SockSide.LEFT)
            assertThat(pattern.id).isEqualTo(program.id)
            assertThat(pattern.steps).isNotEmpty()
            pattern.steps.forEach { step ->
                assertThat(step.durationMs).isAtLeast(MassageTempo.FAST.stepMs)
                assertThat(step.zoneLevels.keys).containsExactlyElementsIn(MassageZone.entries)
                step.zoneLevels.values.forEach { assertThat(it).isIn(Range.closed(0f, 1f)) }
            }
            // Mindestens ein Schritt vibriert wirklich.
            assertThat(pattern.steps.flatMap { it.zoneLevels.values }.max()).isGreaterThan(0f)
        }
    }

    @Test
    fun `wave moves from heel to toes`() {
        val peaks = MassagePatterns.patternFor(BuiltInMassagePrograms.WAVE, SockSide.LEFT).steps
            .map { step -> step.zoneLevels.maxBy { it.value }.key }
        assertThat(peaks).containsExactly(MassageZone.HEEL, MassageZone.ARCH, MassageZone.BALL, MassageZone.TOES).inOrder()
    }

    @Test
    fun `alternate program runs the right sock in opposite phase`() {
        val left = MassagePatterns.patternFor(BuiltInMassagePrograms.RECOVERY, SockSide.LEFT).steps
        val right = MassagePatterns.patternFor(BuiltInMassagePrograms.RECOVERY, SockSide.RIGHT).steps
        left.zip(right).forEach { (l, r) ->
            assertThat(l.zoneLevels.values.sum() > 0f).isNotEqualTo(r.zoneLevels.values.sum() > 0f)
        }
    }

    @Test
    fun `unused zones stay off`() {
        val program = BuiltInMassagePrograms.PULSE.copy(zones = setOf(MassageZone.HEEL))
        MassagePatterns.patternFor(program, SockSide.LEFT).steps.forEach { step ->
            assertThat(step.zoneLevels.filterKeys { it != MassageZone.HEEL }.values.toSet()).containsExactly(0f)
        }
    }

    @Test
    fun `tempo changes the step length`() {
        val slow = MassagePatterns.patternFor(BuiltInMassagePrograms.WAVE.copy(tempo = MassageTempo.SLOW), SockSide.LEFT)
        val fast = MassagePatterns.patternFor(BuiltInMassagePrograms.WAVE.copy(tempo = MassageTempo.FAST), SockSide.LEFT)
        assertThat(slow.steps.first().durationMs).isGreaterThan(fast.steps.first().durationMs)
    }

    @Test
    fun `program ids`() {
        assertThat(MassageProgram.customId(12)).isEqualTo("custom:12")
        assertThat(MassageProgram.databaseIdOf("custom:12")).isEqualTo(12)
        assertThat(MassageProgram.databaseIdOf("builtin:wave")).isNull()
        assertThat(BuiltInMassagePrograms.all.map { it.id }).containsNoDuplicates()
        assertThat(BuiltInMassagePrograms.all.all { it.isBuiltIn }).isTrue()
    }
}
