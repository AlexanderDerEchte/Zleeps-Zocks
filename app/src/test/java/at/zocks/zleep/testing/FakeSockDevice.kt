package at.zocks.zleep.testing

import at.zocks.zleep.domain.device.SockDevice
import at.zocks.zleep.domain.device.SockPair
import at.zocks.zleep.domain.device.SockPairProvider
import at.zocks.zleep.domain.model.ConnectionState
import at.zocks.zleep.domain.model.DeviceCapabilities
import at.zocks.zleep.domain.model.HeatCommand
import at.zocks.zleep.domain.model.HeatState
import at.zocks.zleep.domain.model.MassageCommand
import at.zocks.zleep.domain.model.MassageState
import at.zocks.zleep.domain.model.MassageZone
import at.zocks.zleep.domain.model.SensorSample
import at.zocks.zleep.domain.model.SockSide
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** Einfaches Testgerät, das Befehle nur protokolliert. */
class FakeSockDevice(
    override val side: SockSide,
    override val capabilities: DeviceCapabilities = DeviceCapabilities.None,
) : SockDevice {
    override val connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val batteryPercent = MutableStateFlow<Int?>(null)
    override val heatState = MutableStateFlow(HeatState.Off)
    override val massageState = MutableStateFlow(MassageState.Off)
    override val sensorData = MutableSharedFlow<SensorSample>(extraBufferCapacity = 64)

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

    val massageCommands = mutableListOf<MassageCommand>()

    override suspend fun startMassage(command: MassageCommand) {
        massageCommands += command
        commands += "massage ${command.pattern.id} ${command.intensity}"
        massageState.value = MassageState(true, command.pattern.id, command.intensity)
    }

    override suspend fun stopMassage(fadeOutMs: Long) {
        commands += "stopMassage $fadeOutMs"
        massageState.value = MassageState.Off
    }
}

/** Voll ausgestattete Socke für Regler-Tests. */
val FullCapabilities = DeviceCapabilities(
    heartRate = true,
    hrv = true,
    spo2 = true,
    skinTemperature = true,
    motion = true,
    massageZones = MassageZone.entries.toSet(),
    heatLevels = 5,
)

/** Fester Anbieter für ein Testpaar. */
class FakePairProvider(left: SockDevice, right: SockDevice) : SockPairProvider {
    override val pair = MutableStateFlow(SockPair(left, right))
}
