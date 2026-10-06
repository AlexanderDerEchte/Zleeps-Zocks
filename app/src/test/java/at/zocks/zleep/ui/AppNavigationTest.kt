package at.zocks.zleep.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.zocks.zleep.MainActivity
import at.zocks.zleep.R
import at.zocks.zleep.testing.waitUntilDisplayed
import com.google.common.truth.Truth.assertThat
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
    fun startNightWithoutSocksAsksToPairFirst() {
        val message = composeRule.activity.getString(R.string.snackbar_pair_first)
        composeRule.onNodeWithTag("action_start_night").performScrollTo().performClick()
        composeRule.waitUntil { composeRule.onAllNodes(hasText(message)).fetchSemanticsNodes().isNotEmpty() }
        composeRule.mainClock.advanceTimeBy(SNACKBAR_ANIMATION_MS)
        composeRule.onNodeWithText(message).assertIsDisplayed()
    }

    @Test
    fun connectingSimulatedSocksShowsTheirStatus() {
        composeRule.onNodeWithTag("action_connect_socks").performScrollTo().performClick()
        composeRule.waitUntil(CONNECT_TIMEOUT_MS) {
            composeRule.onAllNodes(hasTestTag("action_disconnect_socks")).fetchSemanticsNodes().isNotEmpty()
        }
        val connected = composeRule.activity.getString(R.string.connection_connected)
        composeRule.onAllNodes(hasText(connected, substring = true)).fetchSemanticsNodes().let {
            assertThat(it).hasSize(2)
        }

        // Steuerung zeigt jetzt den verbundenen Zustand statt „nicht verbunden“.
        composeRule.onNodeWithTag("nav_control").performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.control_connected_title)).assertIsDisplayed()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000L
        const val SNACKBAR_ANIMATION_MS = 1_000L
    }
}
