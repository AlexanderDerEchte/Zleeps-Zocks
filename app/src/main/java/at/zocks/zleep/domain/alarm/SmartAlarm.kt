package at.zocks.zleep.domain.alarm

import at.zocks.zleep.domain.model.SleepStage
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Wie der Wecker weckt. */
enum class AlarmMethod {
    SOUND,
    MASSAGE,

    /** Erst sanfte Massage, nach [SmartAlarmDecider.ESCALATION] zusätzlich Ton. */
    MASSAGE_THEN_SOUND,
}

/** Zeitfenster, in dem geweckt wird: frühestens [start], spätestens [end] (gewünschte Aufstehzeit). */
data class WakeWindow(val start: Instant, val end: Instant) {
    operator fun contains(time: Instant): Boolean = !time.isBefore(start) && time.isBefore(end)
}

/** Warum geweckt wurde. */
enum class WakeReason {
    /** Leichter Schlaf (oder schon wach) im Zeitfenster erkannt. */
    LIGHT_SLEEP,

    /** Ende des Fensters erreicht. */
    DEADLINE,

    /** Ausfallsicherung: der exakte Systemwecker hat ausgelöst (App war z. B. beendet). */
    FALLBACK,

    /** Nach dem Schlummern. */
    SNOOZE,
}

object SmartAlarmPlanner {

    /** Eine Nacht dauert mindestens so lange, bevor der Wecker überhaupt in Frage kommt. */
    val MIN_NIGHT: Duration = Duration.ofHours(1)

    /**
     * Fenster für eine Nacht, die um [nightStart] begann: Ende ist das nächste [wakeTime]
     * frühestens [MIN_NIGHT] nach Beginn, Start [windowMinutes] davor (nie vor Beginn).
     * Rechnet in der Zeitzone [zone], also auch über die Zeitumstellung korrekt.
     */
    fun window(nightStart: Instant, wakeTime: LocalTime, windowMinutes: Int, zone: ZoneId): WakeWindow {
        val earliest = nightStart.plus(MIN_NIGHT).atZone(zone)
        var end = earliest.toLocalDate().atTime(wakeTime).atZone(zone)
        if (end.isBefore(earliest)) end = end.plusDays(1)
        val start = end.minusMinutes(windowMinutes.toLong()).toInstant()
        return WakeWindow(maxOf(start, nightStart), end.toInstant())
    }
}

/**
 * Entscheidet, ob jetzt geweckt wird. Im Fenster genügt die zuletzt geschätzte Phase
 * „leicht“ oder „wach“, sofern sie aktuell ist (höchstens [STAGE_MAX_AGE] alt, gemessen am
 * letzten Messwert). Am Ende des Fensters wird in jedem Fall geweckt.
 */
object SmartAlarmDecider {

    val STAGE_MAX_AGE: Duration = Duration.ofMinutes(3)

    /** Bei „erst Massage, dann Ton“: so lange nur Massage. */
    val ESCALATION: Duration = Duration.ofMinutes(3)

    fun decide(now: Instant, window: WakeWindow, latestStage: SleepStage?, latestStageAt: Instant?): WakeReason? = when {
        now.isBefore(window.start) -> null
        !now.isBefore(window.end) -> WakeReason.DEADLINE
        latestStage != null && latestStageAt != null &&
            latestStage in LIGHT_STAGES &&
            Duration.between(latestStageAt, now) <= STAGE_MAX_AGE -> WakeReason.LIGHT_SLEEP
        else -> null
    }

    private val LIGHT_STAGES = setOf(SleepStage.LIGHT, SleepStage.AWAKE)
}
