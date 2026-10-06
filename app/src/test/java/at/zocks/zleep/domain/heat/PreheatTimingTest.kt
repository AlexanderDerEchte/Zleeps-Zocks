package at.zocks.zleep.domain.heat

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class PreheatTimingTest {

    private val zone = ZoneId.of("Europe/Vienna")

    @Test
    fun `later today`() {
        val now = ZonedDateTime.of(2026, 10, 5, 18, 0, 0, 0, zone).toInstant()
        assertThat(PreheatTiming.nextOccurrence(LocalTime.of(22, 0), now, zone))
            .isEqualTo(ZonedDateTime.of(2026, 10, 5, 22, 0, 0, 0, zone).toInstant())
    }

    @Test
    fun `already passed means tomorrow`() {
        val now = ZonedDateTime.of(2026, 10, 5, 22, 0, 0, 0, zone).toInstant()
        assertThat(PreheatTiming.nextOccurrence(LocalTime.of(22, 0), now, zone))
            .isEqualTo(ZonedDateTime.of(2026, 10, 6, 22, 0, 0, 0, zone).toInstant())
    }

    @Test
    fun `daylight saving change keeps the local time`() {
        // In der Nacht auf den 25. Oktober 2026 wird die Uhr zurückgestellt.
        val now = ZonedDateTime.of(2026, 10, 24, 23, 0, 0, 0, zone).toInstant()
        val next = PreheatTiming.nextOccurrence(LocalTime.of(22, 0), now, zone)
        assertThat(next.atZone(zone).toLocalTime()).isEqualTo(LocalTime.of(22, 0))
        assertThat(next).isGreaterThan(Instant.parse("2026-10-25T00:00:00Z"))
    }
}
