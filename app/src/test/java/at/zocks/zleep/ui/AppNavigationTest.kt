package at.zocks.zleep.ui

import androidx.compose.ui.test.assertIsDisplayed
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
        composeRule.onNodeWithTag("screen_home").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_home").assertIsSelected()
    }

    @Test
    fun bottomBarSwitchesBetweenTopLevelScreens() {
        composeRule.onNodeWithTag("nav_nights").performClick()
        composeRule.onNodeWithTag("screen_nights").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_control").performClick()
        composeRule.onNodeWithTag("screen_control").assertIsDisplayed()
        composeRule.onNodeWithTag("control_heat_content").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.onNodeWithTag("wellness_notice").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("nav_home").performClick()
        composeRule.onNodeWithTag("screen_home").assertIsDisplayed()
    }

    @Test
    fun massageQuickActionOpensMassageTab() {
        composeRule.onNodeWithTag("action_massage").performScrollTo().performClick()
        composeRule.onNodeWithTag("screen_control").assertIsDisplayed()
        composeRule.onNodeWithTag("control_massage_content").assertIsDisplayed()
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

    private companion object {
        const val SNACKBAR_ANIMATION_MS = 1_000L
    }
}
