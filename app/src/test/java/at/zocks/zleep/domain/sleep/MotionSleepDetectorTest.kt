package at.zocks.zleep.domain.sleep

import at.zocks.zleep.domain.model.SensorSample
import at.zocks.zleep.domain.model.SockSide
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class MotionSleepDetectorTest {

    private val start = Instant.parse("2026-10-05T21:00:00Z")
    private val detector = MotionSleepDetector()

    private fun feed(fromSecond: Long, seconds: Long, motion: (Long) -> Double?): Boolean {
        var asleep = false
        var t = fromSecond
        while (t < fromSecond + seconds) {
            asleep = detector.onSample(SensorSample(SockSide.LEFT, start.plusSeconds(t), 60, 40.0, 97, 33.0, motion(t), null))
            t += 5
        }
        return asleep
    }

    @Test
    fun `restless means awake`() {
        assertThat(feed(0, 1200) { 0.3 }).isFalse()
    }

    @Test
    fun `ten quiet minutes mean asleep`() {
        assertThat(feed(0, 300) { 0.3 }).isFalse()
        assertThat(feed(300, 5 * 60) { 0.01 }).isFalse()
        assertThat(feed(600, 6 * 60) { 0.01 }).isTrue()
    }

    @Test
    fun `a recent movement keeps the state awake`() {
        feed(0, 900) { 0.01 }
        assertThat(feed(900, 10) { 0.5 }).isFalse()
        assertThat(feed(910, 4 * 60) { 0.01 }).isFalse()
    }

    @Test
    fun `missing motion values are ignored`() {
        feed(0, 700) { 0.01 }
        assertThat(feed(700, 60) { null }).isTrue()
    }

    @Test
    fun `a time jump backwards starts over`() {
        feed(0, 900) { 0.01 }
        assertThat(detector.isAsleep).isTrue()
        assertThat(feed(-3600, 60) { 0.01 }).isFalse()
    }
}
