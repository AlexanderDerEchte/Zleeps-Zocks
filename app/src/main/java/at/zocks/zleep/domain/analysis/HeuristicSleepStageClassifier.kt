package at.zocks.zleep.domain.analysis

import at.zocks.zleep.domain.model.EPOCH_LENGTH
import at.zocks.zleep.domain.model.EpochMeasurement
import at.zocks.zleep.domain.model.SleepStage
import at.zocks.zleep.domain.model.StageEpoch
import java.time.Instant
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Regelbasierte Schätzung von Wach / Leicht / Tief / REM aus Bewegung, Puls und HRV.
 * Keine medizinische Schlafphasen-Bestimmung (dafür bräuchte es EEG).
 *
 * ## Ablauf
 * 1. **Zusammenführen:** Je 30-s-Epoche werden beide Socken kombiniert – Puls und RMSSD
 *    gemittelt, Bewegung als Maximum (bewegt sich ein Fuß, bewegt sich der Mensch).
 *    Epochen ohne Puls und ohne Bewegung (Lücke) bekommen keine Phase.
 * 2. **Wach/Schlaf (Aktigraphie):** gewichtete Bewegungssumme über ±2 Epochen
 *    (ähnlich Cole-Kripke). Über [WAKE_ACTIVITY] → wach. Zuckungen werden geglättet:
 *    Wach-Abschnitte bis [MAX_TWITCH_EPOCHS] Epochen mitten im Schlaf mit mäßiger Aktivität
 *    (unter [STRONG_ACTIVITY]) zählen als Schlaf, Schlaf-Abschnitte unter [MIN_SLEEP_BOUT]
 *    Epochen zwischen Wachphasen als wach.
 * 3. **Bezugswerte der Nacht:** Median und robuste Streuung (IQR / 1,35) von geglättetem
 *    Puls (Median über 5 Epochen), geglätteter RMSSD und Pulsvariabilität (Standardabweichung
 *    über 9 Epochen) – nur aus Schlafepochen. So zählt der eigene Verlauf, keine Normwerte.
 * 4. **Tief:** sehr ruhig (Aktivität < [DEEP_MAX_ACTIVITY]), Puls deutlich unter dem
 *    Nachtmedian (z ≤ [DEEP_HR_Z]) und HRV über dem Median (z ≥ [DEEP_RMSSD_Z]).
 * 5. **REM:** ruhig (Aktivität < [REM_MAX_ACTIVITY]), mindestens [REM_LATENCY_MINUTES] nach dem
 *    Einschlafen, Puls über dem Median (z ≥ [REM_HR_Z]) und dazu unruhiger Puls
 *    (z ≥ [REM_VAR_Z]) oder niedrige HRV (z ≤ [REM_RMSSD_Z]).
 * 6. **Sonst leicht.** Ohne Pulsdaten gibt es nur wach/leicht.
 * 7. **Glätten:** Modusfilter über 5 Epochen innerhalb des Schlafs, Tief- und REM-Abschnitte
 *    unter [MIN_STAGE_BOUT] Epochen werden zu leicht.
 *
 * Die Schwellen sind gegen den Simulator abgestimmt (siehe Tests) und müssen mit echten
 * Sockendaten neu kalibriert werden.
 */
class HeuristicSleepStageClassifier : SleepStageClassifier {

    private class Features(
        val start: Instant,
        val heartRate: Double?,
        val rmssd: Double?,
        val motion: Double?,
    )

    override fun classify(measurements: List<EpochMeasurement>): List<StageEpoch> {
        val epochs = merge(measurements)
        if (epochs.isEmpty()) return emptyList()

        val activity = activity(epochs)
        val asleep = rescoreWake(BooleanArray(epochs.size) { activity[it] <= WAKE_ACTIVITY }, activity)

        val hrSmooth = rollingMedian(epochs.map { it.heartRate }, 5)
        val rmSmooth = rollingMedian(epochs.map { it.rmssd }, 5)
        val hrVar = rollingSd(epochs.map { it.heartRate }, 9)
        val sleepIdx = epochs.indices.filter { asleep[it] }
        val hrRef = Reference.of(sleepIdx.mapNotNull { hrSmooth[it] })
        val rmRef = Reference.of(sleepIdx.mapNotNull { rmSmooth[it] })
        val varRef = Reference.of(sleepIdx.mapNotNull { hrVar[it] })

        val onset = sleepIdx.firstOrNull()
        val stages = Array(epochs.size) { i ->
            when {
                !asleep[i] -> SleepStage.AWAKE
                hrSmooth[i] == null || hrRef == null -> SleepStage.LIGHT
                else -> {
                    val hrZ = hrRef.z(hrSmooth[i]!!)
                    val rmZ = rmSmooth[i]?.let { rmRef?.z(it) } ?: 0.0
                    val varZ = hrVar[i]?.let { varRef?.z(it) } ?: 0.0
                    val minutesAsleep = if (onset == null) 0.0 else (i - onset) * EPOCH_MINUTES
                    when {
                        activity[i] < DEEP_MAX_ACTIVITY && hrZ <= DEEP_HR_Z && rmZ >= DEEP_RMSSD_Z -> SleepStage.DEEP
                        activity[i] < REM_MAX_ACTIVITY && minutesAsleep >= REM_LATENCY_MINUTES &&
                            hrZ >= REM_HR_Z && (varZ >= REM_VAR_Z || rmZ <= REM_RMSSD_Z) -> SleepStage.REM
                        else -> SleepStage.LIGHT
                    }
                }
            }
        }
        smooth(stages)
        return epochs.indices.map { StageEpoch(epochs[it].start, stages[it]) }
    }

    private fun merge(measurements: List<EpochMeasurement>): List<Features> =
        measurements.groupBy { it.start }
            .toSortedMap()
            .map { (start, sides) ->
                Features(
                    start = start,
                    heartRate = sides.mapNotNull { it.heartRateBpm }.averageOrNull(),
                    rmssd = sides.mapNotNull { it.hrvRmssdMs }.averageOrNull(),
                    motion = sides.mapNotNull { it.motion }.maxOrNull(),
                )
            }
            .filter { it.heartRate != null || it.motion != null }

    /** Gewichtete Bewegung über Nachbarepochen; fehlende Bewegung zählt als 0. */
    private fun activity(epochs: List<Features>): DoubleArray = DoubleArray(epochs.size) { i ->
        ACTIVITY_WEIGHTS.withIndex().sumOf { (k, weight) ->
            val j = i + k - 2
            if (j in epochs.indices) weight * (epochs[j].motion ?: 0.0) else 0.0
        }
    }

    private fun rescoreWake(asleep: BooleanArray, activity: DoubleArray): BooleanArray {
        val result = asleep.copyOf()
        // Kurze, mäßige Bewegung mitten im Schlaf ist meist ein Zucken, kein Aufwachen.
        var w = 0
        while (w < result.size) {
            if (!result[w]) {
                var end = w
                while (end + 1 < result.size && !result[end + 1]) end++
                val inSleep = w > 0 && end < result.lastIndex && result[w - 1] && result[end + 1]
                if (inSleep && end - w + 1 <= MAX_TWITCH_EPOCHS && (w..end).all { activity[it] < STRONG_ACTIVITY }) {
                    for (k in w..end) result[k] = true
                }
                w = end + 1
            } else {
                w++
            }
        }
        // Sehr kurze Schlafstücke zwischen Wachphasen sind meist ruhiges Wachliegen.
        var i = 0
        while (i < result.size) {
            if (result[i]) {
                var end = i
                while (end + 1 < result.size && result[end + 1]) end++
                val length = end - i + 1
                val betweenWake = (i == 0 || !result[i - 1]) && (end == result.lastIndex || !result[end + 1])
                if (length < MIN_SLEEP_BOUT && betweenWake && i > 0 && end < result.lastIndex) {
                    for (k in i..end) result[k] = false
                }
                i = end + 1
            } else {
                i++
            }
        }
        return result
    }

    private fun smooth(stages: Array<SleepStage>) {
        val original = stages.copyOf()
        for (i in stages.indices) {
            if (original[i] == SleepStage.AWAKE) continue
            val window = (max(0, i - 2)..minOf(stages.lastIndex, i + 2)).map { original[it] }.filter { it != SleepStage.AWAKE }
            stages[i] = window.groupingBy { it }.eachCount().maxWith(compareBy<Map.Entry<SleepStage, Int>> { it.value }
                .thenBy { if (it.key == original[i]) 1 else 0 }).key
        }
        // Kurze Tief-/REM-Abschnitte zu leicht.
        var i = 0
        while (i < stages.size) {
            val stage = stages[i]
            var end = i
            while (end + 1 < stages.size && stages[end + 1] == stage) end++
            if ((stage == SleepStage.DEEP || stage == SleepStage.REM) && end - i + 1 < MIN_STAGE_BOUT) {
                for (k in i..end) stages[k] = SleepStage.LIGHT
            }
            i = end + 1
        }
    }

    private class Reference(val median: Double, val spread: Double) {
        fun z(value: Double) = (value - median) / spread

        companion object {
            fun of(values: List<Double>): Reference? {
                if (values.size < MIN_REFERENCE_EPOCHS) return null
                val sorted = values.sorted()
                val iqr = quantile(sorted, 0.75) - quantile(sorted, 0.25)
                return Reference(quantile(sorted, 0.5), max(iqr / 1.35, MIN_SPREAD))
            }
        }
    }

    companion object {
        private val EPOCH_MINUTES = EPOCH_LENGTH.seconds / 60.0

        /** Gewichte für Epochen i−2 … i+2. */
        private val ACTIVITY_WEIGHTS = listOf(0.1, 0.2, 0.4, 0.2, 0.1)

        const val WAKE_ACTIVITY = 0.09
        const val STRONG_ACTIVITY = 0.15
        const val MAX_TWITCH_EPOCHS = 2
        const val MIN_SLEEP_BOUT = 6
        const val DEEP_MAX_ACTIVITY = 0.02
        const val DEEP_HR_Z = -0.6
        const val DEEP_RMSSD_Z = 0.3
        const val REM_MAX_ACTIVITY = 0.03
        const val REM_LATENCY_MINUTES = 45.0
        const val REM_HR_Z = 0.3
        const val REM_VAR_Z = 0.2
        const val REM_RMSSD_Z = -0.3
        const val MIN_STAGE_BOUT = 4
        private const val MIN_REFERENCE_EPOCHS = 20
        private const val MIN_SPREAD = 0.5

        internal fun quantile(sorted: List<Double>, q: Double): Double {
            val pos = q * (sorted.size - 1)
            val lower = sorted[pos.toInt()]
            val upper = sorted[minOf(pos.toInt() + 1, sorted.lastIndex)]
            return lower + (upper - lower) * (pos - pos.toInt())
        }

        internal fun rollingMedian(values: List<Double?>, window: Int): List<Double?> = values.indices.map { i ->
            if (values[i] == null) return@map null
            val half = window / 2
            val slice = (max(0, i - half)..minOf(values.lastIndex, i + half)).mapNotNull { values[it] }.sorted()
            quantile(slice, 0.5)
        }

        internal fun rollingSd(values: List<Double?>, window: Int): List<Double?> = values.indices.map { i ->
            if (values[i] == null) return@map null
            val half = window / 2
            val slice = (max(0, i - half)..minOf(values.lastIndex, i + half)).mapNotNull { values[it] }
            if (slice.size < 3) return@map null
            val mean = slice.average()
            sqrt(slice.sumOf { (it - mean) * (it - mean) } / (slice.size - 1))
        }

        private fun List<Double>.averageOrNull() = if (isEmpty()) null else average()
    }
}
