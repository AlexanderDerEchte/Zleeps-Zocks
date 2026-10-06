package at.zocks.zleep.domain.recording

import at.zocks.zleep.domain.model.SensorSample
import at.zocks.zleep.domain.model.SockSide
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class EpochAggregatorTest {

    private val start = Instant.parse("2026-10-05T21:00:00Z")
    private val aggregator = EpochAggregator()

    private fun sample(second: Long, side: SockSide = SockSide.LEFT, hr: Int? = 60, skin: Double? = 33.0, motion: Double? = 0.1) =
        SensorSample(side, start.plusSeconds(second), hr, 40.0, null, skin, motion, null)

    @Test
    fun `averages values within an epoch and emits it when the next begins`() {
        assertThat(aggregator.add(sample(0, hr = 60))).isNull()
        assertThat(aggregator.add(sample(10, hr = 62))).isNull()
        assertThat(aggregator.add(sample(25, hr = 64))).isNull()
        val epoch = aggregator.add(sample(31, hr = 70))!!
        assertThat(epoch.start).isEqualTo(start)
        assertThat(epoch.heartRateBpm).isWithin(0.001).of(62.0)
        assertThat(epoch.sampleCount).isEqualTo(3)
    }

    @Test
    fun `missing values stay missing`() {
        aggregator.add(sample(0, hr = null, skin = null))
        aggregator.add(sample(5, hr = null, skin = 33.5))
        val epoch = aggregator.add(sample(30))!!
        assertThat(epoch.heartRateBpm).isNull()
        assertThat(epoch.spo2Percent).isNull()
        assertThat(epoch.skinTemperatureC).isWithin(0.001).of(33.5)
    }

    @Test
    fun `sides are aggregated separately`() {
        aggregator.add(sample(0, SockSide.LEFT, hr = 60))
        aggregator.add(sample(1, SockSide.RIGHT, hr = 80))
        val left = aggregator.add(sample(30, SockSide.LEFT))!!
        assertThat(left.side).isEqualTo(SockSide.LEFT)
        assertThat(left.heartRateBpm).isWithin(0.001).of(60.0)
        val rest = aggregator.flush()
        assertThat(rest.map { it.side }).containsExactly(SockSide.LEFT, SockSide.RIGHT)
        assertThat(rest.single { it.side == SockSide.RIGHT }.heartRateBpm).isWithin(0.001).of(80.0)
    }

    @Test
    fun `late samples are dropped and epochs align to 30 seconds`() {
        aggregator.add(sample(40))
        assertThat(aggregator.add(sample(5))).isNull()
        assertThat(EpochAggregator.epochStart(start.plusSeconds(59))).isEqualTo(start.plusSeconds(30))
        assertThat(aggregator.flush().single().sampleCount).isEqualTo(1)
    }
}
