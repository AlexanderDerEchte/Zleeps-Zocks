package at.zocks.zleep.domain.control

import at.zocks.zleep.domain.device.SockPairProvider
import at.zocks.zleep.domain.heat.HeatController
import at.zocks.zleep.domain.heat.HeatRequest
import at.zocks.zleep.domain.heat.PreheatScheduler
import at.zocks.zleep.domain.heat.PreheatTiming
import at.zocks.zleep.domain.model.DeviceMode
import at.zocks.zleep.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.time.Duration

/**
 * Hält App-weite Steuerung im Einklang mit den Einstellungen:
 * „Abschalten beim Einschlafen“ und das tägliche Vorwärmen.
 */
class ControlCoordinator(
    private val settingsRepository: SettingsRepository,
    private val heatController: HeatController,
    private val pairProvider: SockPairProvider,
    private val scheduler: PreheatScheduler,
    private val scope: CoroutineScope,
    private val clock: Clock,
) {
    fun start() {
        scope.launch {
            settingsRepository.settings.map { it.heat.autoOffWhenAsleep }.distinctUntilChanged()
                .collect(heatController::setAutoOffWhenAsleep)
        }
        scope.launch {
            settingsRepository.settings.map { it.heat.preheatEnabled to it.heat.preheatTime }.distinctUntilChanged()
                .collect { reschedulePreheat() }
        }
    }

    suspend fun reschedulePreheat() {
        val heat = settingsRepository.settings.first().heat
        if (heat.preheatEnabled) {
            scheduler.schedule(PreheatTiming.nextOccurrence(heat.preheatTime, clock.instant(), clock.zone))
        } else {
            scheduler.cancel()
        }
    }

    /** Vom Wecker aufgerufen: Socken verbinden (Simulator) und vorwärmen, dann morgen erneut planen. */
    suspend fun runPreheat() {
        val settings = settingsRepository.settings.first()
        try {
            if (!settings.heat.preheatEnabled) return
            val pair = pairProvider.pair.value
            if (settings.deviceMode == DeviceMode.SIMULATOR) {
                withTimeoutOrNull(CONNECT_TIMEOUT.toMillis()) { pair.connect() }
            }
            heatController.start(
                HeatRequest(
                    side = settings.heat.side,
                    mode = settings.heat.mode,
                    level = settings.heat.level,
                    targetTemperatureC = settings.heat.targetTemperatureC,
                    duration = Duration.ofMinutes(settings.heat.preheatMinutes.toLong()),
                ),
                preheat = true,
            )
        } finally {
            reschedulePreheat()
        }
    }

    private companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(20)
    }
}
