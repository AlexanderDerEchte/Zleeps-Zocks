package at.zocks.zleep.domain.device

import at.zocks.zleep.domain.model.ConnectionState
import at.zocks.zleep.domain.model.SockSide
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Zustand einer einzelnen Socke für die Anzeige. */
data class SockStatus(
    val side: SockSide,
    val connection: ConnectionState,
    val batteryPercent: Int?,
    val heating: Boolean,
    val massaging: Boolean,
)

data class PairStatus(val left: SockStatus, val right: SockStatus) {
    val bothConnected: Boolean
        get() = left.connection == ConnectionState.CONNECTED && right.connection == ConnectionState.CONNECTED
    val anyConnected: Boolean
        get() = left.connection == ConnectionState.CONNECTED || right.connection == ConnectionState.CONNECTED
    val anyHeating: Boolean get() = left.heating || right.heating

    fun of(side: SockSide): SockStatus = if (side == SockSide.RIGHT) right else left
}

/**
 * Linke und rechte Socke. Befehle gehen standardmäßig an beide ([SockSide.BOTH]),
 * können aber gezielt an eine Seite gerichtet werden.
 */
class SockPair(val left: SockDevice, val right: SockDevice) {

    init {
        require(left.side == SockSide.LEFT && right.side == SockSide.RIGHT) { "Links/rechts vertauscht" }
    }

    fun devices(side: SockSide = SockSide.BOTH): List<SockDevice> = side.feet.map { if (it == SockSide.LEFT) left else right }

    val status: Flow<PairStatus> = combine(statusOf(left), statusOf(right)) { l, r -> PairStatus(l, r) }

    suspend fun connect(side: SockSide = SockSide.BOTH) = forEach(side) { it.connect() }

    suspend fun disconnect(side: SockSide = SockSide.BOTH) = forEach(side) { it.disconnect() }

    /** Führt [action] für die gewählten Socken parallel aus. */
    suspend fun forEach(side: SockSide, action: suspend (SockDevice) -> Unit) = coroutineScope {
        devices(side).forEach { device -> launch { action(device) } }
    }

    private fun statusOf(device: SockDevice): Flow<SockStatus> = combine(
        device.connectionState,
        device.batteryPercent,
        device.heatState,
        device.massageState,
    ) { connection, battery, heat, massage ->
        SockStatus(device.side, connection, battery, heat.active, massage.active)
    }
}

/** Liefert das aktive Sockenpaar – Simulator oder echtes Gerät, je nach Einstellung. */
interface SockPairProvider {
    val pair: StateFlow<SockPair>
}
