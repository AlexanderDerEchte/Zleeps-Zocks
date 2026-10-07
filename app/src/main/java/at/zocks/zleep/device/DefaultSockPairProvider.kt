package at.zocks.zleep.device

import at.zocks.zleep.device.ble.BleSockDevice
import at.zocks.zleep.device.ble.GattConnectionFactory
import at.zocks.zleep.device.ble.UnavailableSockDevice
import at.zocks.zleep.device.simulator.SimulatedSockDevice
import at.zocks.zleep.device.simulator.SimulationEngine
import at.zocks.zleep.di.ApplicationScope
import at.zocks.zleep.di.DefaultSettings
import at.zocks.zleep.domain.device.SockDevice
import at.zocks.zleep.domain.device.SockPair
import at.zocks.zleep.domain.device.SockPairProvider
import at.zocks.zleep.domain.model.DeviceMode
import at.zocks.zleep.domain.model.PairedSocks
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
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wählt je nach Entwickler-Einstellung das simulierte oder das echte Sockenpaar. Im BLE-Modus
 * wird jede gekoppelte Socke ein [BleSockDevice]; eine noch nicht gekoppelte Seite bleibt
 * [UnavailableSockDevice] (liefert nichts, nimmt keine Befehle an).
 */
@Singleton
class DefaultSockPairProvider @Inject constructor(
    settingsRepository: SettingsRepository,
    engine: SimulationEngine,
    private val gattFactory: GattConnectionFactory,
    private val clock: Clock,
    @param:ApplicationScope private val scope: CoroutineScope,
    @DefaultSettings defaults: UserSettings,
) : SockPairProvider {

    private val simulatorPair by lazy {
        SockPair(
            SimulatedSockDevice(SockSide.LEFT, engine, scope, seed = 1, initialBattery = 92),
            SimulatedSockDevice(SockSide.RIGHT, engine, scope, seed = 2, initialBattery = 88),
        )
    }

    private val unavailableLeft = UnavailableSockDevice(SockSide.LEFT)
    private val unavailableRight = UnavailableSockDevice(SockSide.RIGHT)

    /** Pro Seite und Adresse genau ein Gerät, damit eine bestehende Verbindung erhalten bleibt. */
    private val bleDevices = mutableMapOf<Pair<SockSide, String>, BleSockDevice>()

    private var active: SockPair? = null

    private data class Selection(val mode: DeviceMode, val socks: PairedSocks)

    override val pair: StateFlow<SockPair> = settingsRepository.settings
        .map { Selection(it.deviceMode, it.pairedSocks) }
        .distinctUntilChanged()
        .map(::pairFor)
        .onEach { next ->
            val previous = active
            active = next
            // Nicht mehr verwendete Socken sauber trennen.
            if (previous != null && previous !== next) {
                val stale = previous.devices().filter { it !== next.left && it !== next.right }
                if (stale.isNotEmpty()) scope.launch { stale.forEach { it.disconnect() } }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, pairFor(Selection(defaults.deviceMode, defaults.pairedSocks)))

    private fun pairFor(selection: Selection): SockPair = when (selection.mode) {
        DeviceMode.SIMULATOR -> simulatorPair
        DeviceMode.BLE -> {
            val left = bleDevice(SockSide.LEFT, selection.socks.left) ?: unavailableLeft
            val right = bleDevice(SockSide.RIGHT, selection.socks.right) ?: unavailableRight
            active?.takeIf { it.left === left && it.right === right } ?: SockPair(left, right)
        }
    }

    private fun bleDevice(side: SockSide, address: String?): SockDevice? = address?.let {
        synchronized(bleDevices) {
            bleDevices.getOrPut(side to it) { BleSockDevice(side, it, gattFactory, scope, clock) }
        }
    }
}
