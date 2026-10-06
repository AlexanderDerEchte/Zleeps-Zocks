package at.zocks.zleep.domain.heat

import at.zocks.zleep.domain.model.SensorSample
import at.zocks.zleep.domain.model.SockSide
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/** Warum die Heizung aus Sicherheitsgründen abgeschaltet wurde. */
enum class ShutoffReason {
    /** Gemessene Temperatur über der harten Obergrenze. */
    OVER_TEMPERATURE,

    /** Maximale Heizdauer am Stück erreicht. */
    MAX_RUNTIME,

    /** Messwert unter dem plausiblen Bereich (Sensor lose oder defekt). */
    IMPLAUSIBLE_LOW,

    /** Messwert über dem plausiblen Bereich. */
    IMPLAUSIBLE_HIGH,

    /** Unplausibel schneller Temperatursprung. */
    IMPLAUSIBLE_JUMP,

    /** Während des Heizens kommt kein Temperaturwert mehr. */
    SENSOR_MISSING,
}

/** Warum ein Heizstart abgelehnt wurde. */
enum class HeatRejection {
    /** Das Gerät meldet keinen Temperatursensor – ohne Überwachung wird nicht geheizt. */
    NO_TEMPERATURE_SENSOR,

    /** Pause nach Erreichen der maximalen Heizdauer. */
    COOLDOWN,

    /** Socke nicht verbunden. */
    NOT_CONNECTED,
}

/**
 * Sicherheitslogik fürs Heizen. Jeder Heizbefehl und jeder Messwert während des Heizens
 * läuft hier durch. Grenzwerte:
 * - Zieltemperatur wird auf [TARGET_MIN_C]..[TARGET_MAX_C] begrenzt.
 * - Harte Abschaltung, wenn Haut oder Heizelement über [HARD_CUTOFF_C] liegen.
 * - Höchstens [MAX_CONTINUOUS] am Stück, danach [COOLDOWN] Pause.
 * - Unplausibel: unter [PLAUSIBLE_MIN_C], über [PLAUSIBLE_MAX_C], Sprung über
 *   [MAX_JUMP_C] innerhalb von [JUMP_WINDOW], oder länger als [SENSOR_TIMEOUT] kein Wert.
 *
 * Diese Prüfung ergänzt die Absicherung in der Firmware, sie ersetzt sie nicht.
 * Laufzeiten werden mit der Uhr der App gemessen, Sprünge mit den Zeitstempeln der Messwerte.
 */
class HeatSafetyGuard {

    private data class SideState(
        val heatingSince: Instant? = null,
        val lastTemperature: Double? = null,
        val lastTemperatureAt: Instant? = null,
        val lastSampleAt: Instant? = null,
        val cooldownUntil: Instant? = null,
    )

    private val sides = mutableMapOf(SockSide.LEFT to SideState(), SockSide.RIGHT to SideState())

    fun clampTarget(targetC: Double?): Double? = targetC?.coerceIn(TARGET_MIN_C, TARGET_MAX_C)

    /** Prüft, ob die Socke [side] jetzt heizen darf. `null` = erlaubt. */
    fun checkStart(side: SockSide, hasTemperatureSensor: Boolean, now: Instant): HeatRejection? {
        if (!hasTemperatureSensor) return HeatRejection.NO_TEMPERATURE_SENSOR
        val cooldown = state(side).cooldownUntil
        return if (cooldown != null && now.isBefore(cooldown)) HeatRejection.COOLDOWN else null
    }

    fun cooldownUntil(side: SockSide, now: Instant): Instant? = state(side).cooldownUntil?.takeIf { now.isBefore(it) }

    fun onHeatingStarted(side: SockSide, now: Instant) = update(side) {
        // Bei bereits laufender Heizung zählt die Laufzeit weiter (kein Zurücksetzen durch Stufenwechsel).
        it.copy(heatingSince = it.heatingSince ?: now, lastSampleAt = it.lastSampleAt ?: now)
    }

    fun onHeatingStopped(side: SockSide) = update(side) { it.copy(heatingSince = null) }

    /**
     * Wertet einen Messwert aus. Liefert einen Abschaltgrund, falls die Socke sofort
     * aufhören muss zu heizen; außerhalb des Heizens werden nur Verlaufswerte gemerkt.
     */
    fun onSample(sample: SensorSample, now: Instant): ShutoffReason? {
        val side = sample.side
        val before = state(side)
        val temperature = sample.skinTemperatureC
        val time = sample.timestamp

        val reason = if (before.heatingSince == null) {
            null
        } else {
            when {
                temperature == null && before.lastTemperatureAt != null &&
                    Duration.between(before.lastTemperatureAt, time) > SENSOR_TIMEOUT -> ShutoffReason.SENSOR_MISSING
                temperature == null && before.lastTemperatureAt == null &&
                    Duration.between(before.heatingSince, now) > SENSOR_TIMEOUT -> ShutoffReason.SENSOR_MISSING
                temperature != null && temperature < PLAUSIBLE_MIN_C -> ShutoffReason.IMPLAUSIBLE_LOW
                temperature != null && temperature > PLAUSIBLE_MAX_C -> ShutoffReason.IMPLAUSIBLE_HIGH
                temperature != null && isJump(before, temperature, time) -> ShutoffReason.IMPLAUSIBLE_JUMP
                (temperature ?: 0.0) > HARD_CUTOFF_C || (sample.heaterTemperatureC ?: 0.0) > HARD_CUTOFF_C ->
                    ShutoffReason.OVER_TEMPERATURE
                Duration.between(before.heatingSince, now) >= MAX_CONTINUOUS -> ShutoffReason.MAX_RUNTIME
                else -> null
            }
        }

        update(side) {
            it.copy(
                lastTemperature = temperature ?: it.lastTemperature,
                lastTemperatureAt = if (temperature != null) time else it.lastTemperatureAt,
                lastSampleAt = time,
            )
        }
        if (reason != null) onShutoff(side, reason, now)
        return reason
    }

    /** Prüft die Laufzeit auch ohne neuen Messwert (z. B. per Timer). */
    fun checkRuntime(side: SockSide, now: Instant): ShutoffReason? {
        val since = state(side).heatingSince ?: return null
        if (Duration.between(since, now) < MAX_CONTINUOUS) return null
        onShutoff(side, ShutoffReason.MAX_RUNTIME, now)
        return ShutoffReason.MAX_RUNTIME
    }

    private fun onShutoff(side: SockSide, reason: ShutoffReason, now: Instant) = update(side) {
        it.copy(
            heatingSince = null,
            cooldownUntil = if (reason == ShutoffReason.MAX_RUNTIME) now.plus(COOLDOWN) else it.cooldownUntil,
        )
    }

    private fun isJump(before: SideState, temperature: Double, time: Instant): Boolean {
        val last = before.lastTemperature ?: return false
        val lastAt = before.lastTemperatureAt ?: return false
        val elapsed = Duration.between(lastAt, time)
        return !elapsed.isNegative && elapsed <= JUMP_WINDOW && abs(temperature - last) > MAX_JUMP_C
    }

    private fun state(side: SockSide): SideState = sides.getValue(requireFoot(side))

    private fun update(side: SockSide, transform: (SideState) -> SideState) {
        val foot = requireFoot(side)
        sides[foot] = transform(sides.getValue(foot))
    }

    private fun requireFoot(side: SockSide): SockSide {
        require(side != SockSide.BOTH) { "Sicherheitsprüfung gilt pro Socke" }
        return side
    }

    companion object {
        const val TARGET_MIN_C = 20.0
        const val TARGET_MAX_C = 40.0
        const val HARD_CUTOFF_C = 42.0
        const val PLAUSIBLE_MIN_C = 10.0
        const val PLAUSIBLE_MAX_C = 50.0
        const val MAX_JUMP_C = 3.0
        val JUMP_WINDOW: Duration = Duration.ofSeconds(10)
        val SENSOR_TIMEOUT: Duration = Duration.ofSeconds(30)
        val MAX_CONTINUOUS: Duration = Duration.ofMinutes(90)
        val COOLDOWN: Duration = Duration.ofMinutes(15)
    }
}
