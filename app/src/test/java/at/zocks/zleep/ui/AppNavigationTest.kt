package at.zocks.zleep.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.zocks.zleep.MainActivity
import at.zocks.zleep.R
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

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
class AppNavigationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
        composeRule.waitUntilLoaded()
    }

    @Test
    fun startsOnHome() {
        composeRule.waitUntilDisplayed("screen_home")
        composeRule.onNodeWithTag("nav_home").assertIsSelected()
    }

    @Test
    fun bottomBarSwitchesBetweenTopLevelScreens() {
        composeRule.onNodeWithTag("nav_nights").performClick()
        composeRule.waitUntilDisplayed("screen_nights")

        composeRule.onNodeWithTag("nav_control").performClick()
        composeRule.waitUntilDisplayed("screen_control")
        composeRule.onNodeWithTag("control_heat_content").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.onNodeWithTag("wellness_notice").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("nav_home").performClick()
        composeRule.waitUntilDisplayed("screen_home")
    }

    @Test
    fun massageQuickActionOpensMassageTab() {
        composeRule.onNodeWithTag("action_massage").performScrollTo().performClick()
        composeRule.waitUntilDisplayed("screen_control")
        composeRule.waitUntilDisplayed("control_massage_content")
        composeRule.onNodeWithTag("nav_control").assertIsSelected()
    }

    @Test
    fun startNightOpensTheChecklistAndNeedsConnectedSocks() {
        composeRule.onNodeWithTag("action_start_night").performScrollTo().performClick()
        composeRule.waitUntilDisplayed("night_start_sheet")
        composeRule.onNodeWithTag("start_recording").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun connectingSimulatedSocksShowsTheirStatus() {
        composeRule.onNodeWithTag("action_connect_socks").performScrollTo().performClick()
        // Beide Socken verbinden sich unabhängig voneinander – auf beide warten.
        val connected = composeRule.activity.getString(R.string.connection_connected)
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            composeRule.onAllNodes(hasText(connected, substring = true)).fetchSemanticsNodes().size == 2
        }
        composeRule.onNodeWithTag("action_disconnect_socks").assertExists()

        // Steuerung zeigt jetzt die Heizregler statt „nicht verbunden“.
        composeRule.onNodeWithTag("nav_control").performClick()
        composeRule.waitUntilDisplayed("heat_panel")
    }

}
