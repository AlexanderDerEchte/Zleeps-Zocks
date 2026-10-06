package at.zocks.zleep.testing

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Uhr, die mit der virtuellen Zeit von `runTest` läuft. */
@OptIn(ExperimentalCoroutinesApi::class)
class SchedulerClock(
    private val scheduler: TestCoroutineScheduler,
    private val base: Instant = Instant.parse("2026-10-05T19:30:00Z"),
    private val zoneId: ZoneId = ZoneId.of("Europe/Vienna"),
) : Clock() {
    override fun getZone(): ZoneId = zoneId
    override fun withZone(zone: ZoneId): Clock = SchedulerClock(scheduler, base, zone)
    override fun instant(): Instant = base.plusMillis(scheduler.currentTime)
}
