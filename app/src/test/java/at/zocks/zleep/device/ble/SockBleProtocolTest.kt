package at.zocks.zleep.device.ble

import at.zocks.zleep.domain.model.HeatCommand
import at.zocks.zleep.domain.model.MassageCommand
import at.zocks.zleep.domain.model.MassagePattern
import at.zocks.zleep.domain.model.MassageStep
import at.zocks.zleep.domain.model.MassageZone
import at.zocks.zleep.domain.model.SockSide
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class SockBleProtocolTest {

    private val now = Instant.parse("2026-10-07T21:00:00Z")

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `parses a full sensor packet`() {
        // Puls 58, RMSSD 45.3 ms, SpO2 97 %, Haut 33.25 °C, Bewegung 0.1, Heizelement 36.5 °C, 2 RR-Intervalle.
        val packet = bytes(1, 58, 0xC5, 0x01, 97, 0xFD, 0x0C, 20, 0x42, 0x0E, 2, 0x00, 0x04, 0x10, 0x04)
        val sample = SockBleProtocol.parseSensor(packet, SockSide.LEFT, now)!!

        assertThat(sample.side).isEqualTo(SockSide.LEFT)
        assertThat(sample.timestamp).isEqualTo(now)
        assertThat(sample.heartRateBpm).isEqualTo(58)
        assertThat(sample.hrvRmssdMs).isWithin(1e-9).of(45.3)
        assertThat(sample.spo2Percent).isEqualTo(97)
        assertThat(sample.skinTemperatureC).isWithin(1e-9).of(33.25)
        assertThat(sample.motion).isWithin(1e-9).of(0.1)
        assertThat(sample.heaterTemperatureC).isWithin(1e-9).of(36.5)
        assertThat(sample.rrIntervalsMs).containsExactly(1024, 1040).inOrder()
    }

    @Test
    fun `values the sensor does not deliver stay unavailable`() {
        val packet = bytes(1, 0, 0xFF, 0xFF, 0xFF, 0xFF, 0x7F, 0xFF, 0xFF, 0x7F, 0)
        val sample = SockBleProtocol.parseSensor(packet, SockSide.RIGHT, now)!!

        assertThat(sample.heartRateBpm).isNull()
        assertThat(sample.hrvRmssdMs).isNull()
        assertThat(sample.spo2Percent).isNull()
        assertThat(sample.skinTemperatureC).isNull()
        assertThat(sample.motion).isNull()
        assertThat(sample.heaterTemperatureC).isNull()
        assertThat(sample.rrIntervalsMs).isEmpty()
    }

    @Test
    fun `negative temperatures and full motion are decoded`() {
        val packet = bytes(1, 60, 0, 0, 0xFF, 0x0C, 0xFE, 200, 0xFF, 0x7F, 0)
        val sample = SockBleProtocol.parseSensor(packet, SockSide.LEFT, now)!!
        assertThat(sample.skinTemperatureC).isWithin(1e-9).of(-5.0)
        assertThat(sample.motion).isEqualTo(1.0)
    }

    @Test
    fun `broken or unknown packets are rejected`() {
        assertThat(SockBleProtocol.parseSensor(bytes(1, 60, 0), SockSide.LEFT, now)).isNull()
        assertThat(SockBleProtocol.parseSensor(bytes(2, 60, 0, 0, 97, 0, 0, 0, 0, 0, 0), SockSide.LEFT, now)).isNull()
        // Angekündigt sind 3 RR-Intervalle, geliefert nur eines.
        assertThat(SockBleProtocol.parseSensor(bytes(1, 60, 0, 0, 97, 0, 0, 0, 0, 0, 3, 0, 4), SockSide.LEFT, now)).isNull()
        assertThat(SockBleProtocol.parseStatus(bytes(1, 1))).isNull()
        assertThat(SockBleProtocol.parseCapabilities(bytes(1))).isNull()
        assertThat(SockBleProtocol.parseBattery(bytes(101))).isNull()
        assertThat(SockBleProtocol.parseBattery(ByteArray(0))).isNull()
    }

    @Test
    fun `parses status, capabilities, battery and advertisement`() {
        val status = SockBleProtocol.parseStatus(bytes(1, 1, 3, 0x48, 0x0D, 1, 60, 0x01))!!
        assertThat(status.heating).isTrue()
        assertThat(status.heatLevel).isEqualTo(3)
        assertThat(status.targetTemperatureC).isWithin(1e-9).of(34.0)
        assertThat(status.massaging).isTrue()
        assertThat(status.massageIntensity).isEqualTo(60)
        assertThat(status.firmwareOverheatCutoff).isTrue()

        val capabilities = SockBleProtocol.parseCapabilities(bytes(1, 0b11011, 0b0101, 5))!!
        assertThat(capabilities.heartRate).isTrue()
        assertThat(capabilities.hrv).isTrue()
        assertThat(capabilities.spo2).isFalse()
        assertThat(capabilities.skinTemperature).isTrue()
        assertThat(capabilities.motion).isTrue()
        assertThat(capabilities.massageZones).containsExactly(MassageZone.HEEL, MassageZone.BALL)
        assertThat(capabilities.heatLevels).isEqualTo(5)

        assertThat(SockBleProtocol.parseBattery(bytes(87))).isEqualTo(87)
        assertThat(SockBleProtocol.parseAdvertisement(bytes(1, 1, 64))).isEqualTo(SockBleProtocol.Advertisement(SockSide.RIGHT, 64))
        assertThat(SockBleProtocol.parseAdvertisement(bytes(1, 0xFF, 0xFF))).isEqualTo(SockBleProtocol.Advertisement(null, null))
        assertThat(SockBleProtocol.parseAdvertisement(bytes(9, 0, 50))).isNull()
        assertThat(SockBleProtocol.parseAdvertisement(null)).isNull()
    }

    @Test
    fun `encodes heat and massage commands`() {
        assertThat(SockBleProtocol.encodeHeat(HeatCommand(3, 34.5))).isEqualTo(bytes(0x01, 3, 0x7A, 0x0D))
        assertThat(SockBleProtocol.encodeHeat(HeatCommand(2, null))).isEqualTo(bytes(0x01, 2, 0xFF, 0x7F))
        assertThat(SockBleProtocol.encodeHeatStop()).isEqualTo(bytes(0x02))
        assertThat(SockBleProtocol.encodeMassageStop(3_000)).isEqualTo(bytes(0x11, 30, 0))

        val pattern = MassagePattern(
            "test",
            listOf(
                MassageStep(1_200, mapOf(MassageZone.HEEL to 1f, MassageZone.TOES to 0.5f)),
                MassageStep(700, mapOf(MassageZone.ARCH to 0.4f)),
            ),
        )
        assertThat(SockBleProtocol.encodeMassage(MassageCommand(pattern, 60))).isEqualTo(
            bytes(0x10, 60, 2, 120, 0, 255, 0, 0, 128, 70, 0, 0, 102, 0, 0),
        )
    }

    @Test
    fun `long commands are split into numbered frames and reassembled`() {
        val payload = ByteArray(50) { it.toByte() }
        val frames = SockBleProtocol.frames(payload, mtu = 23)

        assertThat(frames).hasSize(3)
        assertThat(frames.all { it.size <= 20 }).isTrue()
        assertThat(frames.map { it[0].toInt() and 0xFF }).containsExactly(0x00, 0x01, 0x82).inOrder()
        assertThat(SockBleProtocol.reassemble(frames)).isEqualTo(payload)

        val single = SockBleProtocol.frames(bytes(0x02), mtu = 185)
        assertThat(single.single()).isEqualTo(bytes(0x80, 0x02))
        assertThat(SockBleProtocol.reassemble(listOf(bytes(0x01, 1)))).isNull()
    }

    @Test
    fun `firmware encoders round trip through the parsers`() {
        val sample = at.zocks.zleep.domain.model.SensorSample(SockSide.RIGHT, now, 61, 52.4, 96, 32.75, 0.35, 37.1, listOf(980, 1010))
        val parsed = SockBleProtocol.parseSensor(SockBleProtocol.encodeSensor(sample), SockSide.RIGHT, now)
        assertThat(parsed).isEqualTo(sample)

        val status = SockBleProtocol.Status(true, 4, 35.5, false, 0, false)
        assertThat(SockBleProtocol.parseStatus(SockBleProtocol.encodeStatus(status))).isEqualTo(status)

        val capabilities = at.zocks.zleep.testing.FullBleCapabilities
        assertThat(SockBleProtocol.parseCapabilities(SockBleProtocol.encodeCapabilities(capabilities))).isEqualTo(capabilities)

        val advertisement = SockBleProtocol.Advertisement(SockSide.LEFT, 77)
        assertThat(SockBleProtocol.parseAdvertisement(SockBleProtocol.encodeAdvertisement(advertisement))).isEqualTo(advertisement)
    }
}
