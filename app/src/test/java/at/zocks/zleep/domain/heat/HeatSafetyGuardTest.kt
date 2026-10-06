package at.zocks.zleep.domain.heat

import at.zocks.zleep.domain.model.SensorSample
import at.zocks.zleep.domain.model.SockSide
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Duration
import java.time.Instant

class HeatSafetyGuardTest {

    private val guard = HeatSafetyGuard()
    private val start = Instant.parse("2026-10-05T20:00:00Z")

    private fun sample(seconds: Long, skin: Double?, heater: Double? = null, side: SockSide = SockSide.LEFT) = SensorSample(
        side = side,
        timestamp = start.plusSeconds(seconds),
        heartRateBpm = 60,
        hrvRmssdMs = 40.0,
        spo2Percent = 97,
        skinTemperatureC = skin,
        motion = 0.1,
        heaterTemperatureC = heater,
    )

    private fun heating() = guard.onHeatingStarted(SockSide.LEFT, start)

    @Test
    fun `target temperature is clamped to 20 to 40 degrees`() {
        assertThat(guard.clampTarget(45.0)).isEqualTo(40.0)
        assertThat(guard.clampTarget(12.0)).isEqualTo(20.0)
        assertThat(guard.clampTarget(33.5)).isEqualTo(33.5)
        assertThat(guard.clampTarget(null)).isNull()
    }

    @Test
    fun `no heating without a temperature sensor`() {
        assertThat(guard.checkStart(SockSide.LEFT, hasTemperatureSensor = false, start)).isEqualTo(HeatRejection.NO_TEMPERATURE_SENSOR)
        assertThat(guard.checkStart(SockSide.LEFT, hasTemperatureSensor = true, start)).isNull()
    }

    @Test
    fun `normal values while heating are fine`() {
        heating()
        listOf(31.0, 31.5, 32.4, 34.0, 36.0).forEachIndexed { i, temp ->
            assertThat(guard.onSample(sample(i * 5L, temp, heater = temp + 2), start.plusSeconds(i * 5L))).isNull()
        }
    }

    @Test
    fun `hard cutoff above 42 degrees on skin or heater`() {
        heating()
        assertThat(guard.onSample(sample(0, 41.9), start)).isNull()
        assertThat(guard.onSample(sample(15, 42.1), start.plusSeconds(15))).isEqualTo(ShutoffReason.OVER_TEMPERATURE)

        heating()
        assertThat(guard.onSample(sample(30, 38.0, heater = 42.5), start.plusSeconds(30))).isEqualTo(ShutoffReason.OVER_TEMPERATURE)
    }

    @Test
    fun `implausible values shut off`() {
        heating()
        assertThat(guard.onSample(sample(0, 9.5), start)).isEqualTo(ShutoffReason.IMPLAUSIBLE_LOW)
        heating()
        assertThat(guard.onSample(sample(5, 50.5), start)).isEqualTo(ShutoffReason.IMPLAUSIBLE_HIGH)
    }

    @Test
    fun `a jump of more than 3 degrees within 10 seconds is implausible`() {
        heating()
        assertThat(guard.onSample(sample(0, 33.0), start)).isNull()
        assertThat(guard.onSample(sample(5, 36.5), start.plusSeconds(5))).isEqualTo(ShutoffReason.IMPLAUSIBLE_JUMP)
    }

    @Test
    fun `the same change over a longer time is fine`() {
        heating()
        assertThat(guard.onSample(sample(0, 33.0), start)).isNull()
        assertThat(guard.onSample(sample(60, 36.5), start.plusSeconds(60))).isNull()
    }

    @Test
    fun `missing temperature for more than 30 seconds shuts off`() {
        heating()
        assertThat(guard.onSample(sample(0, 33.0), start)).isNull()
        assertThat(guard.onSample(sample(20, null), start.plusSeconds(20))).isNull()
        assertThat(guard.onSample(sample(35, null), start.plusSeconds(35))).isEqualTo(ShutoffReason.SENSOR_MISSING)
    }

    @Test
    fun `values outside heating are only remembered`() {
        assertThat(guard.onSample(sample(0, 55.0), start)).isNull()
        assertThat(guard.onSample(sample(5, 5.0), start)).isNull()
    }

    @Test
    fun `maximum runtime then cooldown`() {
        heating()
        assertThat(guard.checkRuntime(SockSide.LEFT, start.plus(Duration.ofMinutes(89)))).isNull()
        val end = start.plus(HeatSafetyGuard.MAX_CONTINUOUS)
        assertThat(guard.checkRuntime(SockSide.LEFT, end)).isEqualTo(ShutoffReason.MAX_RUNTIME)

        assertThat(guard.checkStart(SockSide.LEFT, true, end.plus(Duration.ofMinutes(10)))).isEqualTo(HeatRejection.COOLDOWN)
        assertThat(guard.cooldownUntil(SockSide.LEFT, end)).isEqualTo(end.plus(HeatSafetyGuard.COOLDOWN))
        assertThat(guard.checkStart(SockSide.LEFT, true, end.plus(Duration.ofMinutes(15)))).isNull()
        // Die andere Socke ist davon nicht betroffen.
        assertThat(guard.checkStart(SockSide.RIGHT, true, end)).isNull()
    }

    @Test
    fun `runtime counts from the first start, not from a level change`() {
        heating()
        guard.onHeatingStarted(SockSide.LEFT, start.plus(Duration.ofMinutes(60)))
        assertThat(guard.checkRuntime(SockSide.LEFT, start.plus(Duration.ofMinutes(90)))).isEqualTo(ShutoffReason.MAX_RUNTIME)
    }

    @Test
    fun `both is not a valid side for safety checks`() {
        assertThrows(IllegalArgumentException::class.java) { guard.onHeatingStarted(SockSide.BOTH, start) }
    }
}
