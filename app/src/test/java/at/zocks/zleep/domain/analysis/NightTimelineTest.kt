package at.zocks.zleep.domain.analysis

import at.zocks.zleep.domain.model.NightEvent
import at.zocks.zleep.domain.model.NightEventType
import at.zocks.zleep.domain.model.SleepStage.AWAKE
import at.zocks.zleep.domain.model.SleepStage.DEEP
import at.zocks.zleep.domain.model.SleepStage.LIGHT
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.testing.TEST_NIGHT_START
import at.zocks.zleep.testing.minutes
import at.zocks.zleep.testing.testNightData
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant

class NightTimelineTest {

    private fun at(minutes: Long): Instant = TEST_NIGHT_START.plus(Duration.ofMinutes(minutes))

    private val data = testNightData(
        listOf(AWAKE to minutes(2), LIGHT to minutes(4), DEEP to minutes(4)),
        events = listOf(
            NightEvent(type = NightEventType.HEAT, side = SockSide.BOTH, start = at(0), end = at(3)),
            NightEvent(type = NightEventType.MASSAGE, side = SockSide.BOTH, start = at(8), end = null),
        ),
    )

    @Test
    fun `merges equal stages into spans`() {
        val timeline = NightTimeline.from(data, now = at(60))

        assertThat(timeline.stages.map { it.second }).containsExactly(AWAKE, LIGHT, DEEP).inOrder()
        assertThat(timeline.stages[1].first).isEqualTo(TimelineSpan(at(2), at(6)))
        assertThat(timeline.end).isEqualTo(at(10))
    }

    @Test
    fun `averages both socks per two minute bucket`() {
        val timeline = NightTimeline.from(data, now = at(60))

        assertThat(timeline.heartRate).hasSize(5)
        assertThat(timeline.heartRate.first().value).isEqualTo(71.0)
        assertThat(timeline.heartRate.last().value).isEqualTo(56.0)
        assertThat(timeline.heartRate.first().time).isEqualTo(at(1))
        assertThat(timeline.hasVitals).isTrue()
    }

    @Test
    fun `buckets without measurements stay empty`() {
        val withHole = data.copy(measurements = data.measurements.filterNot { it.start >= at(4) && it.start < at(6) })
        val timeline = NightTimeline.from(withHole, now = at(60))

        assertThat(timeline.heartRate[2].value).isNull()
        assertThat(timeline.heartRate[3].value).isNotNull()
    }

    @Test
    fun `open events last until the end of the night`() {
        val timeline = NightTimeline.from(data, now = at(60))

        assertThat(timeline.heat).containsExactly(TimelineSpan(at(0), at(3)))
        assertThat(timeline.massage).containsExactly(TimelineSpan(at(8), at(10)))
    }

    @Test
    fun `reads all values at a point in time`() {
        val timeline = NightTimeline.from(data, now = at(60))
        val reading = timeline.valuesAt(at(7))

        assertThat(reading.stage).isEqualTo(DEEP)
        assertThat(reading.heartRate).isEqualTo(56.0)
        assertThat(reading.skinTemperature).isWithin(0.001).of(33.2)
        assertThat(timeline.valuesAt(at(30)).heartRate).isNull()
    }

    @Test
    fun `a night without measurements has no vitals`() {
        val timeline = NightTimeline.from(data.copy(measurements = emptyList()), now = at(60))
        assertThat(timeline.hasVitals).isFalse()
        assertThat(timeline.heartRate.all { it.value == null }).isTrue()
    }
}
