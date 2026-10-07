package at.zocks.zleep.domain.alarm

import java.time.Instant

/** Zeigt den klingelnden Wecker (Benachrichtigung, Vollbild) und spielt auf Wunsch Ton. */
interface AlarmNotifier {
    /** Zeigt den Wecker; mit [sound] klingelt er dauerhaft, bis [dismiss] aufgerufen wird. */
    fun show(sound: Boolean)
    fun dismiss()
}

/**
 * Exakter Systemwecker als Ausfallsicherung: weckt auch, wenn die App beendet wurde.
 * Zeiten sind echte Uhrzeit (nicht Zeitraffer).
 */
interface WakeAlarmScheduler {
    fun schedule(at: Instant)
    fun cancel()

    /** Ob das System exakte Wecker erlaubt (ab Android 12 eine Freigabe). */
    fun canScheduleExact(): Boolean
}
