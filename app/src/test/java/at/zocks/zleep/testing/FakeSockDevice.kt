package at.zocks.zleep.testing

import at.zocks.zleep.domain.device.SockDevice
import at.zocks.zleep.domain.model.ConnectionState
import at.zocks.zleep.domain.model.DeviceCapabilities
import at.zocks.zleep.domain.model.HeatCommand
import at.zocks.zleep.domain.model.HeatState
import at.zocks.zleep.domain.model.MassageCommand
import at.zocks.zleep.domain.model.MassageState
import at.zocks.zleep.domain.model.SensorSample
import at.zocks.zleep.domain.model.SockSide
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** Einfaches Testgerät, das Befehle nur protokolliert. */
class FakeSockDevice(override val side: SockSide) : SockDevice {
    override val capabilities = DeviceCapabilities.None
    override val connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val batteryPercent = MutableStateFlow<Int?>(null)
    override val heatState = MutableStateFlow(HeatState.Off)
    override val massageState = MutableStateFlow(MassageState.Off)
    override val sensorData = MutableSharedFlow<SensorSample>()

    val commands = mutableListOf<String>()

    override suspend fun connect() {
        commands += "connect"
        connectionState.value = ConnectionState.CONNECTED
    }

    override suspend fun disconnect() {
        commands += "disconnect"
        connectionState.value = ConnectionState.DISCONNECTED
    }

    override suspend fun setHeat(command: HeatCommand) {
        commands += "heat ${command.level}"
        heatState.value = HeatState(true, command.level, command.targetTemperatureC)
    }

    override suspend fun stopHeat() {
        commands += "stopHeat"
        heatState.value = HeatState.Off
    }

    override suspend fun startMassage(command: MassageCommand) {
        commands += "massage ${command.pattern.id}"
        massageState.value = MassageState(true, command.pattern.id, command.intensity)
    }

    override suspend fun stopMassage(fadeOutMs: Long) {
        commands += "stopMassage"
        massageState.value = MassageState.Off
    }
}
