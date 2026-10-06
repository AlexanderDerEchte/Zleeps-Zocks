package at.zocks.zleep.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import at.zocks.zleep.data.settings.DataStoreSettingsRepository
import at.zocks.zleep.domain.model.DeviceMode
import at.zocks.zleep.domain.model.TemperatureUnit
import at.zocks.zleep.domain.model.UserSettings
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
}
