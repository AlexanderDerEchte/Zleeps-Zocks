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
import at.zocks.zleep.domain.device.SockPairProvider
import at.zocks.zleep.domain.recording.NightRecorder
import at.zocks.zleep.domain.repository.SettingsRepository
import at.zocks.zleep.domain.routine.RoutineRunner
import at.zocks.zleep.domain.routine.RoutineState
import at.zocks.zleep.testing.SNACKBAR_GONE_MS
import at.zocks.zleep.testing.UI_TIMEOUT_MS
import at.zocks.zleep.testing.waitUntilDisplayed
import at.zocks.zleep.testing.waitUntilLoaded
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import javax.inject.Inject

/** Abendroutine zusammenstellen, starten, beenden und mit der Nacht mitstarten. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
class RoutineFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var runner: RoutineRunner

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var pairs: SockPairProvider

    @Inject lateinit var recorder: NightRecorder

    @Before
    fun setUp() {
        hiltRule.inject()
        composeRule.waitUntilLoaded()
    }

    @After
    fun tearDown() = runBlocking {
        runner.stop()
        recorder.stop()
    }

    private fun waitForTag(tag: String) = composeRule.waitUntil(UI_TIMEOUT_MS) {
        composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun openRoutine() {
        composeRule.onNodeWithTag("nav_control").performClick()
        composeRule.waitUntilDisplayed("screen_control")
        composeRule.onNodeWithTag("control_tab_routine").performClick()
        waitForTag("control_routine_content")
    }

    @Test
    fun editsWithoutSocksAndStartsOnceConnected() {
        openRoutine()
        // Zusammenstellen geht ohne Socken: Schritt hinzufügen und wieder entfernen.
        composeRule.onNodeWithTag("routine_add_step").performScrollTo().performClick()
        waitForTag("routine_step_3")
        composeRule.onNodeWithTag("routine_remove_3").performScrollTo().performClick()
        composeRule.waitUntil(UI_TIMEOUT_MS) { composeRule.onAllNodes(hasTestTag("routine_step_3")).fetchSemanticsNodes().isEmpty() }
        assertThat(runBlocking { settings.settings.first() }.routine.steps).hasSize(3)

        // Ohne Socken kein Start, sondern ein Hinweis.
        composeRule.onNodeWithTag("routine_start").performScrollTo().performClick()
        val pairFirst = composeRule.activity.getString(R.string.snackbar_pair_first)
        composeRule.waitUntil(UI_TIMEOUT_MS) { composeRule.onAllNodes(hasText(pairFirst)).fetchSemanticsNodes().size > 1 }

        runBlocking { pairs.pair.value.connect() }
        // Hinweis unter dem Knopf verschwindet mit der Verbindung, die Snackbar nach kurzer Zeit –
        // erst dann ist der Knopf frei.
        composeRule.mainClock.advanceTimeBy(SNACKBAR_GONE_MS)
        composeRule.waitUntil(UI_TIMEOUT_MS) { composeRule.onAllNodes(hasText(pairFirst)).fetchSemanticsNodes().isEmpty() }
        composeRule.onNodeWithTag("routine_start").performScrollTo().performClick()
        waitForTag("routine_running")
        assertThat(runner.state.value).isInstanceOf(RoutineState.Running::class.java)

        composeRule.onNodeWithTag("routine_stop").performScrollTo().performClick()
        composeRule.waitUntil(UI_TIMEOUT_MS) { composeRule.onAllNodes(hasTestTag("routine_running")).fetchSemanticsNodes().isEmpty() }
        assertThat(runner.state.value).isInstanceOf(RoutineState.Idle::class.java)
    }

    @Test
    fun startsTogetherWithTheNightWhenChosen() {
        composeRule.onNodeWithTag("action_connect_socks").performScrollTo().performClick()
        waitForTag("action_disconnect_socks")
        composeRule.onNodeWithTag("action_start_night").performScrollTo().performClick()
        composeRule.waitUntilDisplayed("night_start_sheet")
        composeRule.onNodeWithTag("start_routine").performScrollTo().performClick()
        composeRule.onNodeWithTag("start_recording").performScrollTo().performClick()
        composeRule.waitUntilDisplayed("screen_recording")

        composeRule.waitUntil(UI_TIMEOUT_MS) { runner.state.value is RoutineState.Running }
        // Gespeichert wird im Hintergrund; die Routine läuft schon vorher mit der gewählten Einstellung.
        val stored = runBlocking { withTimeoutOrNull(UI_TIMEOUT_MS) { settings.settings.first { it.routineWithNight } } }
        assertThat(stored).isNotNull()
    }
}
