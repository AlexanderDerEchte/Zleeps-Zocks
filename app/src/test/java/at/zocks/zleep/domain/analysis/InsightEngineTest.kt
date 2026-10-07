package at.zocks.zleep.domain.analysis

import at.zocks.zleep.domain.model.Tag
import at.zocks.zleep.testing.testSummary
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

class InsightEngineTest {

    private val goal = Duration.ofHours(8)
    private val caffeine = Tag(1, Tag.CAFFEINE, null)

    /** [with] Nächte mit Koffein und [latencyWith] Minuten Einschlafdauer, sonst 10 Minuten. */
    private fun nights(with: Int, without: Int, latencyWith: Long, heatOnAll: Boolean = false): List<NightForInsights> =
        (0 until with + without).map { i ->
            val tagged = i < with
            NightForInsights(
                summary = testSummary(
                    nightId = i.toLong(),
                    nightDate = LocalDate.of(2026, 9, 1).plusDays(i.toLong()),
                    latencyMinutes = if (tagged) latencyWith else 10,
                    heatUsed = heatOnAll,
                ),
                tags = if (tagged) listOf(caffeine) else emptyList(),
            )
        }

    @Test
    fun `finds an association with a tag`() {
        val insights = InsightEngine.find(nights(with = 7, without = 13, latencyWith = 30), goal)

        val insight = insights.single()
        assertThat(insight.factor).isEqualTo(InsightFactor.WithTag(caffeine))
        assertThat(insight.metric).isEqualTo(InsightMetric.SLEEP_LATENCY_MINUTES)
        assertThat(insight.withValue).isEqualTo(30.0)
        assertThat(insight.withoutValue).isEqualTo(10.0)
        assertThat(insight.nightsWith).isEqualTo(7)
        assertThat(insight.nightsWithout).isEqualTo(13)
        assertThat(insight.better).isFalse()
    }

    @Test
    fun `needs fourteen usable nights in total`() {
        val thirteen = nights(with = 6, without = 7, latencyWith = 30)
        assertThat(InsightEngine.hasEnoughData(thirteen)).isFalse()
        assertThat(InsightEngine.find(thirteen, goal)).isEmpty()

        val fourteen = nights(with = 6, without = 8, latencyWith = 30)
        assertThat(InsightEngine.hasEnoughData(fourteen)).isTrue()
        assertThat(InsightEngine.find(fourteen, goal)).hasSize(1)
    }

    @Test
    fun `nights without sleep duration do not count`() {
        val list = nights(with = 6, without = 8, latencyWith = 30).toMutableList()
        list[13] = list[13].copy(summary = list[13].summary.copy(totalSleep = null))
        assertThat(InsightEngine.hasEnoughData(list)).isFalse()
    }

    @Test
    fun `needs five nights on each side`() {
        assertThat(InsightEngine.find(nights(with = 4, without = 16, latencyWith = 30), goal)).isEmpty()
        assertThat(InsightEngine.find(nights(with = 5, without = 15, latencyWith = 30), goal)).hasSize(1)
        // Wärme in allen Nächten: keine Vergleichsgruppe, also kein Hinweis zur Wärme.
        val all = InsightEngine.find(nights(with = 7, without = 13, latencyWith = 30, heatOnAll = true), goal)
        assertThat(all.map { it.factor }).doesNotContain(InsightFactor.Heat)
    }

    @Test
    fun `small differences are not reported`() {
        assertThat(InsightEngine.find(nights(with = 7, without = 13, latencyWith = 13), goal)).isEmpty()
        assertThat(InsightEngine.find(nights(with = 7, without = 13, latencyWith = 14), goal)).hasSize(1)
    }
}
