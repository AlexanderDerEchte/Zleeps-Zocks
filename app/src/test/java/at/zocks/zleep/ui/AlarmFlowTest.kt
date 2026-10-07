package at.zocks.zleep.ui

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.zocks.zleep.MainActivity
import at.zocks.zleep.domain.alarm.AlarmState
import at.zocks.zleep.domain.alarm.SmartAlarmController
import at.zocks.zleep.testing.UI_TIMEOUT_MS
import at.zocks.zleep.testing.waitUntilDisplayed
import at.zocks.zleep.testing.waitUntilLoaded
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import javax.inject.Inject

/** Der klingelnde Wecker liegt über allem und lässt sich ausschalten oder schlummern. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
class AlarmFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var alarm: SmartAlarmController

    @Before
    fun setUp() {
        hiltRule.inject()
        composeRule.waitUntilLoaded()
    }

    private fun ring() {
        // Wie der Systemwecker als Ausfallsicherung (App ohne laufende Nacht).
        runBlocking { alarm.onSystemAlarm() }
        composeRule.waitUntilDisplayed("alarm_overlay")
    }

    @Test
    fun dismissingTurnsTheAlarmOff() {
        ring()
        composeRule.onNodeWithTag("alarm_dismiss").performClick()
        composeRule.waitUntil(UI_TIMEOUT_MS) { composeRule.onAllNodes(hasTestTag("alarm_overlay")).fetchSemanticsNodes().isEmpty() }
        assertThat(alarm.state.value).isInstanceOf(AlarmState.Done::class.java)
        composeRule.waitUntilDisplayed("screen_home")
    }

    @Test
    fun snoozeHidesTheAlarmForNow() {
        ring()
        composeRule.onNodeWithTag("alarm_snooze").performClick()
        composeRule.waitUntil(UI_TIMEOUT_MS) { composeRule.onAllNodes(hasTestTag("alarm_overlay")).fetchSemanticsNodes().isEmpty() }
        assertThat(alarm.state.value).isInstanceOf(AlarmState.Snoozed::class.java)
    }
}
