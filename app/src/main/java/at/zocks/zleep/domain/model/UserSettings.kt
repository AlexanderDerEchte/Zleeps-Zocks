package at.zocks.zleep.domain.model

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
)
