package at.zocks.zleep.ui

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.zocks.zleep.MainActivity
import at.zocks.zleep.R
import at.zocks.zleep.domain.alarm.AlarmMethod
import at.zocks.zleep.domain.model.NightSource
import at.zocks.zleep.domain.model.TemperatureUnit
import at.zocks.zleep.domain.model.SleepStage.AWAKE
import at.zocks.zleep.domain.model.SleepStage.LIGHT
import at.zocks.zleep.domain.repository.NightRepository
import at.zocks.zleep.domain.repository.SettingsRepository
import at.zocks.zleep.testing.UI_TIMEOUT_MS
import at.zocks.zleep.testing.minutes
import at.zocks.zleep.testing.testNightData
import at.zocks.zleep.testing.waitUntilDisplayed
import at.zocks.zleep.testing.waitUntilLoaded
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import javax.inject.Inject

/** Einstellungen: Einheiten, smarter Wecker, alle Nächte löschen, Hinweis „kein Medizinprodukt“. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
class SettingsFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var nights: NightRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        composeRule.waitUntilLoaded()
        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.waitUntilDisplayed("screen_settings")
        composeRule.waitUntilLoaded()
    }

    private fun waitForTag(tag: String) = composeRule.waitUntil(UI_TIMEOUT_MS) {
        composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun stored() = runBlocking { settings.settings.first() }

    @Test
    fun unitsAndAlarmArePersisted() {
        composeRule.onNodeWithTag("settings_unit_1").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_alarm_enabled").performScrollTo().performClick()
        waitForTag("settings_alarm_window_3")
        composeRule.onNodeWithTag("settings_alarm_window_3").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_alarm_method_sound").performScrollTo().performClick()
        // Die Oberfläche zeigt die Auswahl erst, wenn sie gespeichert ist.
        listOf("settings_unit_1", "settings_alarm_window_3", "settings_alarm_method_sound").forEach { tag ->
            composeRule.waitUntil(UI_TIMEOUT_MS) {
                composeRule.onAllNodes(hasTestTag(tag) and isSelected()).fetchSemanticsNodes().isNotEmpty()
            }
        }

        val result = stored()
        assertThat(result.temperatureUnit).isEqualTo(TemperatureUnit.FAHRENHEIT)
        assertThat(result.alarm.enabled).isTrue()
        assertThat(result.alarm.windowMinutes).isEqualTo(45)
        assertThat(result.alarm.method).isEqualTo(AlarmMethod.SOUND)
        composeRule.onNodeWithTag("wellness_notice").performScrollTo()
    }

    @Test
    fun deletingAllNightsNeedsConfirmation() {
        runBlocking {
            nights.insertCompleteNight(testNightData(listOf(AWAKE to minutes(10), LIGHT to minutes(60)), id = 0).let {
                it.copy(night = it.night.copy(source = NightSource.SIMULATOR))
            })
        }
        composeRule.onNodeWithTag("settings_delete").performScrollTo().performClick()
        waitForTag("confirm_delete")
        composeRule.onNodeWithTag("confirm_delete").performClick()
        val done = composeRule.activity.getString(R.string.delete_done)
        composeRule.waitUntil(UI_TIMEOUT_MS) { composeRule.onAllNodes(hasText(done)).fetchSemanticsNodes().isNotEmpty() }
        assertThat(runBlocking { nights.observeNights().first() }).isEmpty()
    }

    @Test
    fun healthConnectExplainsWhenItIsMissing() {
        composeRule.onNodeWithTag("settings_health_connect").performScrollTo()
        val missing = composeRule.activity.getString(R.string.hc_not_installed)
        val unsupported = composeRule.activity.getString(R.string.hc_not_supported)
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            composeRule.onAllNodes(hasText(missing, substring = true) or hasText(unsupported, substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
