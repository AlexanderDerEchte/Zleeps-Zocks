package at.zocks.zleep.device

import at.zocks.zleep.device.ble.BleSockDevice
import at.zocks.zleep.device.ble.GattConnectionFactory
import at.zocks.zleep.device.ble.UnavailableSockDevice
import at.zocks.zleep.device.simulator.NightScenarioGenerator
import at.zocks.zleep.device.simulator.SimulatedSockDevice
import at.zocks.zleep.device.simulator.SimulationEngine
import at.zocks.zleep.domain.model.ConnectionState
import at.zocks.zleep.domain.model.DeviceMode
import at.zocks.zleep.domain.model.PairedSocks
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.domain.model.UserSettings
import at.zocks.zleep.testing.FakeSettingsRepository
import at.zocks.zleep.testing.FakeSockFirmware
import at.zocks.zleep.testing.MutableClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultSockPairProviderTest {

    private val left = FakeSockFirmware("L", SockSide.LEFT)
    private val right = FakeSockFirmware("R", SockSide.RIGHT)
    private val factory = GattConnectionFactory { address -> if (address == "L") left.open() else right.open() }

    private fun TestScope.provider(settings: FakeSettingsRepository, defaults: UserSettings = UserSettings()): DefaultSockPairProvider {
        val clock = MutableClock()
        val engine = SimulationEngine(backgroundScope, clock, NightScenarioGenerator())
        return DefaultSockPairProvider(settings, engine, factory, clock, backgroundScope, defaults)
    }

    @Test
    fun startsWithTheInjectedDefaultMode() = runTest {
        val settings = FakeSettingsRepository(UserSettings(deviceMode = DeviceMode.BLE))
        val provider = provider(settings, defaults = UserSettings(deviceMode = DeviceMode.BLE))
        assertThat(provider.pair.value.left).isInstanceOf(UnavailableSockDevice::class.java)
    }

    @Test
    fun bleModeUsesPairedAddressesAndKeepsDevicesAcrossChanges() = runTest {
        val settings = FakeSettingsRepository(UserSettings(deviceMode = DeviceMode.BLE))
        val provider = provider(settings)
        runCurrent()
        assertThat(provider.pair.value.left).isInstanceOf(UnavailableSockDevice::class.java)
        assertThat(provider.pair.value.right).isInstanceOf(UnavailableSockDevice::class.java)

        settings.update { it.copy(pairedSocks = PairedSocks(left = "L")) }
        runCurrent()
        val leftDevice = provider.pair.value.left
        assertThat((leftDevice as BleSockDevice).address).isEqualTo("L")
        assertThat(provider.pair.value.right).isInstanceOf(UnavailableSockDevice::class.java)

        // Die rechte Socke dazu: die linke bleibt dasselbe Gerät (Verbindung bleibt bestehen).
        settings.update { it.copy(pairedSocks = it.pairedSocks.with(SockSide.RIGHT, "R")) }
        runCurrent()
        assertThat(provider.pair.value.left).isSameInstanceAs(leftDevice)
        assertThat((provider.pair.value.right as BleSockDevice).address).isEqualTo("R")
    }

    @Test
    fun unpairedSockIsDisconnected() = runTest {
        val settings = FakeSettingsRepository(UserSettings(deviceMode = DeviceMode.BLE, pairedSocks = PairedSocks("L", "R")))
        val provider = provider(settings)
        runCurrent()
        provider.pair.value.connect()
        runCurrent()
        val rightDevice = provider.pair.value.right
        assertThat(rightDevice.connectionState.value).isEqualTo(ConnectionState.CONNECTED)

        settings.update { it.copy(pairedSocks = it.pairedSocks.with(SockSide.RIGHT, null)) }
        runCurrent()
        assertThat(rightDevice.connectionState.value).isEqualTo(ConnectionState.DISCONNECTED)
        assertThat(provider.pair.value.left.connectionState.value).isEqualTo(ConnectionState.CONNECTED)
    }

    @Test
    fun switchingToSimulatorDisconnectsRealSocks() = runTest {
        val settings = FakeSettingsRepository(UserSettings(deviceMode = DeviceMode.BLE, pairedSocks = PairedSocks("L", "R")))
        val provider = provider(settings)
        runCurrent()
        provider.pair.value.connect()
        runCurrent()
        val ble = provider.pair.value

        settings.update { it.copy(deviceMode = DeviceMode.SIMULATOR) }
        runCurrent()
        assertThat(provider.pair.value.left).isInstanceOf(SimulatedSockDevice::class.java)
        assertThat(ble.left.connectionState.value).isEqualTo(ConnectionState.DISCONNECTED)
        assertThat(ble.right.connectionState.value).isEqualTo(ConnectionState.DISCONNECTED)
    }
}
