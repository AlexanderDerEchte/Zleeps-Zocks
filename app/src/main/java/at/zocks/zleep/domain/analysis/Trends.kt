package at.zocks.zleep.domain.analysis

import at.zocks.zleep.domain.model.NightSummary
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class TrendPeriod { WEEK, MONTH, YEAR }

/** Ein Balken im Trend: eine Nacht (Woche/Monat) oder ein Monatsmittel (Jahr). */
data class TrendPoint(
    val date: LocalDate,
    val score: Int?,
    val totalSleepMinutes: Int?,
    val restingHeartRate: Double?,
    val nights: Int,
)

/** Wie gleichmäßig die Schlafenszeiten sind (Standardabweichung in Minuten). */
data class Regularity(
    val averageSleepOnset: LocalTime,
    val averageWake: LocalTime,
    val sleepOnsetSpreadMinutes: Int,
    val wakeSpreadMinutes: Int,
) {
    val rating: RegularityRating
        get() = when (maxOf(sleepOnsetSpreadMinutes, wakeSpreadMinutes)) {
            in 0..30 -> RegularityRating.VERY_REGULAR
            in 31..60 -> RegularityRating.FAIRLY_REGULAR
            else -> RegularityRating.IRREGULAR
        }
}

enum class RegularityRating { VERY_REGULAR, FAIRLY_REGULAR, IRREGULAR }

data class TrendSummary(
    val period: TrendPeriod,
    val from: LocalDate,
    val to: LocalDate,
    val points: List<TrendPoint>,
    val nights: Int,
    val averageScore: Int?,
    val averageSleepMinutes: Int?,
    val averageRestingHeartRate: Double?,
    val averageEfficiency: Double?,
    val regularity: Regularity?,
)

/**
 * Trends über Woche (7 Nächte), Monat (30 Nächte) und Jahr (12 Monatsmittel).
 * Fehlende Nächte bleiben als leere Balken stehen – nichts wird aufgefüllt.
 */
object TrendCalculator {

    /** Für die Regelmäßigkeit braucht es mindestens so viele Nächte. */
    const val MIN_NIGHTS_FOR_REGULARITY = 5

    fun calculate(
        summaries: List<NightSummary>,
        period: TrendPeriod,
        today: LocalDate,
        goal: Duration,
        zone: ZoneId,
    ): TrendSummary {
        // „Heute“ ist frühestens die Nacht von gestern (eine Nacht zählt zum Tag ihres Beginns).
        val last = today.minusDays(1)
        val from = when (period) {
            TrendPeriod.WEEK -> last.minusDays(6)
            TrendPeriod.MONTH -> last.minusDays(29)
            TrendPeriod.YEAR -> YearMonth.from(last).minusMonths(11).atDay(1)
        }
        val inRange = summaries.filter { !it.nightDate.isBefore(from) && !it.nightDate.isAfter(last) }
        val byDate = inRange.groupBy { it.nightDate }

        val points = when (period) {
            TrendPeriod.WEEK, TrendPeriod.MONTH -> generateSequence(from) { it.plusDays(1) }
                .takeWhile { !it.isAfter(last) }
                .map { date -> point(date, byDate[date].orEmpty(), goal) }
                .toList()
            TrendPeriod.YEAR -> (0..11).map { offset ->
                val month = YearMonth.from(from).plusMonths(offset.toLong())
                point(month.atDay(1), inRange.filter { YearMonth.from(it.nightDate) == month }, goal)
            }
        }

        val scores = inRange.mapNotNull { SleepScoreCalculator.calculate(it, goal)?.value }
        return TrendSummary(
            period = period,
            from = from,
            to = last,
            points = points,
            nights = inRange.size,
            averageScore = scores.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            averageSleepMinutes = inRange.mapNotNull { it.totalSleep?.toMinutes() }.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            averageRestingHeartRate = inRange.mapNotNull { it.restingHeartRateBpm }.takeIf { it.isNotEmpty() }?.average(),
            averageEfficiency = inRange.mapNotNull { it.efficiency }.takeIf { it.isNotEmpty() }?.average(),
            regularity = regularity(inRange, zone),
        )
    }

    private fun point(date: LocalDate, nights: List<NightSummary>, goal: Duration) = TrendPoint(
        date = date,
        score = nights.mapNotNull { SleepScoreCalculator.calculate(it, goal)?.value }.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
        totalSleepMinutes = nights.mapNotNull { it.totalSleep?.toMinutes() }.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
        restingHeartRate = nights.mapNotNull { it.restingHeartRateBpm }.takeIf { it.isNotEmpty() }?.average(),
        nights = nights.size,
    )

    /**
     * Uhrzeiten werden für die Statistik um 12 Stunden verschoben, damit 23:30 und 00:30
     * nebeneinander liegen (statt an entgegengesetzten Enden des Tages).
     */
    internal fun regularity(nights: List<NightSummary>, zone: ZoneId): Regularity? {
        val onsets = nights.mapNotNull { it.sleepOnset?.atZone(zone)?.toLocalTime() }
        val wakes = nights.mapNotNull { it.finalWake?.atZone(zone)?.toLocalTime() }
        if (onsets.size < MIN_NIGHTS_FOR_REGULARITY || wakes.size < MIN_NIGHTS_FOR_REGULARITY) return null
        val shiftedOnsets = onsets.map { (it.toSecondOfDay() / 60 + 12 * 60) % MINUTES_PER_DAY }
        val wakeMinutes = wakes.map { it.toSecondOfDay() / 60 }
        return Regularity(
            averageSleepOnset = LocalTime.ofSecondOfDay(((shiftedOnsets.average().roundToInt() - 12 * 60 + MINUTES_PER_DAY) % MINUTES_PER_DAY) * 60L),
            averageWake = LocalTime.ofSecondOfDay((wakeMinutes.average().roundToInt() % MINUTES_PER_DAY) * 60L),
            sleepOnsetSpreadMinutes = standardDeviation(shiftedOnsets).roundToInt(),
            wakeSpreadMinutes = standardDeviation(wakeMinutes).roundToInt(),
        )
    }

    private fun standardDeviation(values: List<Int>): Double {
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }

    private const val MINUTES_PER_DAY = 24 * 60
}
