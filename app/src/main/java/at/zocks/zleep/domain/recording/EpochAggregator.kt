package at.zocks.zleep.domain.recording

import at.zocks.zleep.domain.model.EPOCH_LENGTH
import at.zocks.zleep.domain.model.EpochMeasurement
import at.zocks.zleep.domain.model.SensorSample
import at.zocks.zleep.domain.model.SockSide
import java.time.Instant

/**
 * Verdichtet Messpunkte je Socke zu 30-s-Epochen (Mittelwerte). Fehlende Werte bleiben
 * fehlend: Eine Epoche ohne Pulswert hat `heartRateBpm = null`, es wird nichts geschätzt.
 * Eine Epoche ist fertig, sobald ein Messpunkt einer späteren Epoche derselben Socke kommt.
 */
class EpochAggregator {

    private class Bucket(val start: Instant) {
        val heartRate = mutableListOf<Double>()
        val rmssd = mutableListOf<Double>()
        val spo2 = mutableListOf<Double>()
        val skin = mutableListOf<Double>()
        val motion = mutableListOf<Double>()
        var count = 0
    }

    private val open = mutableMapOf<SockSide, Bucket>()

    /** Nimmt einen Messpunkt auf und liefert ggf. die dadurch abgeschlossene Epoche. */
    fun add(sample: SensorSample): EpochMeasurement? {
        val start = epochStart(sample.timestamp)
        val current = open[sample.side]
        val finished = when {
            current == null -> null
            start.isBefore(current.start) -> return null // verspäteter Messpunkt: verwerfen
            start.isAfter(current.start) -> current.toMeasurement(sample.side)
            else -> null
        }
        val bucket = if (current == null || start.isAfter(current.start)) Bucket(start).also { open[sample.side] = it } else current
        bucket.count++
        sample.heartRateBpm?.let { bucket.heartRate += it.toDouble() }
        sample.hrvRmssdMs?.let { bucket.rmssd += it }
        sample.spo2Percent?.let { bucket.spo2 += it.toDouble() }
        sample.skinTemperatureC?.let { bucket.skin += it }
        sample.motion?.let { bucket.motion += it }
        return finished
    }

    /** Schließt alle offenen Epochen ab (z. B. beim Beenden der Aufzeichnung). */
    fun flush(): List<EpochMeasurement> {
        val result = open.map { (side, bucket) -> bucket.toMeasurement(side) }
        open.clear()
        return result
    }

    private fun Bucket.toMeasurement(side: SockSide) = EpochMeasurement(
        side = side,
        start = start,
        heartRateBpm = heartRate.averageOrNull(),
        hrvRmssdMs = rmssd.averageOrNull(),
        spo2Percent = spo2.averageOrNull(),
        skinTemperatureC = skin.averageOrNull(),
        motion = motion.averageOrNull(),
        sampleCount = count,
    )

    private fun List<Double>.averageOrNull() = if (isEmpty()) null else average()

    companion object {
        fun epochStart(time: Instant): Instant {
            val length = EPOCH_LENGTH.toMillis()
            return Instant.ofEpochMilli(Math.floorDiv(time.toEpochMilli(), length) * length)
        }
    }
}
