package at.zocks.zleep.device.simulator

import at.zocks.zleep.domain.model.NightEventType
import at.zocks.zleep.domain.model.NightSource
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.domain.model.Tag
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

class DemoNightsGeneratorTest {

    private val zone = ZoneId.of("Europe/Vienna")
    private val tags = Tag.BuiltInKeys.mapIndexed { index, key -> key to Tag(index + 1L, key, null) }.toMap()
    private val generator = DemoNightsGenerator(NightScenarioGenerator())
    private val lastNight = LocalDate.of(2026, 10, 5)

    @Test
    fun `generates consecutive complete nights`() {
        val nights = generator.generate(lastNight, 30, zone, tags)
        assertThat(nights).hasSize(30)
        assertThat(nights.map { it.night.nightOf(zone) })
            .containsExactlyElementsIn((0L until 30L).map { lastNight.minusDays(29 - it) })
            .inOrder()

        nights.forEach { data ->
            val night = data.night
            assertThat(night.source).isEqualTo(NightSource.DEMO)
            assertThat(night.end).isNotNull()
            assertThat(night.sleepOnset).isGreaterThan(night.start)
            assertThat(night.finalWake).isLessThan(night.end)
            assertThat(Duration.between(night.sleepOnset, night.finalWake).toHours()).isAtLeast(5)
            assertThat(data.stages).isNotEmpty()
            assertThat(data.measurements.map { it.side }.toSet()).containsExactly(SockSide.LEFT, SockSide.RIGHT)
            assertThat(night.tags).containsNoDuplicates()
            data.events.forEach { event ->
                assertThat(event.type).isAnyOf(NightEventType.HEAT, NightEventType.MASSAGE)
                assertThat(event.end).isGreaterThan(event.start)
            }
        }
    }

    @Test
    fun `gaps leave no measurements for the affected sock`() {
        val nights = generator.generate(lastNight, 60, zone, tags, seed = 5)
        val withGap = nights.filter { it.gaps.isNotEmpty() }
        assertThat(withGap).isNotEmpty()
        withGap.forEach { data ->
            val gap = data.gaps.single()
            val inGap = data.measurements.filter { it.side == gap.side && !it.start.isBefore(gap.start) && it.start.isBefore(gap.end) }
            assertThat(inGap).isEmpty()
        }
    }

    @Test
    fun `built in correlations are visible across many nights`() {
        val nights = generator.generate(lastNight, 300, zone, tags, seed = 11)
        fun latency(withCaffeine: Boolean) = nights
            .filter { data -> data.night.tags.any { it.key == Tag.CAFFEINE } == withCaffeine }
            .map { Duration.between(it.night.start, it.night.sleepOnset).toMinutes() }
            .average()
        assertThat(latency(withCaffeine = true)).isGreaterThan(latency(withCaffeine = false) + 5)
    }

    @Test
    fun `same seed is reproducible`() {
        val a = generator.generate(lastNight, 3, zone, tags, seed = 9)
        val b = generator.generate(lastNight, 3, zone, tags, seed = 9)
        assertThat(a).isEqualTo(b)
    }
}
