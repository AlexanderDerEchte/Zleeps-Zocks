package at.zocks.zleep.device.ble

import at.zocks.zleep.domain.model.ConnectionState
import at.zocks.zleep.domain.model.DeviceCapabilities
import at.zocks.zleep.domain.model.HeatCommand
import at.zocks.zleep.domain.model.MassageCommand
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.domain.massage.BuiltInMassagePrograms
import at.zocks.zleep.domain.massage.MassagePatterns
import at.zocks.zleep.testing.FakeSockFirmware
import at.zocks.zleep.testing.FullBleCapabilities
import at.zocks.zleep.testing.SchedulerClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BleSockDeviceTest {

    private val firmware = FakeSockFirmware("AA:BB:CC:00:00:01", SockSide.LEFT)

    private fun TestScope.device() =
        BleSockDevice(SockSide.LEFT, firmware.address, { firmware.open() }, backgroundScope, SchedulerClock(testScheduler))

    @Test
    fun `connects, reads capabilities and battery and forwards samples`() = runTest {
        val device = device()
        device.connect()
        assertThat(device.connectionState.value).isEqualTo(ConnectionState.CONNECTED)
        assertThat(device.capabilities).isEqualTo(FullBleCapabilities)
        assertThat(device.batteryPercent.value).isEqualTo(80)
        assertThat(firmware.current!!.mtu).isEqualTo(SockBleProtocol.PREFERRED_MTU)

        val received = mutableListOf<at.zocks.zleep.domain.model.SensorSample>()
        backgroundScope.launch { device.sensorData.collect { received += it } }
        runCurrent()
        firmware.sendSample(heartRate = 57, skin = 33.4, motion = 0.05)
        firmware.sendSample(heartRate = null, skin = null)
        runCurrent()

        assertThat(received).hasSize(2)
        assertThat(received[0].side).isEqualTo(SockSide.LEFT)
        assertThat(received[0].heartRateBpm).isEqualTo(57)
        assertThat(received[0].skinTemperatureC).isWithin(1e-9).of(33.4)
        assertThat(received[1].heartRateBpm).isNull()
        assertThat(received[1].skinTemperatureC).isNull()
    }

    @Test
    fun `commands reach the firmware and its status is mirrored`() = runTest {
        val device = device()
        device.connect()

        device.setHeat(HeatCommand(3, 34.0))
        runCurrent()
        assertThat(firmware.heating).isTrue()
        assertThat(firmware.heatLevel).isEqualTo(3)
        assertThat(device.heatState.value.level).isEqualTo(3)

        // Die Firmware schaltet selbst ab (Überhitzungsschutz) – die App zeigt das.
        firmware.overheatCutoff = true
        firmware.sendStatus()
        runCurrent()
        assertThat(device.heatState.value.active).isFalse()

        device.stopHeat()
        assertThat(firmware.heating).isFalse()
    }

    @Test
    fun `long massage patterns are split to fit a small mtu`() = runTest {
        firmware.maxMtu = 23
        val device = device()
        device.connect()
        val command = MassageCommand(MassagePatterns.patternFor(BuiltInMassagePrograms.WAVE, SockSide.LEFT), 60)

        device.startMassage(command)

        assertThat(firmware.framesReceived.size).isGreaterThan(1)
        assertThat(firmware.framesReceived.all { it.size <= 20 }).isTrue()
        assertThat(firmware.commands.last()).isEqualTo(SockBleProtocol.encodeMassage(command))
        assertThat(device.massageState.value.patternId).isEqualTo(command.pattern.id)
    }

    @Test
    fun `reconnects with growing pauses after the connection drops`() = runTest {
        val device = device()
        device.connect()
        firmware.failConnects = 1
        firmware.drop()
        runCurrent()
        assertThat(device.connectionState.value).isEqualTo(ConnectionState.RECONNECTING)

        advanceTimeBy(1_999)
        runCurrent()
        assertThat(firmware.opened).isEqualTo(1)
        advanceTimeBy(2)
        runCurrent()
        // Zweiter Versuch schlägt fehl, dann 5 s Pause.
        assertThat(firmware.opened).isEqualTo(2)
        assertThat(device.connectionState.value).isEqualTo(ConnectionState.RECONNECTING)
        advanceTimeBy(5_001)
        runCurrent()
        assertThat(firmware.opened).isEqualTo(3)
        assertThat(device.connectionState.value).isEqualTo(ConnectionState.CONNECTED)
    }

    @Test
    fun `disconnect ends reconnecting`() = runTest {
        val device = device()
        device.connect()
        device.disconnect()
        assertThat(device.connectionState.value).isEqualTo(ConnectionState.DISCONNECTED)
        assertThat(firmware.current!!.connected).isFalse()
        advanceTimeBy(120_000)
        assertThat(firmware.opened).isEqualTo(1)
    }

    @Test
    fun `commands that cannot be delivered throw`() = runTest {
        val device = device()
        assertThrows(SockCommandException::class.java) { kotlinx.coroutines.runBlocking { device.setHeat(HeatCommand(2, null)) } }
        device.connect()
        firmware.failWrites = true
        assertThrows(SockCommandException::class.java) { kotlinx.coroutines.runBlocking { device.stopHeat() } }
    }

    @Test
    fun `a sock without readable capabilities reports none`() = runTest {
        firmware.capabilities = null
        val device = device()
        device.connect()
        assertThat(device.connectionState.value).isEqualTo(ConnectionState.CONNECTED)
        assertThat(device.capabilities).isEqualTo(DeviceCapabilities.None)
    }
}
