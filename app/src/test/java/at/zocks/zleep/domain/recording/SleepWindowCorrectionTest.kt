package at.zocks.zleep.domain.recording

import at.zocks.zleep.domain.model.Night
import at.zocks.zleep.domain.model.NightSource
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class SleepWindowCorrectionTest {

    private val zone = ZoneId.of("Europe/Vienna")
    private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone).toInstant()

    private val night = Night(1, at(5, 22, 30), at(6, 7, 0), null, null, NightSource.DEVICE, null, emptyList())

    @Test
    fun `evening times stay on the evening, morning times move to the next day`() {
        assertThat(SleepWindowCorrection.resolve(LocalTime.of(23, 10), night, zone)).isEqualTo(at(5, 23, 10))
        assertThat(SleepWindowCorrection.resolve(LocalTime.of(0, 40), night, zone)).isEqualTo(at(6, 0, 40))
        assertThat(SleepWindowCorrection.resolve(LocalTime.of(6, 15), night, zone)).isEqualTo(at(6, 6, 15))
    }

    @Test
    fun `nights starting after midnight`() {
        val late = night.copy(start = at(6, 0, 30), end = at(6, 8, 0))
        assertThat(SleepWindowCorrection.resolve(LocalTime.of(1, 0), late, zone)).isEqualTo(at(6, 1, 0))
        assertThat(SleepWindowCorrection.resolve(LocalTime.of(23, 50), late, zone)).isEqualTo(at(5, 23, 50))
    }

    @Test
    fun `validity`() {
        assertThat(SleepWindowCorrection.isValid(at(5, 23, 0), at(6, 6, 30), night)).isTrue()
        assertThat(SleepWindowCorrection.isValid(at(6, 6, 30), at(5, 23, 0), night)).isFalse()
        assertThat(SleepWindowCorrection.isValid(at(5, 22, 0), at(6, 6, 30), night)).isFalse()
        assertThat(SleepWindowCorrection.isValid(at(5, 23, 0), at(6, 7, 30), night)).isFalse()
    }
}
