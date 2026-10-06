package at.zocks.zleep.ui.home

import at.zocks.zleep.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalTime

class GreetingTest {

    @Test
    fun `greeting follows the time of day`() {
        assertThat(greetingFor(LocalTime.of(6, 0))).isEqualTo(R.string.greeting_morning)
        assertThat(greetingFor(LocalTime.of(14, 30))).isEqualTo(R.string.greeting_day)
        assertThat(greetingFor(LocalTime.of(20, 15))).isEqualTo(R.string.greeting_evening)
        assertThat(greetingFor(LocalTime.of(23, 50))).isEqualTo(R.string.greeting_night)
        assertThat(greetingFor(LocalTime.of(3, 0))).isEqualTo(R.string.greeting_night)
    }
}
