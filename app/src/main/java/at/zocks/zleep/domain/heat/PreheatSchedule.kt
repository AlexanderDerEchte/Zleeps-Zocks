package at.zocks.zleep.domain.heat

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Plant das tägliche Vorwärmen (Umsetzung über den Systemwecker in der Datenschicht). */
interface PreheatScheduler {
    fun schedule(at: Instant)
    fun cancel()
}

object PreheatTiming {
    /** Nächster Zeitpunkt für [time] ab [now] (heute, falls noch nicht vorbei, sonst morgen). */
    fun nextOccurrence(time: LocalTime, now: Instant, zone: ZoneId): Instant {
        val local = now.atZone(zone)
        val today = local.toLocalDate().atTime(time).atZone(zone)
        return (if (today.toInstant().isAfter(now)) today else today.plusDays(1)).toInstant()
    }
}
