package at.zocks.zleep.device

import at.zocks.zleep.device.ble.UnavailableSockDevice
import at.zocks.zleep.device.simulator.SimulatedSockDevice
import at.zocks.zleep.device.simulator.SimulationEngine
import at.zocks.zleep.di.ApplicationScope
import at.zocks.zleep.domain.device.SockPair
import at.zocks.zleep.domain.device.SockPairProvider
import at.zocks.zleep.domain.model.DeviceMode
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.domain.model.UserSettings
import at.zocks.zleep.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Wählt je nach Entwickler-Einstellung das simulierte oder das echte Sockenpaar. */
@Singleton
class DefaultSockPairProvider @Inject constructor(
    settingsRepository: SettingsRepository,
    engine: SimulationEngine,
    @param:ApplicationScope private val scope: CoroutineScope,
) : SockPairProvider {

    private val simulatorPair by lazy {
        SockPair(
            SimulatedSockDevice(SockSide.LEFT, engine, scope, seed = 1, initialBattery = 92),
            SimulatedSockDevice(SockSide.RIGHT, engine, scope, seed = 2, initialBattery = 88),
        )
    }

    private val blePair by lazy {
        SockPair(UnavailableSockDevice(SockSide.LEFT), UnavailableSockDevice(SockSide.RIGHT))
    }

    private var active: SockPair? = null

    override val pair: StateFlow<SockPair> = settingsRepository.settings
        .map { it.deviceMode }
        .distinctUntilChanged()
        .map(::pairFor)
        .onEach { next ->
            // Beim Umschalten das bisherige Paar sauber trennen.
            active?.takeIf { it !== next }?.let { previous -> scope.launch { previous.disconnect() } }
            active = next
        }
        .stateIn(scope, SharingStarted.Eagerly, pairFor(UserSettings().deviceMode))

    private fun pairFor(mode: DeviceMode): SockPair = when (mode) {
        DeviceMode.SIMULATOR -> simulatorPair
        DeviceMode.BLE -> blePair
    }
}
