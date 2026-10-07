package at.zocks.zleep.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import at.zocks.zleep.domain.model.AlarmPreferences
import at.zocks.zleep.domain.model.HeatPreferences
import at.zocks.zleep.domain.model.MassagePreferences
import at.zocks.zleep.domain.model.UserSettings
import at.zocks.zleep.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/** Einstellungen in DataStore. Unbekannte oder fehlende Werte fallen auf die Standardwerte zurück. */
@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<UserSettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it.toSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (UserSettings) -> UserSettings) {
        dataStore.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs.write(updated)
        }
    }

    private fun Preferences.toSettings(): UserSettings {
        val defaults = UserSettings()
        return UserSettings(
            deviceMode = enumOr(this[Keys.DEVICE_MODE], defaults.deviceMode),
            temperatureUnit = enumOr(this[Keys.TEMPERATURE_UNIT], defaults.temperatureUnit),
            use24HourClock = this[Keys.USE_24H] ?: defaults.use24HourClock,
            bedtime = timeOr(this[Keys.BEDTIME], defaults.bedtime),
            wakeTime = timeOr(this[Keys.WAKE_TIME], defaults.wakeTime),
            sleepGoalMinutes = this[Keys.SLEEP_GOAL] ?: defaults.sleepGoalMinutes,
            onboardingCompleted = this[Keys.ONBOARDING_DONE] ?: defaults.onboardingCompleted,
            simulatorSpeed = this[Keys.SIMULATOR_SPEED] ?: defaults.simulatorSpeed,
            heat = HeatPreferences(
                side = enumOr(this[Keys.HEAT_SIDE], defaults.heat.side),
                mode = enumOr(this[Keys.HEAT_MODE], defaults.heat.mode),
                level = this[Keys.HEAT_LEVEL] ?: defaults.heat.level,
                targetTemperatureC = this[Keys.HEAT_TARGET] ?: defaults.heat.targetTemperatureC,
                timerMinutes = this[Keys.HEAT_TIMER] ?: defaults.heat.timerMinutes,
                autoOffWhenAsleep = this[Keys.HEAT_AUTO_OFF] ?: defaults.heat.autoOffWhenAsleep,
                preheatEnabled = this[Keys.PREHEAT_ENABLED] ?: defaults.heat.preheatEnabled,
                preheatTime = timeOr(this[Keys.PREHEAT_TIME], defaults.heat.preheatTime),
                preheatMinutes = this[Keys.PREHEAT_MINUTES] ?: defaults.heat.preheatMinutes,
            ),
            massage = MassagePreferences(
                side = enumOr(this[Keys.MASSAGE_SIDE], defaults.massage.side),
                programId = this[Keys.MASSAGE_PROGRAM] ?: defaults.massage.programId,
                intensity = this[Keys.MASSAGE_INTENSITY] ?: defaults.massage.intensity,
                durationMinutes = this[Keys.MASSAGE_DURATION] ?: defaults.massage.durationMinutes,
                favorites = this[Keys.MASSAGE_FAVORITES] ?: defaults.massage.favorites,
            ),
            alarm = AlarmPreferences(
                enabled = this[Keys.ALARM_ENABLED] ?: defaults.alarm.enabled,
                windowMinutes = (this[Keys.ALARM_WINDOW] ?: defaults.alarm.windowMinutes)
                    .takeIf { it in AlarmPreferences.WINDOW_OPTIONS } ?: defaults.alarm.windowMinutes,
                method = enumOr(this[Keys.ALARM_METHOD], defaults.alarm.method),
            ),
            routine = RoutineJson.decode(this[Keys.ROUTINE]),
            routineWithNight = this[Keys.ROUTINE_WITH_NIGHT] ?: defaults.routineWithNight,
            healthConnectAutoExport = this[Keys.HEALTH_AUTO_EXPORT] ?: defaults.healthConnectAutoExport,
        )
    }

    private fun MutablePreferences.write(settings: UserSettings) {
        this[Keys.DEVICE_MODE] = settings.deviceMode.name
        this[Keys.TEMPERATURE_UNIT] = settings.temperatureUnit.name
        this[Keys.USE_24H] = settings.use24HourClock
        this[Keys.BEDTIME] = settings.bedtime.toString()
        this[Keys.WAKE_TIME] = settings.wakeTime.toString()
        this[Keys.SLEEP_GOAL] = settings.sleepGoalMinutes
        this[Keys.ONBOARDING_DONE] = settings.onboardingCompleted
        this[Keys.SIMULATOR_SPEED] = settings.simulatorSpeed
        with(settings.heat) {
            this@write[Keys.HEAT_SIDE] = side.name
            this@write[Keys.HEAT_MODE] = mode.name
            this@write[Keys.HEAT_LEVEL] = level
            this@write[Keys.HEAT_TARGET] = targetTemperatureC
            this@write[Keys.HEAT_TIMER] = timerMinutes
            this@write[Keys.HEAT_AUTO_OFF] = autoOffWhenAsleep
            this@write[Keys.PREHEAT_ENABLED] = preheatEnabled
            this@write[Keys.PREHEAT_TIME] = preheatTime.toString()
            this@write[Keys.PREHEAT_MINUTES] = preheatMinutes
        }
        with(settings.massage) {
            this@write[Keys.MASSAGE_SIDE] = side.name
            this@write[Keys.MASSAGE_PROGRAM] = programId
            this@write[Keys.MASSAGE_INTENSITY] = intensity
            this@write[Keys.MASSAGE_DURATION] = durationMinutes
            this@write[Keys.MASSAGE_FAVORITES] = favorites
        }
        this[Keys.ALARM_ENABLED] = settings.alarm.enabled
        this[Keys.ALARM_WINDOW] = settings.alarm.windowMinutes
        this[Keys.ALARM_METHOD] = settings.alarm.method.name
        this[Keys.ROUTINE] = RoutineJson.encode(settings.routine)
        this[Keys.ROUTINE_WITH_NIGHT] = settings.routineWithNight
        this[Keys.HEALTH_AUTO_EXPORT] = settings.healthConnectAutoExport
    }

    private inline fun <reified E : Enum<E>> enumOr(value: String?, default: E): E =
        value?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    private fun timeOr(value: String?, default: LocalTime): LocalTime =
        value?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: default

    private object Keys {
        val DEVICE_MODE = stringPreferencesKey("device_mode")
        val TEMPERATURE_UNIT = stringPreferencesKey("temperature_unit")
        val USE_24H = booleanPreferencesKey("use_24h")
        val BEDTIME = stringPreferencesKey("bedtime")
        val WAKE_TIME = stringPreferencesKey("wake_time")
        val SLEEP_GOAL = intPreferencesKey("sleep_goal_minutes")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val SIMULATOR_SPEED = intPreferencesKey("simulator_speed")
        val HEAT_SIDE = stringPreferencesKey("heat_side")
        val HEAT_MODE = stringPreferencesKey("heat_mode")
        val HEAT_LEVEL = intPreferencesKey("heat_level")
        val HEAT_TARGET = doublePreferencesKey("heat_target_c")
        val HEAT_TIMER = intPreferencesKey("heat_timer_minutes")
        val HEAT_AUTO_OFF = booleanPreferencesKey("heat_auto_off_asleep")
        val PREHEAT_ENABLED = booleanPreferencesKey("preheat_enabled")
        val PREHEAT_TIME = stringPreferencesKey("preheat_time")
        val PREHEAT_MINUTES = intPreferencesKey("preheat_minutes")
        val MASSAGE_SIDE = stringPreferencesKey("massage_side")
        val MASSAGE_PROGRAM = stringPreferencesKey("massage_program")
        val MASSAGE_INTENSITY = intPreferencesKey("massage_intensity")
        val MASSAGE_DURATION = intPreferencesKey("massage_duration_minutes")
        val MASSAGE_FAVORITES = stringSetPreferencesKey("massage_favorites")
        val ALARM_ENABLED = booleanPreferencesKey("alarm_enabled")
        val ALARM_WINDOW = intPreferencesKey("alarm_window_minutes")
        val ALARM_METHOD = stringPreferencesKey("alarm_method")
        val ROUTINE = stringPreferencesKey("evening_routine")
        val ROUTINE_WITH_NIGHT = booleanPreferencesKey("routine_with_night")
        val HEALTH_AUTO_EXPORT = booleanPreferencesKey("health_connect_auto_export")
    }
}
