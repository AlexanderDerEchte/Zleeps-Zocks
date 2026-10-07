package at.zocks.zleep.domain.analysis

import at.zocks.zleep.domain.model.SleepStage
import at.zocks.zleep.testing.testSummary
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration

class SleepScoreCalculatorTest {

    private val goal = Duration.ofHours(8)

    private fun points(score: SleepScore?, component: ScoreComponent) = score!!.parts.single { it.component == component }.points

    @Test
    fun `an ideal night scores 100`() {
        val score = SleepScoreCalculator.calculate(testSummary(), goal)!!
        assertThat(score.value).isEqualTo(100)
        assertThat(score.parts.sumOf { it.maxPoints }).isEqualTo(100)
    }

    @Test
    fun `duration ramps from half to full goal`() {
        assertThat(points(SleepScoreCalculator.calculate(testSummary(totalSleepMinutes = 240), goal), ScoreComponent.DURATION)).isEqualTo(0)
        assertThat(points(SleepScoreCalculator.calculate(testSummary(totalSleepMinutes = 241), goal), ScoreComponent.DURATION)).isEqualTo(0)
        assertThat(points(SleepScoreCalculator.calculate(testSummary(totalSleepMinutes = 360), goal), ScoreComponent.DURATION)).isEqualTo(18)
        assertThat(points(SleepScoreCalculator.calculate(testSummary(totalSleepMinutes = 479), goal), ScoreComponent.DURATION)).isEqualTo(35)
        assertThat(points(SleepScoreCalculator.calculate(testSummary(totalSleepMinutes = 470), goal), ScoreComponent.DURATION)).isEqualTo(34)
        assertThat(points(SleepScoreCalculator.calculate(testSummary(totalSleepMinutes = 600), goal), ScoreComponent.DURATION)).isEqualTo(35)
    }

    @Test
    fun `efficiency ramps from 65 to 90 percent`() {
        fun eff(value: Double) = points(SleepScoreCalculator.calculate(testSummary(efficiency = value), goal), ScoreComponent.EFFICIENCY)
        assertThat(eff(0.64)).isEqualTo(0)
        assertThat(eff(0.65)).isEqualTo(0)
        assertThat(eff(0.66)).isEqualTo(1)
        assertThat(eff(0.89)).isEqualTo(24)
        assertThat(eff(0.90)).isEqualTo(25)
    }

    @Test
    fun `latency and continuity lose points the longer you are awake`() {
        fun latency(min: Long) = points(SleepScoreCalculator.calculate(testSummary(latencyMinutes = min), goal), ScoreComponent.LATENCY)
        assertThat(latency(15)).isEqualTo(10)
        assertThat(latency(16)).isEqualTo(10)
        assertThat(latency(20)).isEqualTo(9)
        assertThat(latency(59)).isEqualTo(0)
        assertThat(latency(60)).isEqualTo(0)

        fun waso(min: Long) = points(SleepScoreCalculator.calculate(testSummary(wakeAfterOnsetMinutes = min), goal), ScoreComponent.CONTINUITY)
        assertThat(waso(10)).isEqualTo(10)
        assertThat(waso(15)).isEqualTo(9)
        assertThat(waso(35)).isEqualTo(5)
        assertThat(waso(60)).isEqualTo(0)
    }

    @Test
    fun `restorative share counts deep plus rem`() {
        fun restorative(deep: Int, rem: Int, light: Int) = points(
            SleepScoreCalculator.calculate(
                testSummary(stageMinutes = mapOf(SleepStage.DEEP to deep, SleepStage.REM to rem, SleepStage.LIGHT to light)),
                goal,
            ),
            ScoreComponent.RESTORATIVE,
        )
        assertThat(restorative(deep = 15, rem = 0, light = 85)).isEqualTo(0)
        assertThat(restorative(deep = 20, rem = 7, light = 73)).isEqualTo(10)
        assertThat(restorative(deep = 20, rem = 20, light = 60)).isEqualTo(20)
    }

    @Test
    fun `missing parts are left out and the score is scaled to the rest`() {
        val summary = testSummary(efficiency = null, stageMinutes = emptyMap(), wakeAfterOnsetMinutes = null, latencyMinutes = 37)
        val score = SleepScoreCalculator.calculate(summary, goal)!!

        assertThat(points(score, ScoreComponent.EFFICIENCY)).isNull()
        assertThat(points(score, ScoreComponent.RESTORATIVE)).isNull()
        // Dauer 35/35 + Einschlafen 5/10 = 40 von 45 Punkten.
        assertThat(score.value).isEqualTo(89)
    }

    @Test
    fun `no score without sleep duration`() {
        assertThat(SleepScoreCalculator.calculate(testSummary(totalSleepMinutes = null), goal)).isNull()
    }

    @Test
    fun `the weakest part is the one furthest from its maximum`() {
        val score = SleepScoreCalculator.calculate(testSummary(latencyMinutes = 45, totalSleepMinutes = 420), goal)!!
        assertThat(score.weakestPart!!.component).isEqualTo(ScoreComponent.LATENCY)
        assertThat(SleepScoreCalculator.calculate(testSummary(), goal)!!.weakestPart!!.points).isEqualTo(35)
    }

    @Test
    fun `ramp is linear and clamped`() {
        assertThat(SleepScoreCalculator.ramp(5.0, 10.0, 20.0)).isEqualTo(0.0)
        assertThat(SleepScoreCalculator.ramp(15.0, 10.0, 20.0)).isEqualTo(0.5)
        assertThat(SleepScoreCalculator.ramp(25.0, 10.0, 20.0)).isEqualTo(1.0)
    }
}
