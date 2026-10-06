package at.zocks.zleep.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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

/** Abläufe in der Steuerung: Heizen (inkl. Sicherheitsabschaltung) und Massage. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
class ControlFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
        composeRule.waitUntilLoaded()
        // Simulierte Socken verbinden und zur Steuerung wechseln.
        composeRule.onNodeWithTag("action_connect_socks").performScrollTo().performClick()
        waitForTag("action_disconnect_socks")
        composeRule.onNodeWithTag("nav_control").performClick()
        composeRule.waitUntilDisplayed("heat_panel")
    }

    private fun string(id: Int, vararg args: Any) = composeRule.activity.getString(id, *args)

    private fun waitForTag(tag: String) = composeRule.waitUntil(UI_TIMEOUT_MS) {
        composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForGone(tag: String) = composeRule.waitUntil(UI_TIMEOUT_MS) {
        composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty()
    }

    private fun waitForText(text: String) = composeRule.waitUntil(UI_TIMEOUT_MS) {
        composeRule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun heatingShowsTheBannerAndCanBeStoppedFromIt() {
        composeRule.onNodeWithTag("heat_start").performScrollTo().performClick()
        waitForTag("heating_banner")
        composeRule.onNodeWithTag("heating_banner").assertIsDisplayed()
        waitForTag("heat_stop")

        composeRule.onNodeWithTag("heating_banner_stop").performClick()
        waitForGone("heating_banner")
        waitForTag("heat_start")
    }

    @Test
    fun targetTemperatureCanBeSetWithBigButtons() {
        composeRule.onNodeWithTag("heat_mode_1").performScrollTo().performClick()
        waitForTag("heat_target_value")
        composeRule.onNodeWithTag("heat_target_increase").performScrollTo().performClick()
        composeRule.onNodeWithTag("heat_target_increase").performClick()
        // Standard 34,0 °C + 2 × 0,5 °C
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            composeRule.onAllNodes(hasTestTag("heat_target_value") and hasText("35", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun implausibleSensorValueSwitchesHeatingOff() {
        composeRule.onNodeWithTag("heat_start").performScrollTo().performClick()
        waitForTag("heating_banner")

        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.onNodeWithTag("settings_developer").performScrollTo().performClick()
        composeRule.waitUntilDisplayed("screen_developer")
        waitForTag("dev_fault_left")
        composeRule.onNodeWithTag("dev_fault_left").performScrollTo().performClick()

        // Meldung erscheint auf jedem Screen; die rechte Socke heizt weiter.
        val message = string(R.string.heat_notice_safety, string(R.string.sock_left), string(R.string.heat_reason_implausible))
        waitForText(message)
        composeRule.onNodeWithTag("heating_banner").assertIsDisplayed()
    }

    @Test
    fun massageStartsAndFadesOutOnStop() {
        composeRule.onNodeWithTag("control_tab_massage").performClick()
        composeRule.waitUntilDisplayed("massage_panel")
        composeRule.onNodeWithTag("massage_start").performScrollTo().performClick()
        waitForTag("massage_stop")

        composeRule.onNodeWithTag("massage_stop").performScrollTo().performClick()
        waitForGone("massage_stop")
        waitForText(string(R.string.massage_idle))
    }

    @Test
    fun favoritesAndCustomPrograms() {
        composeRule.onNodeWithTag("control_tab_massage").performClick()
        composeRule.waitUntilDisplayed("massage_panel")

        val wave = string(R.string.massage_program_wave)
        composeRule.onNodeWithTag("favorite_builtin:wave").performScrollTo().performClick()
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            composeRule.onAllNodes(hasContentDescription(string(R.string.massage_favorite_remove, wave)))
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("massage_open_editor").performScrollTo().performClick()
        waitForTag("custom_name")
        composeRule.onNodeWithTag("custom_name").performTextInput("Abendwelle")
        composeRule.onNodeWithTag("custom_save").performClick()
        waitForText(string(R.string.massage_custom_saved))
        waitForText("Abendwelle")

        composeRule.waitUntil(UI_TIMEOUT_MS) {
            composeRule.onAllNodes(hasContentDescription(string(R.string.massage_delete_custom, "Abendwelle")))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
