package at.zocks.zleep.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
    }
}
