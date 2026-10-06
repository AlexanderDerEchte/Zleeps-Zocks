package at.zocks.zleep.ui

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.zocks.zleep.MainActivity
import at.zocks.zleep.R
import at.zocks.zleep.domain.simulator.SimulatorController
import at.zocks.zleep.testing.UI_TIMEOUT_MS
import at.zocks.zleep.testing.waitUntilDisplayed
import at.zocks.zleep.testing.waitUntilLoaded
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import javax.inject.Inject

/** Ablauf: Nacht starten → Nachtmodus → Schlaf erkannt → Nacht beenden → Nachtdetail. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
class RecordingFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var simulator: SimulatorController

    @Before
    fun setUp() {
        hiltRule.inject()
        composeRule.waitUntilLoaded()
        composeRule.onNodeWithTag("action_connect_socks").performScrollTo().performClick()
        waitForTag("action_disconnect_socks")
    }

    private fun waitForTag(tag: String, timeout: Long = UI_TIMEOUT_MS) = composeRule.waitUntil(timeout) {
        composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun startNight() {
        composeRule.onNodeWithTag("action_start_night").performScrollTo().performClick()
        composeRule.waitUntilDisplayed("night_start_sheet")
        composeRule.onNodeWithTag("start_recording").performScrollTo().performClick()
        composeRule.waitUntilDisplayed("screen_recording")
        waitForTag("recording_clock")
    }

    @Test
    fun nightModeHidesTheBottomBarAndHomeShowsTheRecording() {
        startNight()
        composeRule.waitUntil(UI_TIMEOUT_MS) { composeRule.onAllNodes(hasTestTag("nav_home")).fetchSemanticsNodes().isEmpty() }

        composeRule.onNodeWithTag("action_back").performClick()
        composeRule.waitUntilDisplayed("recording_card")

        // Sofort wieder beendet: zu kurz, wird verworfen.
        composeRule.onNodeWithTag("stop_recording").performScrollTo().performClick()
        val discarded = composeRule.activity.getString(R.string.recording_discarded)
        composeRule.waitUntil(UI_TIMEOUT_MS) { composeRule.onAllNodes(hasText(discarded)).fetchSemanticsNodes().isNotEmpty() }
        waitForTag("action_start_night")
    }

    @Test
    fun sleepIsDetectedLiveAndEndingTheNightOpensIt() {
        startNight()
        // Zeitraffer: 1 h Nacht in einer Sekunde. Das automatische Beenden am Morgen prüft
        // NightRecorderTest in virtueller Zeit.
        simulator.playNight(speed = 3600)

        val asleep = composeRule.activity.getString(R.string.recording_asleep_since, "").trim()
        composeRule.waitUntil(LONG_TIMEOUT_MS) {
            composeRule.onAllNodes(hasText(asleep, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("night_mode_stop").performClick()
        waitForTag("confirm_stop")
        composeRule.onNodeWithTag("confirm_stop").performClick()
        composeRule.waitUntilDisplayed("screen_night_detail", LONG_TIMEOUT_MS)
        waitForTag("night_duration", LONG_TIMEOUT_MS)
        simulator.stopNight()
    }

    private companion object {
        const val LONG_TIMEOUT_MS = 120_000L
    }
}
