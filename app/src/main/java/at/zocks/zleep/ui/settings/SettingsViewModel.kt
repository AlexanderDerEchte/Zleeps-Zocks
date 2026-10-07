package at.zocks.zleep.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.zocks.zleep.BuildConfig
import at.zocks.zleep.R
import at.zocks.zleep.domain.alarm.AlarmMethod
import at.zocks.zleep.domain.alarm.WakeAlarmScheduler
import at.zocks.zleep.domain.device.PairStatus
import at.zocks.zleep.domain.device.SockPairProvider
import at.zocks.zleep.domain.export.CsvExporter
import at.zocks.zleep.domain.export.ExportSink
import at.zocks.zleep.domain.health.HealthAvailability
import at.zocks.zleep.domain.health.HealthConnectGateway
import at.zocks.zleep.domain.health.HealthExporter
import at.zocks.zleep.domain.model.DeviceMode
import at.zocks.zleep.domain.model.Tag
import at.zocks.zleep.domain.model.TemperatureUnit
import at.zocks.zleep.domain.model.UserSettings
import at.zocks.zleep.domain.repository.NightRepository
import at.zocks.zleep.domain.repository.SettingsRepository
import at.zocks.zleep.ui.components.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalTime
import javax.inject.Inject

/** Was der Export braucht, um eingebaute Tags in der Sprache der App zu benennen. */
typealias TagNamer = (Tag) -> String

enum class ExportKind { NIGHTS, MEASUREMENTS }

data class SettingsUiState(
    val loading: Boolean = true,
    val settings: UserSettings = UserSettings(),
    val pairStatus: PairStatus? = null,
    val exactAlarmAllowed: Boolean = true,
    val healthAvailability: HealthAvailability = HealthAvailability.NOT_SUPPORTED,
    val healthGranted: Boolean = false,
    val healthPermissions: Set<String> = emptySet(),
    val busy: Boolean = false,
    /** Debug-Builds zeigen sie immer, Release-Builds erst nach siebenmaligem Tippen auf die Version. */
    val developerOptionsVisible: Boolean = BuildConfig.DEBUG,
    val userMessage: UiText? = null,
)

sealed interface SettingsEvent {
    data class SetTemperatureUnit(val unit: TemperatureUnit) : SettingsEvent
    data class SetUse24h(val enabled: Boolean) : SettingsEvent
    data class SetSleepGoal(val minutes: Int) : SettingsEvent
    data class SetBedtime(val time: LocalTime) : SettingsEvent
    data class SetWakeTime(val time: LocalTime) : SettingsEvent
    data class SetAlarmEnabled(val enabled: Boolean) : SettingsEvent
    data class SetAlarmWindow(val minutes: Int) : SettingsEvent
    data class SetAlarmMethod(val method: AlarmMethod) : SettingsEvent
    data object ConnectSocks : SettingsEvent
    data object DisconnectSocks : SettingsEvent
    data class Export(val kind: ExportKind, val target: String, val tagName: TagNamer) : SettingsEvent
    data class SetHealthAutoExport(val enabled: Boolean) : SettingsEvent
    data object ExportToHealthConnect : SettingsEvent
    data object DeleteAllNights : SettingsEvent
    data object ShowOnboardingAgain : SettingsEvent
    data object UnlockDeveloperOptions : SettingsEvent

    /** Nach Rückkehr aus Systemdialogen: Freigaben neu prüfen. */
    data object Refresh : SettingsEvent
    data object MessageShown : SettingsEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val pairProvider: SockPairProvider,
    private val nights: NightRepository,
    private val csvExporter: CsvExporter,
    private val exportSink: ExportSink,
    private val health: HealthConnectGateway,
    private val healthExporter: HealthExporter,
    private val wakeAlarms: WakeAlarmScheduler,
    private val clock: Clock,
) : ViewModel() {

    private data class Local(
        val exactAlarmAllowed: Boolean = true,
        val healthAvailability: HealthAvailability = HealthAvailability.NOT_SUPPORTED,
        val healthGranted: Boolean = false,
        val busy: Boolean = false,
        val message: UiText? = null,
    )

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        pairProvider.pair.flatMapLatest { it.status },
        local,
    ) { settings, status, l ->
        SettingsUiState(
            loading = false,
            settings = settings,
            pairStatus = status,
            exactAlarmAllowed = l.exactAlarmAllowed,
            healthAvailability = l.healthAvailability,
            healthGranted = l.healthGranted,
            healthPermissions = health.requiredPermissions,
            busy = l.busy,
            developerOptionsVisible = BuildConfig.DEBUG || settings.developerOptionsUnlocked,
            userMessage = l.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        refresh()
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.SetTemperatureUnit -> update { it.copy(temperatureUnit = event.unit) }
            is SettingsEvent.SetUse24h -> update { it.copy(use24HourClock = event.enabled) }
            is SettingsEvent.SetSleepGoal -> update { it.copy(sleepGoalMinutes = event.minutes.coerceIn(MIN_GOAL, MAX_GOAL)) }
            is SettingsEvent.SetBedtime -> update { it.copy(bedtime = event.time) }
            is SettingsEvent.SetWakeTime -> update { it.copy(wakeTime = event.time) }
            is SettingsEvent.SetAlarmEnabled -> update { it.copy(alarm = it.alarm.copy(enabled = event.enabled)) }
            is SettingsEvent.SetAlarmWindow -> update { it.copy(alarm = it.alarm.copy(windowMinutes = event.minutes)) }
            is SettingsEvent.SetAlarmMethod -> update { it.copy(alarm = it.alarm.copy(method = event.method)) }
            SettingsEvent.ConnectSocks -> connect()
            SettingsEvent.DisconnectSocks -> viewModelScope.launch { pairProvider.pair.value.disconnect() }
            is SettingsEvent.Export -> export(event)
            is SettingsEvent.SetHealthAutoExport -> update { it.copy(healthConnectAutoExport = event.enabled) }
            SettingsEvent.ExportToHealthConnect -> busy {
                val count = runCatching { healthExporter.exportAll() }.getOrElse { return@busy message(UiText.of(R.string.hc_failed)) }
                message(if (count == 0) UiText.of(R.string.hc_nothing) else UiText.Plural(R.plurals.hc_exported, count))
            }
            SettingsEvent.DeleteAllNights -> busy {
                nights.deleteAll()
                message(UiText.of(R.string.delete_done))
            }
            SettingsEvent.ShowOnboardingAgain -> update { it.copy(onboardingCompleted = false) }
            SettingsEvent.UnlockDeveloperOptions -> {
                update { it.copy(developerOptionsUnlocked = true) }
                message(UiText.of(R.string.settings_developer_unlocked))
            }
            SettingsEvent.Refresh -> refresh()
            SettingsEvent.MessageShown -> local.update { it.copy(message = null) }
        }
    }

    private fun refresh() {
        local.update { it.copy(exactAlarmAllowed = wakeAlarms.canScheduleExact(), healthAvailability = health.availability()) }
        viewModelScope.launch {
            val granted = local.value.healthAvailability == HealthAvailability.AVAILABLE &&
                runCatching { health.hasPermissions() }.getOrDefault(false)
            local.update { it.copy(healthGranted = granted) }
        }
    }

    private fun export(event: SettingsEvent.Export) = busy {
        val zone = clock.zone
        val count = runCatching {
            exportSink.write(event.target) { out ->
                when (event.kind) {
                    ExportKind.NIGHTS -> csvExporter.writeNights(out, zone, event.tagName)
                    ExportKind.MEASUREMENTS -> csvExporter.writeMeasurements(out, zone)
                }
            }
        }.getOrElse { return@busy message(UiText.of(R.string.export_failed)) }
        message(UiText.Plural(R.plurals.export_done, count))
    }

    private fun connect() {
        viewModelScope.launch {
            runCatching { pairProvider.pair.value.connect() }.onFailure { message(UiText.of(R.string.error_generic)) }
        }
    }

    private fun busy(block: suspend () -> Unit) = viewModelScope.launch {
        local.update { it.copy(busy = true) }
        try {
            block()
        } finally {
            local.update { it.copy(busy = false) }
        }
    }

    private fun message(text: UiText) = local.update { it.copy(message = text) }

    private fun update(transform: (UserSettings) -> UserSettings) = viewModelScope.launch { settingsRepository.update(transform) }

    private companion object {
        const val MIN_GOAL = 6 * 60
        const val MAX_GOAL = 10 * 60
    }
}
