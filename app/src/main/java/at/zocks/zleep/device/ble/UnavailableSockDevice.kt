package at.zocks.zleep.device.ble

import at.zocks.zleep.domain.device.SockDevice
import at.zocks.zleep.domain.model.ConnectionState
import at.zocks.zleep.domain.model.DeviceCapabilities
import at.zocks.zleep.domain.model.HeatCommand
import at.zocks.zleep.domain.model.HeatState
import at.zocks.zleep.domain.model.MassageCommand
import at.zocks.zleep.domain.model.MassageState
import at.zocks.zleep.domain.model.SensorSample
import at.zocks.zleep.domain.model.SockSide
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Eine noch nicht gekoppelte Seite im BLE-Modus. Verbindet sich nie und liefert keine
 * Messwerte – es werden keine Werte erfunden. Befehle werden abgelehnt ([SockCommandException]).
 */
class UnavailableSockDevice(override val side: SockSide) : SockDevice {
    override val capabilities = DeviceCapabilities.None
    override val connectionState: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val batteryPercent: StateFlow<Int?> = MutableStateFlow(null)
    override val heatState: StateFlow<HeatState> = MutableStateFlow(HeatState.Off)
    override val massageState: StateFlow<MassageState> = MutableStateFlow(MassageState.Off)
    override val sensorData: Flow<SensorSample> = emptyFlow()

    override suspend fun connect() = Unit
    override suspend fun disconnect() = Unit
    override suspend fun setHeat(command: HeatCommand): Unit = throw SockCommandException("Nicht gekoppelt")
    override suspend fun stopHeat() = Unit
    override suspend fun startMassage(command: MassageCommand): Unit = throw SockCommandException("Nicht gekoppelt")
    override suspend fun stopMassage(fadeOutMs: Long) = Unit
}
