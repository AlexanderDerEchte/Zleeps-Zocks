package at.zocks.zleep.domain.heat

import at.zocks.zleep.domain.model.SockSide
import java.time.Duration
import java.time.Instant

enum class HeatMode {
    /** Feste Heizstufe. */
    LEVEL,

    /** Auf eine Zieltemperatur am Fuß regeln. */
    TARGET,
}

data class HeatRequest(
    val side: SockSide = SockSide.BOTH,
    val mode: HeatMode = HeatMode.LEVEL,
    val level: Int = 3,
    val targetTemperatureC: Double = 34.0,
    /** Automatische Abschaltung; höchstens [HeatSafetyGuard.MAX_CONTINUOUS]. */
    val duration: Duration = Duration.ofMinutes(30),
)

/** Heizplan einer Socke, wie die App ihn steuert. */
data class SideHeatPlan(
    val level: Int,
    val targetTemperatureC: Double?,
    val startedAt: Instant,
    val endsAt: Instant,
    val preheat: Boolean,
)

/** Meldung des Heizreglers an die Oberfläche. */
sealed interface HeatNotice {
    val side: SockSide

    data class SafetyShutoff(override val side: SockSide, val reason: ShutoffReason) : HeatNotice
    data class Rejected(override val side: SockSide, val reason: HeatRejection) : HeatNotice
    data class TimerFinished(override val side: SockSide) : HeatNotice
    data class FellAsleep(override val side: SockSide) : HeatNotice
    data class PreheatStarted(override val side: SockSide) : HeatNotice
}

data class HeatControlState(
    /** Aktive Pläne je Socke (nur LEFT/RIGHT). */
    val plans: Map<SockSide, SideHeatPlan> = emptyMap(),
    val autoOffWhenAsleep: Boolean = false,
    val notice: HeatNotice? = null,
) {
    val isHeating: Boolean get() = plans.isNotEmpty()
}
