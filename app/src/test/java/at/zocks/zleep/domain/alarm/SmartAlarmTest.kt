package at.zocks.zleep.domain.alarm

import at.zocks.zleep.domain.model.SleepStage
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class SmartAlarmTest {

    private val zone = ZoneId.of("Europe/Vienna")
    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant()

    @Test
    fun `window ends at the next wake time after the night started`() {
        val window = SmartAlarmPlanner.window(at("2026-10-05T22:30"), LocalTime.of(6, 45), 30, zone)
        assertThat(window).isEqualTo(WakeWindow(at("2026-10-06T06:15"), at("2026-10-06T06:45")))
    }

    @Test
    fun `a night after midnight wakes the same morning, a late start the next day`() {
        assertThat(SmartAlarmPlanner.window(at("2026-10-06T01:30"), LocalTime.of(6, 45), 30, zone).end)
            .isEqualTo(at("2026-10-06T06:45"))
        // Weniger als eine Stunde bis zur Aufstehzeit: erst am nächsten Tag.
        assertThat(SmartAlarmPlanner.window(at("2026-10-06T06:00"), LocalTime.of(6, 45), 30, zone).end)
            .isEqualTo(at("2026-10-07T06:45"))
        // Das Fenster beginnt nie vor der Nacht.
        assertThat(SmartAlarmPlanner.window(at("2026-10-06T05:30"), LocalTime.of(6, 45), 90, zone).start)
            .isEqualTo(at("2026-10-06T05:30"))
    }

    @Test
    fun `window handles the switch to winter time`() {
        // In der Nacht auf den 25.10.2026 wird die Uhr um 3 Uhr auf 2 Uhr zurückgestellt.
        val window = SmartAlarmPlanner.window(at("2026-10-24T22:00"), LocalTime.of(6, 45), 30, zone)
        assertThat(window.end).isEqualTo(java.time.Instant.parse("2026-10-25T05:45:00Z"))
        assertThat(Duration.between(window.start, window.end)).isEqualTo(Duration.ofMinutes(30))
    }

    private val window = WakeWindow(at("2026-10-06T06:15"), at("2026-10-06T06:45"))

    @Test
    fun `waits before the window and in deep or rem sleep`() {
        assertThat(SmartAlarmDecider.decide(at("2026-10-06T06:14"), window, SleepStage.LIGHT, at("2026-10-06T06:14"))).isNull()
        assertThat(SmartAlarmDecider.decide(at("2026-10-06T06:20"), window, SleepStage.DEEP, at("2026-10-06T06:20"))).isNull()
        assertThat(SmartAlarmDecider.decide(at("2026-10-06T06:20"), window, SleepStage.REM, at("2026-10-06T06:20"))).isNull()
        assertThat(SmartAlarmDecider.decide(at("2026-10-06T06:20"), window, null, null)).isNull()
    }

    @Test
    fun `wakes in light sleep or when already awake inside the window`() {
        assertThat(SmartAlarmDecider.decide(at("2026-10-06T06:15"), window, SleepStage.LIGHT, at("2026-10-06T06:14")))
            .isEqualTo(WakeReason.LIGHT_SLEEP)
        assertThat(SmartAlarmDecider.decide(at("2026-10-06T06:30"), window, SleepStage.AWAKE, at("2026-10-06T06:29")))
            .isEqualTo(WakeReason.LIGHT_SLEEP)
    }

    @Test
    fun `an outdated stage does not count`() {
        val now = at("2026-10-06T06:30")
        assertThat(SmartAlarmDecider.decide(now, window, SleepStage.LIGHT, now.minus(Duration.ofMinutes(3)))).isEqualTo(WakeReason.LIGHT_SLEEP)
        assertThat(SmartAlarmDecider.decide(now, window, SleepStage.LIGHT, now.minus(Duration.ofSeconds(181)))).isNull()
    }

    @Test
    fun `wakes at the end of the window in any case`() {
        assertThat(SmartAlarmDecider.decide(at("2026-10-06T06:44:59"), window, SleepStage.DEEP, at("2026-10-06T06:44"))).isNull()
        assertThat(SmartAlarmDecider.decide(at("2026-10-06T06:45"), window, SleepStage.DEEP, at("2026-10-06T06:44"))).isEqualTo(WakeReason.DEADLINE)
        assertThat(window.contains(at("2026-10-06T06:45"))).isFalse()
        assertThat(window.contains(at("2026-10-06T06:15"))).isTrue()
    }
}
