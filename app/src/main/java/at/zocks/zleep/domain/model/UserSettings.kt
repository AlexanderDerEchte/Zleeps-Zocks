package at.zocks.zleep.domain.model

import at.zocks.zleep.domain.alarm.AlarmMethod
import at.zocks.zleep.domain.heat.HeatMode
import at.zocks.zleep.domain.massage.BuiltInMassagePrograms
import at.zocks.zleep.domain.routine.EveningRoutine
import java.time.LocalTime

enum class DeviceMode { SIMULATOR, BLE }

enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

data class UserSettings(
    /** Bis zur BLE-Umsetzung (Phase 7) ist der Simulator Standard. */
    val deviceMode: DeviceMode = DeviceMode.SIMULATOR,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val use24HourClock: Boolean = true,
    val bedtime: LocalTime = LocalTime.of(22, 30),
    val wakeTime: LocalTime = LocalTime.of(6, 45),
    val sleepGoalMinutes: Int = 480,
    val onboardingCompleted: Boolean = false,
    /** Zeitraffer-Faktor für die simulierte Nacht. */
    val simulatorSpeed: Int = 300,
    val heat: HeatPreferences = HeatPreferences(),
    val massage: MassagePreferences = MassagePreferences(),
    val alarm: AlarmPreferences = AlarmPreferences(),
    val routine: EveningRoutine = EveningRoutine.Default,
    /** Abendroutine beim Start der Nacht mitstarten. */
    val routineWithNight: Boolean = false,
    /** Abgeschlossene Nächte automatisch nach Health Connect übertragen. */
    val healthConnectAutoExport: Boolean = false,
)

/** Smarter Wecker: weckt im Fenster vor [UserSettings.wakeTime] in leichtem Schlaf. */
data class AlarmPreferences(
    val enabled: Boolean = false,
    val windowMinutes: Int = 30,
    val method: AlarmMethod = AlarmMethod.MASSAGE_THEN_SOUND,
) {
    companion object {
        val WINDOW_OPTIONS = listOf(10, 20, 30, 45)
    }
}

/** Zuletzt gewählte Heizeinstellungen und Vorwärmen. */
data class HeatPreferences(
    val side: SockSide = SockSide.BOTH,
    val mode: HeatMode = HeatMode.LEVEL,
    val level: Int = 3,
    val targetTemperatureC: Double = 34.0,
    val timerMinutes: Int = 30,
    val autoOffWhenAsleep: Boolean = true,
    val preheatEnabled: Boolean = false,
    val preheatTime: LocalTime = LocalTime.of(22, 0),
    val preheatMinutes: Int = 20,
)

/** Zuletzt gewählte Massage und Favoriten. */
data class MassagePreferences(
    val side: SockSide = SockSide.BOTH,
    val programId: String = BuiltInMassagePrograms.RELAX.id,
    val intensity: Int = 50,
    val durationMinutes: Int = 15,
    val favorites: Set<String> = setOf(BuiltInMassagePrograms.RELAX.id, BuiltInMassagePrograms.SLEEP.id),
)
