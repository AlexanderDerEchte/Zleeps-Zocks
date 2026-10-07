package at.zocks.zleep.domain.analysis

import at.zocks.zleep.domain.model.NightSummary
import java.time.Duration
import kotlin.math.roundToInt

/** Bestandteile des Schlafscores mit ihrem Höchstwert. Summe = 100. */
enum class ScoreComponent(val maxPoints: Int) {
    /** Schlafdauer im Verhältnis zum persönlichen Ziel. */
    DURATION(35),

    /** Anteil geschlafener Zeit an der Zeit im Bett. */
    EFFICIENCY(25),

    /** Anteil Tief- und REM-Schlaf. */
    RESTORATIVE(20),

    /** Wie schnell du eingeschlafen bist. */
    LATENCY(10),

    /** Wie ungestört der Schlaf war (Wachzeit nach dem Einschlafen). */
    CONTINUITY(10),
}

data class ScorePart(
    val component: ScoreComponent,
    /** `null`, wenn die Messwerte für diesen Teil fehlen. */
    val points: Int?,
) {
    val maxPoints: Int get() = component.maxPoints
}

data class SleepScore(val value: Int, val parts: List<ScorePart>) {
    /** Der Teil mit dem größten Abstand zum Höchstwert – der beste Hebel. */
    val weakestPart: ScorePart?
        get() = parts.filter { it.points != null }.maxByOrNull { (it.maxPoints - it.points!!).toDouble() / it.maxPoints }
}

/**
 * Schlafscore 0–100, nachvollziehbar aus fünf Teilen. Jeder Teil steigt zwischen einer
 * unteren und einer oberen Schwelle linear an:
 *
 * | Teil | 0 Punkte | volle Punkte |
 * |---|---|---|
 * | Dauer (35) | ≤ 50 % des Ziels | ≥ 100 % des Ziels |
 * | Effizienz (25) | ≤ 65 % | ≥ 90 % |
 * | Tief + REM (20) | ≤ 15 % des Schlafs | ≥ 40 % |
 * | Einschlafen (10) | ≥ 60 min | ≤ 15 min |
 * | Durchschlafen (10) | ≥ 60 min wach | ≤ 10 min wach |
 *
 * Fehlen Werte für einen Teil, zählt er nicht; der Score wird auf die übrigen Teile
 * hochgerechnet. Ohne Schlafdauer gibt es keinen Score.
 */
object SleepScoreCalculator {

    fun calculate(summary: NightSummary, goal: Duration): SleepScore? {
        val totalSleep = summary.totalSleep ?: return null
        val parts = listOf(
            ScorePart(ScoreComponent.DURATION, points(ScoreComponent.DURATION, ramp(totalSleep.toMinutes().toDouble() / goal.toMinutes(), 0.5, 1.0))),
            ScorePart(ScoreComponent.EFFICIENCY, summary.efficiency?.let { points(ScoreComponent.EFFICIENCY, ramp(it, 0.65, 0.90)) }),
            ScorePart(ScoreComponent.RESTORATIVE, summary.restorativeShare?.let { points(ScoreComponent.RESTORATIVE, ramp(it, 0.15, 0.40)) }),
            ScorePart(
                ScoreComponent.LATENCY,
                summary.sleepLatency?.let { points(ScoreComponent.LATENCY, 1 - ramp(it.toMinutes().toDouble(), 15.0, 60.0)) },
            ),
            ScorePart(
                ScoreComponent.CONTINUITY,
                summary.wakeAfterOnset?.let { points(ScoreComponent.CONTINUITY, 1 - ramp(it.toMinutes().toDouble(), 10.0, 60.0)) },
            ),
        )
        val available = parts.filter { it.points != null }
        val value = (available.sumOf { it.points!! } * 100.0 / available.sumOf { it.maxPoints }).roundToInt()
        return SleepScore(value.coerceIn(0, 100), parts)
    }

    /** 0 bei [low], 1 bei [high], dazwischen linear. */
    internal fun ramp(value: Double, low: Double, high: Double): Double = ((value - low) / (high - low)).coerceIn(0.0, 1.0)

    private fun points(component: ScoreComponent, fraction: Double) = (fraction * component.maxPoints).roundToInt()
}
