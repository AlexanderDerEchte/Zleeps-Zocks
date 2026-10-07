package at.zocks.zleep.domain.analysis

import at.zocks.zleep.domain.model.NightSummary
import at.zocks.zleep.domain.model.Tag
import java.time.Duration
import kotlin.math.abs

/** Wonach Nächte verglichen werden. */
sealed interface InsightFactor {
    data object Heat : InsightFactor
    data object Massage : InsightFactor
    data class WithTag(val tag: Tag) : InsightFactor
}

enum class InsightMetric(
    /** Ab diesem Unterschied der Mittelwerte lohnt sich ein Hinweis. */
    val minDifference: Double,
    /** Wird ein höherer Wert als besser empfunden? */
    val higherIsBetter: Boolean,
) {
    SCORE(4.0, true),
    SLEEP_LATENCY_MINUTES(4.0, false),
    TOTAL_SLEEP_MINUTES(15.0, true),
    RESTORATIVE_PERCENT(3.0, true),
}

/**
 * Ein beobachteter Zusammenhang: In Nächten mit [factor] lag [metric] im Mittel bei
 * [withValue], ohne bei [withoutValue]. Kein Beleg für eine Ursache.
 */
data class Insight(
    val factor: InsightFactor,
    val metric: InsightMetric,
    val withValue: Double,
    val withoutValue: Double,
    val nightsWith: Int,
    val nightsWithout: Int,
) {
    val difference: Double get() = withValue - withoutValue

    /** Ob die Nächte mit dem Faktor in dieser Kennzahl besser waren. */
    val better: Boolean get() = if (metric.higherIsBetter) difference > 0 else difference < 0
}

/** Eine Nacht samt ihren Tags, so wie die Erkenntnisse sie brauchen. */
data class NightForInsights(val summary: NightSummary, val tags: List<Tag>)

/**
 * Vergleicht Nächte mit und ohne einen Faktor (Wärme, Massage, Tags). Ein Hinweis erscheint
 * nur, wenn genug Nächte vorliegen ([MIN_NIGHTS_TOTAL] insgesamt, je Gruppe mindestens
 * [MIN_NIGHTS_PER_GROUP]) und der Unterschied die Schwelle der Kennzahl übersteigt.
 * Je Faktor wird nur die Kennzahl mit dem deutlichsten Unterschied gezeigt.
 */
object InsightEngine {

    const val MIN_NIGHTS_TOTAL = 14
    const val MIN_NIGHTS_PER_GROUP = 5
    const val MAX_INSIGHTS = 4

    fun hasEnoughData(nights: List<NightForInsights>): Boolean = nights.count { it.summary.totalSleep != null } >= MIN_NIGHTS_TOTAL

    fun find(nights: List<NightForInsights>, goal: Duration): List<Insight> {
        val usable = nights.filter { it.summary.totalSleep != null }
        if (usable.size < MIN_NIGHTS_TOTAL) return emptyList()

        val tags = usable.flatMap { it.tags }.distinctBy { it.id }
        val factors = listOf(InsightFactor.Heat, InsightFactor.Massage) + tags.map { InsightFactor.WithTag(it) }

        return factors.mapNotNull { factor ->
            val (with, without) = usable.partition { it.has(factor) }
            if (with.size < MIN_NIGHTS_PER_GROUP || without.size < MIN_NIGHTS_PER_GROUP) return@mapNotNull null
            InsightMetric.entries
                .mapNotNull { metric -> compare(factor, metric, with, without, goal) }
                .filter { abs(it.difference) >= it.metric.minDifference }
                .maxByOrNull { abs(it.difference) / it.metric.minDifference }
        }
            .sortedByDescending { abs(it.difference) / it.metric.minDifference }
            .take(MAX_INSIGHTS)
    }

    private fun NightForInsights.has(factor: InsightFactor): Boolean = when (factor) {
        InsightFactor.Heat -> summary.heatUsed
        InsightFactor.Massage -> summary.massageUsed
        is InsightFactor.WithTag -> tags.any { it.id == factor.tag.id }
    }

    private fun compare(
        factor: InsightFactor,
        metric: InsightMetric,
        with: List<NightForInsights>,
        without: List<NightForInsights>,
        goal: Duration,
    ): Insight? {
        val a = with.mapNotNull { metric.valueOf(it.summary, goal) }
        val b = without.mapNotNull { metric.valueOf(it.summary, goal) }
        if (a.size < MIN_NIGHTS_PER_GROUP || b.size < MIN_NIGHTS_PER_GROUP) return null
        return Insight(factor, metric, a.average(), b.average(), a.size, b.size)
    }

    private fun InsightMetric.valueOf(summary: NightSummary, goal: Duration): Double? = when (this) {
        InsightMetric.SCORE -> SleepScoreCalculator.calculate(summary, goal)?.value?.toDouble()
        InsightMetric.SLEEP_LATENCY_MINUTES -> summary.sleepLatency?.toMinutes()?.toDouble()
        InsightMetric.TOTAL_SLEEP_MINUTES -> summary.totalSleep?.toMinutes()?.toDouble()
        InsightMetric.RESTORATIVE_PERCENT -> summary.restorativeShare?.times(100)
    }
}
