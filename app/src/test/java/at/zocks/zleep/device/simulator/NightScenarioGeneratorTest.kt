package at.zocks.zleep.device.simulator

import at.zocks.zleep.domain.model.EPOCH_LENGTH
import at.zocks.zleep.domain.model.SleepStage
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

class NightScenarioGeneratorTest {

    private val generator = NightScenarioGenerator()
    private val start = Instant.parse("2026-10-05T20:45:00Z")

    private fun night(profile: NightProfile = NightProfile(), seed: Long = 7) = generator.generate(start, profile, Random(seed))

    private fun List<Physiology>.mean(stage: SleepStage, selector: (Physiology) -> Double) =
        filter { it.stage == stage }.map(selector).average()

    @Test
    fun `same seed gives the same night`() {
        assertThat(night(seed = 3).epochs).isEqualTo(night(seed = 3).epochs)
        assertThat(night(seed = 3).epochs).isNotEqualTo(night(seed = 4).epochs)
    }

    @Test
    fun `total sleep and latency follow the profile`() {
        val profile = NightProfile(sleepLatencyMinutes = 20.0, totalSleepMinutes = 420.0)
        repeat(10) { seed ->
            val scenario = night(profile, seed.toLong())
            val asleep = SleepStage.entries.filter { it != SleepStage.AWAKE }.sumOf { scenario.minutesIn(it) }
            assertThat(asleep).isWithin(10.0).of(420.0)
            val latency = Duration.between(scenario.start, scenario.sleepOnset).toMinutes()
            assertThat(latency).isIn(Range.closed(19L, 21L))
            assertThat(scenario.finalWake).isLessThan(scenario.end)
        }
    }

    @Test
    fun `stage shares are in a healthy range`() {
        repeat(20) { seed ->
            val scenario = night(seed = seed.toLong())
            val asleep = SleepStage.entries.filter { it != SleepStage.AWAKE }.sumOf { scenario.minutesIn(it) }
            val deep = scenario.minutesIn(SleepStage.DEEP) / asleep
            val rem = scenario.minutesIn(SleepStage.REM) / asleep
            assertThat(deep).isIn(Range.closed(0.10, 0.30))
            assertThat(rem).isIn(Range.closed(0.15, 0.32))
        }
    }

    @Test
    fun `deep sleep dominates the first half, REM the second`() {
        val scenario = night()
        val half = scenario.epochs.size / 2
        val first = scenario.epochs.take(half)
        val second = scenario.epochs.drop(half)
        assertThat(first.count { it.stage == SleepStage.DEEP }).isGreaterThan(second.count { it.stage == SleepStage.DEEP })
        assertThat(second.count { it.stage == SleepStage.REM }).isGreaterThan(first.count { it.stage == SleepStage.REM })
    }

    @Test
    fun `physiology differs by stage as expected`() {
        val epochs = (1..5L).flatMap { night(seed = it).epochs }
        val hrDeep = epochs.mean(SleepStage.DEEP) { it.heartRateBpm }
        val hrLight = epochs.mean(SleepStage.LIGHT) { it.heartRateBpm }
        val hrRem = epochs.mean(SleepStage.REM) { it.heartRateBpm }
        val hrAwake = epochs.mean(SleepStage.AWAKE) { it.heartRateBpm }
        assertThat(hrDeep).isLessThan(hrLight)
        assertThat(hrLight).isLessThan(hrRem)
        assertThat(hrRem).isLessThan(hrAwake)

        assertThat(epochs.mean(SleepStage.DEEP) { it.hrvRmssdMs }).isGreaterThan(epochs.mean(SleepStage.REM) { it.hrvRmssdMs })
        assertThat(epochs.mean(SleepStage.AWAKE) { it.motion }).isGreaterThan(10 * epochs.mean(SleepStage.DEEP) { it.motion })
    }

    @Test
    fun `values stay in plausible ranges`() {
        night().epochs.forEach {
            assertThat(it.heartRateBpm).isIn(Range.closed(38.0, 140.0))
            assertThat(it.hrvRmssdMs).isIn(Range.closed(8.0, 160.0))
            assertThat(it.spo2Percent).isIn(Range.closed(90.0, 99.5))
            assertThat(it.skinTemperatureC).isIn(Range.closed(28.0, 36.0))
            assertThat(it.motion).isIn(Range.closed(0.0, 1.0))
        }
    }

    @Test
    fun `feet warm up after falling asleep`() {
        val scenario = night()
        val epochsPerHour = (3600 / EPOCH_LENGTH.seconds).toInt()
        val beforeSleep = scenario.epochs.take(10).map { it.skinTemperatureC }.average()
        val laterInNight = scenario.epochs.drop(2 * epochsPerHour).take(epochsPerHour).map { it.skinTemperatureC }.average()
        assertThat(laterInNight - beforeSleep).isGreaterThan(1.5)
    }

    @Test
    fun `profile factors shift the night`() {
        val moreDeep = night(NightProfile(deepFactor = 1.3)).minutesIn(SleepStage.DEEP)
        val lessDeep = night(NightProfile(deepFactor = 0.7)).minutesIn(SleepStage.DEEP)
        assertThat(moreDeep).isGreaterThan(lessDeep)

        fun awakeAfterOnset(profile: NightProfile) = night(profile).let { scenario ->
            val onset = scenario.epochs.indexOfFirst { it.stage != SleepStage.AWAKE }
            val wake = scenario.epochs.indexOfLast { it.stage != SleepStage.AWAKE }
            scenario.epochs.subList(onset, wake).count { it.stage == SleepStage.AWAKE }
        }
        assertThat(awakeAfterOnset(NightProfile(extraAwakenings = 4))).isGreaterThan(awakeAfterOnset(NightProfile()))
    }

    @Test
    fun `epochAt maps time to the right epoch`() {
        val scenario = night()
        assertThat(scenario.epochAt(start.minusSeconds(1))).isNull()
        assertThat(scenario.epochAt(start)).isEqualTo(scenario.epochs[0])
        assertThat(scenario.epochAt(start.plusSeconds(45))).isEqualTo(scenario.epochs[1])
        assertThat(scenario.epochAt(scenario.end)).isNull()
    }
}
