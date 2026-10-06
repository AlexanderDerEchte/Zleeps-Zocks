package at.zocks.zleep.ui.heat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.zocks.zleep.domain.device.SockPairProvider
import at.zocks.zleep.domain.heat.HeatController
import at.zocks.zleep.domain.heat.HeatMode
import at.zocks.zleep.domain.heat.HeatRequest
import at.zocks.zleep.domain.heat.HeatSafetyGuard
import at.zocks.zleep.domain.heat.SideHeatPlan
import at.zocks.zleep.domain.model.HeatPreferences
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.domain.model.TemperatureUnit
import at.zocks.zleep.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject

data class HeatUiState(
    val loading: Boolean = true,
    val prefs: HeatPreferences = HeatPreferences(),
    val plans: Map<SockSide, SideHeatPlan> = emptyMap(),
    val footTemperature: Map<SockSide, Double> = emptyMap(),
    val heatLevels: Int = 5,
    val cooldownUntil: Instant? = null,
    val now: Instant = Instant.EPOCH,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val use24HourClock: Boolean = true,
) {
    val isHeating: Boolean get() = plans.isNotEmpty()

    /** Längste verbleibende Heizzeit über alle Socken. */
    val remaining: Duration?
        get() = plans.values.maxOfOrNull { Duration.between(now, it.endsAt) }?.coerceAtLeast(Duration.ZERO)

    companion object {
        val TIMER_OPTIONS = listOf(15, 30, 45, 60, 90)
        val PREHEAT_OPTIONS = listOf(10, 20, 30)
        const val TARGET_STEP_C = 0.5
    }
}

sealed interface HeatEvent {
    data class SetSide(val side: SockSide) : HeatEvent
    data class SetMode(val mode: HeatMode) : HeatEvent
    data class SetLevel(val level: Int) : HeatEvent
    data class ChangeTarget(val steps: Int) : HeatEvent
    data class SetTimer(val minutes: Int) : HeatEvent
    data class SetAutoOff(val enabled: Boolean) : HeatEvent
    data object Start : HeatEvent
    data object Stop : HeatEvent
    data class SetPreheatEnabled(val enabled: Boolean) : HeatEvent
    data class SetPreheatTime(val time: LocalTime) : HeatEvent
    data class SetPreheatMinutes(val minutes: Int) : HeatEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HeatViewModel @Inject constructor(
    private val heatController: HeatController,
    private val settingsRepository: SettingsRepository,
    pairProvider: SockPairProvider,
    private val clock: Clock,
) : ViewModel() {

    private val footTemperatures: Flow<Map<SockSide, Double>> = pairProvider.pair.flatMapLatest { pair ->
        merge(pair.left.sensorData, pair.right.sensorData).scan(emptyMap()) { latest, sample ->
            sample.skinTemperatureC?.let { latest + (sample.side to it) } ?: latest
        }
    }

    private val heatLevels: Flow<Int> = pairProvider.pair.map { pair ->
        maxOf(pair.left.capabilities.heatLevels, pair.right.capabilities.heatLevels).coerceAtLeast(1)
    }

    /** Läuft nur während des Heizens, damit die Restzeit aktuell bleibt. */
    private val now: Flow<Instant> = heatController.state.map { it.isHeating }.distinctUntilChanged().flatMapLatest { heating ->
        if (heating) {
            flow {
                while (true) {
                    emit(clock.instant())
                    delay(TICK_MS)
                }
            }
        } else {
            flowOf(clock.instant())
        }
    }

    val uiState: StateFlow<HeatUiState> = combine(
        settingsRepository.settings,
        heatController.state,
        footTemperatures,
        heatLevels,
        now,
    ) { settings, control, temperatures, levels, time ->
        HeatUiState(
            loading = false,
            prefs = settings.heat,
            plans = control.plans,
            footTemperature = temperatures,
            heatLevels = levels,
            cooldownUntil = settings.heat.side.feet.mapNotNull { heatController.cooldownUntil(it) }.maxOrNull(),
            now = time,
            temperatureUnit = settings.temperatureUnit,
            use24HourClock = settings.use24HourClock,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HeatUiState())

    fun onEvent(event: HeatEvent) {
        when (event) {
            is HeatEvent.SetSide -> updatePrefs { it.copy(side = event.side) }
            is HeatEvent.SetMode -> updatePrefs { it.copy(mode = event.mode) }
            is HeatEvent.SetLevel -> updatePrefs { it.copy(level = event.level) }
            is HeatEvent.ChangeTarget -> updatePrefs {
                val target = it.targetTemperatureC + event.steps * HeatUiState.TARGET_STEP_C
                it.copy(targetTemperatureC = target.coerceIn(HeatSafetyGuard.TARGET_MIN_C, HeatSafetyGuard.TARGET_MAX_C))
            }
            is HeatEvent.SetTimer -> updatePrefs { it.copy(timerMinutes = event.minutes) }
            is HeatEvent.SetAutoOff -> updatePrefs { it.copy(autoOffWhenAsleep = event.enabled) }
            is HeatEvent.SetPreheatEnabled -> updatePrefs { it.copy(preheatEnabled = event.enabled) }
            is HeatEvent.SetPreheatTime -> updatePrefs { it.copy(preheatTime = event.time) }
            is HeatEvent.SetPreheatMinutes -> updatePrefs { it.copy(preheatMinutes = event.minutes) }
            HeatEvent.Start -> viewModelScope.launch {
                val prefs = uiState.value.prefs
                // Abgelehnte Socken meldet der Regler selbst (App-weite Meldung).
                heatController.start(
                    HeatRequest(
                        side = prefs.side,
                        mode = prefs.mode,
                        level = prefs.level,
                        targetTemperatureC = prefs.targetTemperatureC,
                        duration = Duration.ofMinutes(prefs.timerMinutes.toLong()),
                    ),
                )
            }
            HeatEvent.Stop -> viewModelScope.launch { heatController.stop(SockSide.BOTH) }
        }
    }

    private fun updatePrefs(transform: (HeatPreferences) -> HeatPreferences) {
        viewModelScope.launch { settingsRepository.update { it.copy(heat = transform(it.heat)) } }
    }

    private companion object {
        const val TICK_MS = 15_000L
    }
}
