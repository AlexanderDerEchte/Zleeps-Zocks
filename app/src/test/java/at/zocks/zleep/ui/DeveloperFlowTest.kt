package at.zocks.zleep.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.zocks.zleep.MainActivity
import at.zocks.zleep.R
import at.zocks.zleep.testing.waitUntilDisplayed
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Ablauf: Entwickleroptionen → Demo-Daten laden → Nächte ansehen → Nacht öffnen. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
class DeveloperFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private fun waitForTag(tag: String) = composeRule.waitUntil(TIMEOUT_MS) {
        composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun loadDemoDataAndOpenANight() {
        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.onNodeWithTag("settings_developer").performScrollTo().performClick()
        composeRule.waitUntilDisplayed("screen_developer")
        composeRule.onNodeWithTag("nav_settings").assertIsSelected()

        composeRule.onNodeWithTag("dev_demo_load").performScrollTo().performClick()
        val loaded = composeRule.activity.resources.getQuantityString(R.plurals.dev_demo_count, 30, 30)
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodes(hasText(loaded)).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("nav_nights").performClick()
        waitForTag("night_item")
        composeRule.onAllNodesWithTag("night_item").onFirst().performClick()

        composeRule.waitUntilDisplayed("screen_night_detail")
        waitForTag("night_duration")
        composeRule.onNodeWithTag("night_duration").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_nights").assertIsSelected()

        composeRule.onNodeWithTag("action_back").performClick()
        composeRule.waitUntilDisplayed("screen_nights")

        // Startseite zeigt jetzt die letzte Nacht.
        composeRule.onNodeWithTag("nav_home").performClick()
        waitForTag("card_last_night")
    }

    @Test
    fun switchingToRealDevicesHidesTheSimulator() {
        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.onNodeWithTag("settings_developer").performScrollTo().performClick()
        waitForTag("dev_play_night")

        composeRule.onNodeWithTag("dev_mode_ble").performClick()
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodes(hasTestTag("dev_play_night")).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("dev_mode_ble").assertIsSelected()
    }

    private companion object {
        const val TIMEOUT_MS = 20_000L
    }
}
