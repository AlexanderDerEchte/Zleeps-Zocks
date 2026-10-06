package at.zocks.zleep.domain.analysis

import at.zocks.zleep.domain.model.ConnectionGap
import at.zocks.zleep.domain.model.EpochMeasurement
import at.zocks.zleep.domain.model.Night
import at.zocks.zleep.domain.model.NightData
import at.zocks.zleep.domain.model.NightSource
import at.zocks.zleep.domain.model.SleepStage
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.domain.model.StageEpoch
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant

class NightStatisticsCalculatorTest {

    private val start = Instant.parse("2026-10-05T20:30:00Z")
    private fun at(minutes: Long) = start.plus(Duration.ofMinutes(minutes))

    private fun measurement(minute: Long, hr: Double?, temp: Double? = 33.0) = EpochMeasurement(
        side = SockSide.LEFT,
        start = at(minute),
        heartRateBpm = hr,
        hrvRmssdMs = null,
        spo2Percent = null,
        skinTemperatureC = temp,
        motion = 0.0,
        sampleCount = 6,
    )

    private val night = Night(
        id = 1,
        start = start,
        end = at(120),
        sleepOnset = at(10),
        finalWake = at(100),
        source = NightSource.SIMULATOR,
        note = null,
        tags = emptyList(),
    )

    @Test
    fun `averages only measured values inside the sleep window`() {
        val data = NightData(
            night = night,
            measurements = listOf(
                measurement(0, hr = 90.0), // vor dem Einschlafen
                measurement(20, hr = 50.0),
                measurement(30, hr = null), // nicht gemessen
                measurement(40, hr = 60.0),
                measurement(110, hr = 95.0), // nach dem Aufwachen
            ),
            stages = emptyList(),
            events = emptyList(),
            gaps = emptyList(),
        )
        val stats = NightStatisticsCalculator.calculate(data, now = at(200))
        assertThat(stats.avgHeartRateBpm).isWithin(0.001).of(55.0)
        assertThat(stats.avgHrvRmssdMs).isNull()
        assertThat(stats.avgSpo2Percent).isNull()
        assertThat(stats.avgSkinTemperatureC).isWithin(0.001).of(33.0)
        assertThat(stats.sleepWindow).isEqualTo(Duration.ofMinutes(90))
    }

    @Test
    fun `stage minutes and shares`() {
        val stages = List(20) { StageEpoch(at(it / 2L), SleepStage.LIGHT) } +
            List(10) { StageEpoch(at(10 + it / 2L), SleepStage.DEEP) } +
            List(10) { StageEpoch(at(15 + it / 2L), SleepStage.REM) } +
            List(4) { StageEpoch(at(20 + it / 2L), SleepStage.AWAKE) }
        val stats = NightStatisticsCalculator.calculate(NightData(night, emptyList(), stages, emptyList(), emptyList()), at(200))
        assertThat(stats.stageMinutes).containsExactly(
            SleepStage.LIGHT, 10,
            SleepStage.DEEP, 5,
            SleepStage.REM, 5,
            SleepStage.AWAKE, 2,
        )
        assertThat(stats.stageShare(SleepStage.LIGHT)).isEqualTo(50)
        assertThat(stats.stageShare(SleepStage.DEEP)).isEqualTo(25)
        assertThat(stats.stageShare(SleepStage.AWAKE)).isNull()
    }

    @Test
    fun `open gaps count until the end of the night`() {
        val gaps = listOf(
            ConnectionGap(side = SockSide.LEFT, start = at(30), end = at(35)),
            ConnectionGap(side = SockSide.RIGHT, start = at(110), end = null),
        )
        val stats = NightStatisticsCalculator.calculate(NightData(night, emptyList(), emptyList(), emptyList(), gaps), at(200))
        assertThat(stats.gapDuration).isEqualTo(Duration.ofMinutes(15))
    }
}
