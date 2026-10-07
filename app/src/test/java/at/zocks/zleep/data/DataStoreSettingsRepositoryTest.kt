package at.zocks.zleep.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import at.zocks.zleep.data.settings.DataStoreSettingsRepository
import at.zocks.zleep.domain.alarm.AlarmMethod
import at.zocks.zleep.domain.heat.HeatMode
import at.zocks.zleep.domain.model.AlarmPreferences
import at.zocks.zleep.domain.model.DeviceMode
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.domain.model.TemperatureUnit
import at.zocks.zleep.domain.model.UserSettings
import at.zocks.zleep.domain.routine.EveningRoutine
import at.zocks.zleep.domain.routine.RoutineHeat
import at.zocks.zleep.domain.routine.RoutineMassage
import at.zocks.zleep.domain.routine.RoutineStep
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalTime

class DataStoreSettingsRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.dataStore() = PreferenceDataStoreFactory.create(
        scope = backgroundScope,
        produceFile = { File(folder.root, "settings.preferences_pb") },
    )

    @Test
    fun `defaults without stored values`() = runTest {
        val repository = DataStoreSettingsRepository(dataStore())
        assertThat(repository.settings.first()).isEqualTo(UserSettings())
    }

    @Test
    fun `updates are persisted`() = runTest {
        val store = dataStore()
        val repository = DataStoreSettingsRepository(store)
        repository.update {
            it.copy(
                deviceMode = DeviceMode.BLE,
                temperatureUnit = TemperatureUnit.FAHRENHEIT,
                use24HourClock = false,
                bedtime = LocalTime.of(23, 15),
                sleepGoalMinutes = 450,
                simulatorSpeed = 1200,
            )
        }
        val stored = DataStoreSettingsRepository(store).settings.first()
        assertThat(stored.deviceMode).isEqualTo(DeviceMode.BLE)
        assertThat(stored.temperatureUnit).isEqualTo(TemperatureUnit.FAHRENHEIT)
        assertThat(stored.use24HourClock).isFalse()
        assertThat(stored.bedtime).isEqualTo(LocalTime.of(23, 15))
        assertThat(stored.sleepGoalMinutes).isEqualTo(450)
        assertThat(stored.simulatorSpeed).isEqualTo(1200)
    }

    @Test
    fun `heat and massage preferences are persisted`() = runTest {
        val store = dataStore()
        DataStoreSettingsRepository(store).update {
            it.copy(
                heat = it.heat.copy(
                    side = SockSide.LEFT,
                    mode = HeatMode.TARGET,
                    targetTemperatureC = 36.5,
                    preheatEnabled = true,
                    preheatTime = LocalTime.of(21, 45),
                ),
                massage = it.massage.copy(programId = "custom:3", intensity = 80, favorites = setOf("builtin:wave")),
            )
        }
        val stored = DataStoreSettingsRepository(store).settings.first()
        assertThat(stored.heat.side).isEqualTo(SockSide.LEFT)
        assertThat(stored.heat.mode).isEqualTo(HeatMode.TARGET)
        assertThat(stored.heat.targetTemperatureC).isEqualTo(36.5)
        assertThat(stored.heat.preheatEnabled).isTrue()
        assertThat(stored.heat.preheatTime).isEqualTo(LocalTime.of(21, 45))
        assertThat(stored.massage.programId).isEqualTo("custom:3")
        assertThat(stored.massage.favorites).containsExactly("builtin:wave")
    }

    @Test
    fun `unknown values fall back to defaults`() = runTest {
        val store = dataStore()
        store.edit {
            it[stringPreferencesKey("device_mode")] = "TELEPATHY"
            it[stringPreferencesKey("bedtime")] = "kurz nach zehn"
        }
        val settings = DataStoreSettingsRepository(store).settings.first()
        assertThat(settings.deviceMode).isEqualTo(UserSettings().deviceMode)
        assertThat(settings.bedtime).isEqualTo(UserSettings().bedtime)
    }

    @Test
    fun `alarm, routine and health connect settings are persisted`() = runTest {
        val store = dataStore()
        val routine = EveningRoutine(
            steps = listOf(
                RoutineStep(10, heat = RoutineHeat(4)),
                RoutineStep(5),
                RoutineStep(20, heat = RoutineHeat(1), massage = RoutineMassage("custom:3", 25)),
            ),
            endWhenAsleep = false,
            side = SockSide.LEFT,
        )
        DataStoreSettingsRepository(store).update {
            it.copy(
                alarm = AlarmPreferences(enabled = true, windowMinutes = 45, method = AlarmMethod.SOUND),
                routine = routine,
                routineWithNight = true,
                healthConnectAutoExport = true,
            )
        }
        val settings = DataStoreSettingsRepository(store).settings.first()
        assertThat(settings.alarm).isEqualTo(AlarmPreferences(enabled = true, windowMinutes = 45, method = AlarmMethod.SOUND))
        assertThat(settings.routine).isEqualTo(routine)
        assertThat(settings.routineWithNight).isTrue()
        assertThat(settings.healthConnectAutoExport).isTrue()
    }

    @Test
    fun `broken routine and unknown alarm values fall back to defaults`() = runTest {
        val store = dataStore()
        store.edit {
            it[stringPreferencesKey("evening_routine")] = "{kaputt"
            it[stringPreferencesKey("alarm_method")] = "TROMPETE"
            it[intPreferencesKey("alarm_window_minutes")] = 17
        }
        val settings = DataStoreSettingsRepository(store).settings.first()
        assertThat(settings.routine).isEqualTo(EveningRoutine.Default)
        assertThat(settings.alarm).isEqualTo(AlarmPreferences())
    }
}
