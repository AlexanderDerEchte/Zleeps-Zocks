package at.zocks.zleep.domain.analysis

import at.zocks.zleep.domain.model.EPOCH_LENGTH
import at.zocks.zleep.domain.model.EpochMeasurement
import at.zocks.zleep.domain.model.NightData
import at.zocks.zleep.domain.model.NightEventType
import at.zocks.zleep.domain.model.SleepStage
import java.time.Duration
import java.time.Instant

/** Ein Wert im Verlauf; `null` = keine Messung (die Linie wird dort unterbrochen). */
data class TimelinePoint(val time: Instant, val value: Double?)

data class TimelineSpan(val start: Instant, val end: Instant)

/** Aufbereitete Verläufe einer Nacht für die Diagramme, alle auf derselben Zeitachse. */
data class NightTimeline(
    val start: Instant,
    val end: Instant,
    val stages: List<Pair<TimelineSpan, SleepStage>>,
    val heartRate: List<TimelinePoint>,
    val hrv: List<TimelinePoint>,
    val skinTemperature: List<TimelinePoint>,
    val heat: List<TimelineSpan>,
    val massage: List<TimelineSpan>,
    val gaps: List<TimelineSpan>,
) {
    val hasVitals: Boolean
        get() = listOf(heartRate, hrv, skinTemperature).any { series -> series.any { it.value != null } }

    /** Werte zum Zeitpunkt [time] (für die Ablese-Linie und die Tabellenansicht). */
    fun valuesAt(time: Instant): TimelineReading = TimelineReading(
        time = time,
        stage = stages.firstOrNull { (span, _) -> !time.isBefore(span.start) && time.isBefore(span.end) }?.second,
        heartRate = heartRate.valueAt(time),
        hrv = hrv.valueAt(time),
        skinTemperature = skinTemperature.valueAt(time),
    )

    companion object {
        /** Auflösung der Linien: 2 Minuten (≈ 240 Punkte pro Nacht). */
        val BUCKET: Duration = Duration.ofMinutes(2)

        fun from(data: NightData, now: Instant): NightTimeline {
            val night = data.night
            val end = night.end ?: now

            // Aufeinanderfolgende gleiche Phasen zu Abschnitten zusammenfassen.
            val stageSpans = mutableListOf<Pair<TimelineSpan, SleepStage>>()
            data.stages.sortedBy { it.start }.forEach { epoch ->
                val epochEnd = epoch.start.plus(EPOCH_LENGTH)
                val last = stageSpans.lastOrNull()
                if (last != null && last.second == epoch.stage && last.first.end == epoch.start) {
                    stageSpans[stageSpans.lastIndex] = TimelineSpan(last.first.start, epochEnd) to epoch.stage
                } else {
                    stageSpans += TimelineSpan(epoch.start, epochEnd) to epoch.stage
                }
            }

            fun series(selector: (EpochMeasurement) -> Double?): List<TimelinePoint> {
                val buckets = data.measurements.groupBy { bucketStart(it.start, night.start) }
                val result = mutableListOf<TimelinePoint>()
                var t = night.start
                while (t.isBefore(end)) {
                    val values = buckets[t].orEmpty().mapNotNull(selector)
                    result += TimelinePoint(t.plus(BUCKET.dividedBy(2)), values.takeIf { it.isNotEmpty() }?.average())
                    t = t.plus(BUCKET)
                }
                return result
            }

            fun spans(type: NightEventType) = data.events.filter { it.type == type }
                .map { TimelineSpan(it.start, it.end ?: end) }

            return NightTimeline(
                start = night.start,
                end = end,
                stages = stageSpans,
                heartRate = series { it.heartRateBpm },
                hrv = series { it.hrvRmssdMs },
                skinTemperature = series { it.skinTemperatureC },
                heat = spans(NightEventType.HEAT),
                massage = spans(NightEventType.MASSAGE),
                gaps = data.gaps.map { TimelineSpan(it.start, it.end ?: end) },
            )
        }

        private fun bucketStart(time: Instant, origin: Instant): Instant {
            val index = Duration.between(origin, time).toMillis().coerceAtLeast(0) / BUCKET.toMillis()
            return origin.plus(BUCKET.multipliedBy(index))
        }
    }
}

/** Wert zum Zeitpunkt [time]: der nächste Punkt, höchstens einen Abschnitt entfernt. */
fun List<TimelinePoint>.valueAt(time: Instant): Double? =
    minByOrNull { Duration.between(it.time, time).abs() }
        ?.takeIf { Duration.between(it.time, time).abs() <= NightTimeline.BUCKET }
        ?.value

data class TimelineReading(
    val time: Instant,
    val stage: SleepStage?,
    val heartRate: Double?,
    val hrv: Double?,
    val skinTemperature: Double?,
)
