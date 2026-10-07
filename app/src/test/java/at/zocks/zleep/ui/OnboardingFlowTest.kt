package at.zocks.zleep.ui

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.zocks.zleep.MainActivity
import at.zocks.zleep.domain.repository.SettingsRepository
import at.zocks.zleep.testing.TestOnboarding
import at.zocks.zleep.testing.UI_TIMEOUT_MS
import at.zocks.zleep.testing.waitUntilDisplayed
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import javax.inject.Inject

/** Erster Start: Erklärung, Socken koppeln, Schlafzeiten, danach die Startseite. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
class OnboardingFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    /** Muss vor dem Start der Activity greifen. */
    @get:Rule(order = 1)
    val onboarding = object : ExternalResource() {
        override fun before() {
            TestOnboarding.enabled = true
        }

        override fun after() {
            TestOnboarding.enabled = false
        }
    }

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var settings: SettingsRepository

    @Before
    fun setUp() = hiltRule.inject()

    private fun waitForTag(tag: String) = composeRule.waitUntil(UI_TIMEOUT_MS) {
        composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun walksThroughTheSetupAndLandsOnHome() {
        composeRule.waitUntilDisplayed("screen_onboarding")
        composeRule.onNodeWithTag("onboarding_next").performClick()

        waitForTag("onboarding_connect")
        composeRule.onNodeWithTag("onboarding_connect").performScrollTo().performClick()
        waitForTag("onboarding_paired")
        composeRule.onNodeWithTag("onboarding_next").performClick()

        waitForTag("onboarding_alarm")
        composeRule.onNodeWithTag("onboarding_goal_increase").performScrollTo().performClick()
        composeRule.onNodeWithTag("onboarding_alarm").performScrollTo().performClick()
        composeRule.onNodeWithTag("onboarding_finish").performClick()

        composeRule.waitUntilDisplayed("screen_home")
        val stored = runBlocking { settings.settings.first() }
        assertThat(stored.onboardingCompleted).isTrue()
        assertThat(stored.alarm.enabled).isTrue()
        // Standardziel 8 h, eine Viertelstunde mehr gewählt.
        assertThat(stored.sleepGoalMinutes).isEqualTo(8 * 60 + 15)
    }

    @Test
    fun setupCanBePostponed() {
        composeRule.waitUntilDisplayed("screen_onboarding")
        composeRule.onNodeWithTag("onboarding_later").performClick()
        composeRule.waitUntilDisplayed("screen_home")
        assertThat(runBlocking { settings.settings.first() }.onboardingCompleted).isTrue()
    }
}
