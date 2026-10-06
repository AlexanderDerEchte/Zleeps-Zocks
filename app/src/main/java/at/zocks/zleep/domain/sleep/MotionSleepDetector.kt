package at.zocks.zleep.domain.sleep

import at.zocks.zleep.domain.model.SensorSample
import java.time.Duration
import java.time.Instant

/**
 * Einfache Live-Erkennung „eingeschlafen“ aus der Bewegung (Aktigraphie):
 * Schlaf gilt als erkannt, wenn über [window] die mittlere Bewegung beider Socken unter
 * [averageThreshold] liegt und es im letzten [quietPeriod] keine deutliche Bewegung
 * (> [spikeThreshold]) gab. Wird in Phase 4 um Puls und HRV ergänzt.
 *
 * Messwerte ohne Bewegungswert werden ignoriert – sie zählen weder für noch gegen Schlaf.
 */
class MotionSleepDetector(
    private val window: Duration = Duration.ofMinutes(10),
    private val quietPeriod: Duration = Duration.ofMinutes(5),
    private val averageThreshold: Double = 0.05,
    private val spikeThreshold: Double = 0.2,
) {
    private val history = ArrayDeque<Pair<Instant, Double>>()

    var isAsleep: Boolean = false
        private set

    fun reset() {
        history.clear()
        isAsleep = false
    }

    /** Verarbeitet einen Messwert und liefert den neuen Zustand. */
    fun onSample(sample: SensorSample): Boolean {
        val motion = sample.motion ?: return isAsleep
        val time = sample.timestamp
        // Zeitsprung rückwärts (z. B. neue Simulation): neu beginnen.
        if (history.isNotEmpty() && time.isBefore(history.last().first)) reset()
        history.addLast(time to motion)
        while (history.isNotEmpty() && Duration.between(history.first().first, time) > window) history.removeFirst()

        val covered = Duration.between(history.first().first, time)
        isAsleep = covered >= window.minusSeconds(SLACK_SECONDS) &&
            history.map { it.second }.average() < averageThreshold &&
            history.none { (at, value) -> Duration.between(at, time) <= quietPeriod && value > spikeThreshold }
        return isAsleep
    }

    private companion object {
        /** Toleranz, weil Messwerte nur alle paar Sekunden kommen. */
        const val SLACK_SECONDS = 30L
    }
}
