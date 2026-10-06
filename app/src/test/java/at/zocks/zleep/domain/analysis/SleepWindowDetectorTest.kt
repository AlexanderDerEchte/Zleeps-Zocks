package at.zocks.zleep.domain.analysis

import at.zocks.zleep.domain.model.EPOCH_LENGTH
import at.zocks.zleep.domain.model.SleepStage
import at.zocks.zleep.domain.model.StageEpoch
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant

class SleepWindowDetectorTest {

    private val start = Instant.parse("2026-10-05T21:00:00Z")

    private fun stages(vararg runs: Pair<SleepStage, Int>): List<StageEpoch> {
        var i = 0L
        return runs.flatMap { (stage, count) -> List(count) { StageEpoch(start.plus(EPOCH_LENGTH.multipliedBy(i++)), stage) } }
    }

    private fun at(epoch: Int) = start.plus(EPOCH_LENGTH.multipliedBy(epoch.toLong()))

    @Test
    fun `finds onset and final wake around short awakenings`() {
        val window = SleepWindowDetector.detect(
            stages(
                SleepStage.AWAKE to 30,
                SleepStage.LIGHT to 100,
                SleepStage.AWAKE to 4, // kurzes Aufwachen gehört dazu
                SleepStage.DEEP to 100,
                SleepStage.AWAKE to 40,
            ),
        )!!
        assertThat(window.sleepOnset).isEqualTo(at(30))
        assertThat(window.finalWake).isEqualTo(at(234))
    }

    @Test
    fun `a short nap while falling asleep is not the onset`() {
        val window = SleepWindowDetector.detect(
            stages(SleepStage.AWAKE to 20, SleepStage.LIGHT to 3, SleepStage.AWAKE to 20, SleepStage.LIGHT to 60),
        )!!
        assertThat(window.sleepOnset).isEqualTo(at(43))
    }

    @Test
    fun `dozing off again in the morning still counts as sleep`() {
        val window = SleepWindowDetector.detect(
            stages(SleepStage.AWAKE to 10, SleepStage.LIGHT to 200, SleepStage.AWAKE to 6, SleepStage.LIGHT to 14, SleepStage.AWAKE to 30),
        )!!
        assertThat(window.finalWake).isEqualTo(at(230))
    }

    @Test
    fun `getting up for a while ends the night before it`() {
        val window = SleepWindowDetector.detect(
            stages(SleepStage.AWAKE to 10, SleepStage.LIGHT to 200, SleepStage.AWAKE to 30, SleepStage.LIGHT to 5, SleepStage.AWAKE to 30),
        )!!
        assertThat(window.finalWake).isEqualTo(at(210))
    }

    @Test
    fun `no stable sleep means no window`() {
        assertThat(SleepWindowDetector.detect(stages(SleepStage.AWAKE to 60))).isNull()
        assertThat(SleepWindowDetector.detect(stages(SleepStage.LIGHT to 10))).isNull()
    }

    @Test
    fun `matches the simulator within a few minutes`() {
        val classifier = HeuristicSleepStageClassifier()
        ClassifierEvaluation.simulatedNights(10, seed = 21).forEach { night ->
            val window = SleepWindowDetector.detect(classifier.classify(night.measurements))!!
            assertThat(Duration.between(night.night.sleepOnset, window.sleepOnset).abs()).isAtMost(Duration.ofMinutes(5))
            assertThat(Duration.between(night.night.finalWake, window.finalWake).abs()).isAtMost(Duration.ofMinutes(5))
        }
    }
}
