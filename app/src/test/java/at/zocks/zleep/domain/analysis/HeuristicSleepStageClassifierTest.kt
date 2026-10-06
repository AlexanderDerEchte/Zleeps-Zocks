package at.zocks.zleep.domain.analysis

import at.zocks.zleep.domain.model.EPOCH_LENGTH
import at.zocks.zleep.domain.model.EpochMeasurement
import at.zocks.zleep.domain.model.SleepStage
import at.zocks.zleep.domain.model.SockSide
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant

class HeuristicSleepStageClassifierTest {

    private val classifier = HeuristicSleepStageClassifier()
    private val start = Instant.parse("2026-10-05T21:00:00Z")

    private fun epoch(i: Int, hr: Double?, rmssd: Double?, motion: Double?, side: SockSide = SockSide.LEFT) = EpochMeasurement(
        side, start.plus(EPOCH_LENGTH.multipliedBy(i.toLong())), hr, rmssd, null, 33.0, motion, 6,
    )

    @Test
    fun `agrees with the simulator for most of the night`() {
        val result = ClassifierEvaluation.evaluate(classifier, nights = 20)
        assertThat(result.accuracy).isAtLeast(0.85)
        assertThat(result.recall(SleepStage.AWAKE)).isAtLeast(0.9)
        assertThat(result.recall(SleepStage.DEEP)).isAtLeast(0.8)
        assertThat(result.recall(SleepStage.REM)).isAtLeast(0.65)
        assertThat(result.precision(SleepStage.DEEP)).isAtLeast(0.85)
        assertThat(result.precision(SleepStage.REM)).isAtLeast(0.75)
    }

    @Test
    fun `restless epochs are awake`() {
        val epochs = List(60) { epoch(it, hr = 70.0, rmssd = 30.0, motion = 0.4) }
        assertThat(classifier.classify(epochs).map { it.stage }.toSet()).containsExactly(SleepStage.AWAKE)
    }

    @Test
    fun `a single twitch does not wake you up`() {
        val epochs = List(80) { i -> epoch(i, hr = 55.0, rmssd = 45.0, motion = if (i == 40) 0.25 else 0.01) }
        assertThat(classifier.classify(epochs)[40].stage).isNotEqualTo(SleepStage.AWAKE)
    }

    @Test
    fun `without heart rate only awake or light is possible`() {
        val epochs = List(120) { i -> epoch(i, hr = null, rmssd = null, motion = if (i < 30) 0.4 else 0.005) }
        val stages = classifier.classify(epochs).map { it.stage }.toSet()
        assertThat(stages).containsExactly(SleepStage.AWAKE, SleepStage.LIGHT)
    }

    @Test
    fun `gaps without any data get no stage`() {
        val epochs = List(100) { i -> epoch(i, hr = 55.0, rmssd = 45.0, motion = 0.01) }
            .filterIndexed { i, _ -> i !in 40..49 }
        val stages = classifier.classify(epochs)
        assertThat(stages).hasSize(90)
        assertThat(stages.none { it.start == start.plus(EPOCH_LENGTH.multipliedBy(45)) }).isTrue()
    }

    @Test
    fun `both socks are combined into one stage per epoch`() {
        val left = List(60) { epoch(it, hr = 56.0, rmssd = 45.0, motion = 0.01, side = SockSide.LEFT) }
        val right = List(60) { epoch(it, hr = 58.0, rmssd = 47.0, motion = 0.01, side = SockSide.RIGHT) }
        val stages = classifier.classify(left + right)
        assertThat(stages).hasSize(60)
        assertThat(stages.map { it.start }).isInOrder()
    }

    @Test
    fun `no REM right after falling asleep`() {
        val result = ClassifierEvaluation.simulatedNights(5, seed = 3).map { night ->
            val stages = classifier.classify(night.measurements)
            val onset = stages.first { it.stage != SleepStage.AWAKE }.start
            stages.filter { it.stage == SleepStage.REM }.minOf { Duration.between(onset, it.start) }
        }
        result.forEach { assertThat(it).isAtLeast(Duration.ofMinutes(45)) }
    }

    @Test
    fun `empty input gives no stages`() {
        assertThat(classifier.classify(emptyList())).isEmpty()
    }
}
