package at.zocks.zleep.domain.health

import at.zocks.zleep.domain.model.NightSource
import at.zocks.zleep.domain.model.SleepStage.AWAKE
import at.zocks.zleep.domain.model.SleepStage.LIGHT
import at.zocks.zleep.domain.model.UserSettings
import at.zocks.zleep.domain.recording.LiveRecording
import at.zocks.zleep.domain.recording.RecordingState
import at.zocks.zleep.testing.FakeNightRepository
import at.zocks.zleep.testing.FakeSettingsRepository
import at.zocks.zleep.testing.TEST_NIGHT_START
import at.zocks.zleep.testing.minutes
import at.zocks.zleep.testing.testNightData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HealthExporterTest {

    private class FakeGateway(var available: HealthAvailability = HealthAvailability.AVAILABLE, var granted: Boolean = true) :
        HealthConnectGateway {
        val written = mutableListOf<HealthSleepSession>()
        override val requiredPermissions = setOf("schlaf")
        override fun availability() = available
        override suspend fun hasPermissions() = granted
        override suspend fun write(session: HealthSleepSession) {
            written += session
        }
    }

    private val recording = object : LiveRecording {
        override val state = MutableStateFlow<RecordingState>(RecordingState.Idle())
        override fun setAnalysisInterval(epochs: Int) = Unit
    }
    private val nights = FakeNightRepository()
    private val plan = listOf(AWAKE to minutes(10), LIGHT to minutes(30), AWAKE to minutes(5))

    @Test
    fun `exports all real nights and skips demo nights`() = runTest {
        val gateway = FakeGateway()
        nights.insertCompleteNight(testNightData(plan, id = 0))
        val demo = testNightData(plan, id = 0)
        nights.insertCompleteNight(demo.copy(night = demo.night.copy(source = NightSource.DEMO)))
        val exporter = HealthExporter(nights, gateway, FakeSettingsRepository(), recording, backgroundScope)

        assertThat(exporter.exportAll()).isEqualTo(1)
        assertThat(gateway.written).hasSize(1)

        gateway.granted = false
        assertThat(exporter.exportAll()).isEqualTo(0)
        gateway.granted = true
        gateway.available = HealthAvailability.NOT_INSTALLED
        assertThat(exporter.exportAll()).isEqualTo(0)
    }

    @Test
    fun `exports a night automatically when the recording ends, if enabled`() = runTest {
        val gateway = FakeGateway()
        val settings = FakeSettingsRepository(UserSettings(healthConnectAutoExport = true))
        val id = nights.insertCompleteNight(testNightData(plan, id = 0))
        val exporter = HealthExporter(nights, gateway, settings, recording, backgroundScope)
        exporter.start()
        runCurrent()

        recording.state.value = RecordingState.Active(id, TEST_NIGHT_START, TEST_NIGHT_START)
        runCurrent()
        recording.state.value = RecordingState.Idle(lastNightId = id)
        runCurrent()
        assertThat(gateway.written.map { it.id }).containsExactly("zocks-night-$id")

        settings.update { it.copy(healthConnectAutoExport = false) }
        recording.state.value = RecordingState.Active(id, TEST_NIGHT_START, TEST_NIGHT_START)
        runCurrent()
        recording.state.value = RecordingState.Idle(lastNightId = id)
        runCurrent()
        assertThat(gateway.written).hasSize(1)
    }
}
