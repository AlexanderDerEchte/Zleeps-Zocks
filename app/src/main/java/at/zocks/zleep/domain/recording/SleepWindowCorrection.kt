package at.zocks.zleep.domain.recording

import at.zocks.zleep.domain.model.Night
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Hilfen für die manuelle Korrektur von Einschlaf- und Aufwachzeit. */
object SleepWindowCorrection {

    /**
     * Ordnet eine Uhrzeit dem richtigen Tag der Nacht zu: Von den Kandidaten am Abend und am
     * Morgen wird der genommen, der am nächsten an der Aufzeichnung liegt.
     */
    fun resolve(time: LocalTime, night: Night, zone: ZoneId): Instant {
        val evening = night.nightOf(zone)
        val end = night.end ?: night.start.plus(Duration.ofHours(12))
        val middle = night.start.plus(Duration.between(night.start, end).dividedBy(2))
        return listOf(evening, evening.plusDays(1))
            .map { it.atTime(time).atZone(zone).toInstant() }
            .minBy { Duration.between(it, middle).abs() }
    }

    /** Gültig, wenn Einschlafen vor dem Aufwachen und beides innerhalb der Aufzeichnung liegt. */
    fun isValid(onset: Instant, wake: Instant, night: Night): Boolean {
        val end = night.end ?: return onset.isBefore(wake) && !onset.isBefore(night.start)
        return onset.isBefore(wake) && !onset.isBefore(night.start) && !wake.isAfter(end)
    }
}
