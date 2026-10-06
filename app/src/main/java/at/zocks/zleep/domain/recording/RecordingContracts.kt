package at.zocks.zleep.domain.recording

import java.time.Instant

/**
 * Zeit, in der die Socken messen. Beim echten Gerät die Uhr des Handys, im Simulator
 * die (ggf. im Zeitraffer laufende) simulierte Zeit.
 */
interface DeviceClock {
    val isSimulated: Boolean
    fun now(): Instant
}

/** Startet/stoppt die Nachtaufzeichnung samt Vordergrunddienst (Umsetzung in `device`). */
interface RecordingLauncher {
    fun start()
    fun stop()

    /** Setzt eine offene Aufzeichnung fort, z. B. nach einem Absturz. */
    fun resumeIfNeeded()
}
