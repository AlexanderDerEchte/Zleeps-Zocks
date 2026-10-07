package at.zocks.zleep.domain.analysis

import at.zocks.zleep.domain.model.NightSource
import at.zocks.zleep.domain.model.SleepStage.AWAKE
import at.zocks.zleep.domain.model.SleepStage.LIGHT
import at.zocks.zleep.testing.FakeNightRepository
import at.zocks.zleep.testing.FakeNightSummaryRepository
import at.zocks.zleep.testing.TEST_NIGHT_START
import at.zocks.zleep.testing.minutes
import at.zocks.zleep.testing.testNightData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset

class NightSummaryUpdaterTest {

    private val nights = FakeNightRepository()
    private val summaries = FakeNightSummaryRepository(nights)
    private val plan = listOf(AWAKE to minutes(10), LIGHT to minutes(60), AWAKE to minutes(5))

    private fun TestScope.updater() = NightSummaryUpdater(
        nights,
        summaries,
        Clock.fixed(TEST_NIGHT_START.plus(Duration.ofDays(1)), ZoneOffset.UTC),
        StandardTestDispatcher(testScheduler),
        backgroundScope,
    )

    @Test
    fun `fills in missing summaries of finished nights`() = runTest {
        val updater = updater()
        updater.start()
        updater.start() // zweiter Aufruf startet nichts doppelt

        val id = nights.insertCompleteNight(testNightData(plan, id = 0))
        val running = nights.startNight(TEST_NIGHT_START.plus(Duration.ofDays(1)), NightSource.SIMULATOR)
        runCurrent()
        assertThat(summaries.summaries.value).isEmpty()

        advanceTimeBy(1_500)
        runCurrent()
        assertThat(summaries.summaries.value.keys).containsExactly(id)
        assertThat(summaries.summaries.value.getValue(id).totalSleep).isEqualTo(Duration.ofMinutes(60))
        assertThat(summaries.summaries.value).doesNotContainKey(running)
    }

    @Test
    fun `refresh recalculates after a correction`() = runTest {
        val updater = updater()
        val id = nights.insertCompleteNight(testNightData(plan, id = 0))
        updater.refresh(id)
        val night = nights.night(id)!!
        nights.updateSleepWindow(id, night.sleepOnset!!.plus(Duration.ofMinutes(30)), night.finalWake, manual = true)

        updater.refresh(id)
        assertThat(summaries.summaries.value.getValue(id).totalSleep).isEqualTo(Duration.ofMinutes(30))
    }
}
