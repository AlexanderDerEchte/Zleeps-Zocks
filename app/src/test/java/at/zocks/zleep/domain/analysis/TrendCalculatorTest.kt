package at.zocks.zleep.domain.analysis

import at.zocks.zleep.testing.testSummary
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class TrendCalculatorTest {

    private val zone = ZoneOffset.UTC
    private val goal = Duration.ofHours(8)
    private val today = LocalDate.of(2026, 10, 8)

    private fun day(dayOfMonth: Int) = LocalDate.of(2026, 10, dayOfMonth)

    @Test
    fun `week shows the last seven nights and leaves missing nights empty`() {
        val summaries = listOf(
            testSummary(nightId = 1, nightDate = day(2)),
            testSummary(nightId = 2, nightDate = day(5), totalSleepMinutes = 360),
            testSummary(nightId = 3, nightDate = day(8)), // heute Nacht – noch nicht vorbei
            testSummary(nightId = 4, nightDate = LocalDate.of(2026, 9, 30)), // zu alt
        )
        val trend = TrendCalculator.calculate(summaries, TrendPeriod.WEEK, today, goal, zone)

        assertThat(trend.from).isEqualTo(day(1))
        assertThat(trend.to).isEqualTo(day(7))
        assertThat(trend.points.map { it.date }).containsExactlyElementsIn((1..7).map(::day)).inOrder()
        assertThat(trend.points.count { it.score != null }).isEqualTo(2)
        assertThat(trend.points.single { it.date == day(3) }.totalSleepMinutes).isNull()
        assertThat(trend.nights).isEqualTo(2)
        assertThat(trend.averageSleepMinutes).isEqualTo(420)
        // 6 h von 8 h Ziel: 18 von 35 Punkten für die Dauer, also 83; Mittel aus 100 und 83.
        assertThat(trend.averageScore).isEqualTo(92)
        assertThat(trend.regularity).isNull()
    }

    @Test
    fun `month covers thirty nights`() {
        val trend = TrendCalculator.calculate(emptyList(), TrendPeriod.MONTH, today, goal, zone)
        assertThat(trend.points).hasSize(30)
        assertThat(trend.averageScore).isNull()
        assertThat(trend.nights).isEqualTo(0)
    }

    @Test
    fun `year averages per month`() {
        val summaries = listOf(
            testSummary(nightId = 1, nightDate = day(1), totalSleepMinutes = 400),
            testSummary(nightId = 2, nightDate = day(2), totalSleepMinutes = 500),
            testSummary(nightId = 3, nightDate = LocalDate.of(2025, 11, 3), totalSleepMinutes = 450),
            testSummary(nightId = 4, nightDate = LocalDate.of(2025, 10, 31)), // außerhalb
        )
        val trend = TrendCalculator.calculate(summaries, TrendPeriod.YEAR, today, goal, zone)

        assertThat(trend.points).hasSize(12)
        assertThat(trend.points.first().date).isEqualTo(LocalDate.of(2025, 11, 1))
        assertThat(trend.points.first().totalSleepMinutes).isEqualTo(450)
        assertThat(trend.points.last().date).isEqualTo(day(1))
        assertThat(trend.points.last().totalSleepMinutes).isEqualTo(450)
        assertThat(trend.points.last().nights).isEqualTo(2)
        assertThat(trend.nights).isEqualTo(3)
    }

    @Test
    fun `regularity handles bedtimes around midnight`() {
        val onsets = listOf("23:30", "00:30", "23:45", "00:15", "00:00")
        val wakes = listOf("06:30", "07:30", "07:00", "07:00", "07:00")
        val summaries = onsets.indices.map { i ->
            val date = day(1 + i)
            val onsetDate = if (onsets[i].startsWith("23")) date else date.plusDays(1)
            testSummary(
                nightId = i.toLong(),
                nightDate = date,
                sleepOnset = onsetDate.atTime(LocalTime.parse(onsets[i])).toInstant(zone),
                finalWake = date.plusDays(1).atTime(LocalTime.parse(wakes[i])).toInstant(zone),
            )
        }
        val regularity = TrendCalculator.regularity(summaries, zone)!!

        assertThat(regularity.averageSleepOnset).isEqualTo(LocalTime.MIDNIGHT)
        assertThat(regularity.averageWake).isEqualTo(LocalTime.of(7, 0))
        assertThat(regularity.sleepOnsetSpreadMinutes).isEqualTo(21)
        assertThat(regularity.wakeSpreadMinutes).isEqualTo(19)
        assertThat(regularity.rating).isEqualTo(RegularityRating.VERY_REGULAR)

        assertThat(TrendCalculator.regularity(summaries.take(4), zone)).isNull()
    }

    @Test
    fun `regularity rating thresholds`() {
        fun rating(spread: Int) = Regularity(LocalTime.MIDNIGHT, LocalTime.NOON, spread, 0).rating
        assertThat(rating(30)).isEqualTo(RegularityRating.VERY_REGULAR)
        assertThat(rating(31)).isEqualTo(RegularityRating.FAIRLY_REGULAR)
        assertThat(rating(60)).isEqualTo(RegularityRating.FAIRLY_REGULAR)
        assertThat(rating(61)).isEqualTo(RegularityRating.IRREGULAR)
    }
}
