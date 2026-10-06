package at.zocks.zleep.testing

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Uhr, die Tests gezielt vorstellen können. */
class MutableClock(
    var now: Instant = Instant.parse("2026-10-05T18:00:00Z"),
    private val zoneId: ZoneId = ZoneId.of("Europe/Vienna"),
) : Clock() {
    override fun getZone(): ZoneId = zoneId
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    override fun instant(): Instant = now

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }
}
