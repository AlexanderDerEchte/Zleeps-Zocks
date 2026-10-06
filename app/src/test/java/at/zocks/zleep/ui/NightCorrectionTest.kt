package at.zocks.zleep.ui

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.zocks.zleep.MainActivity
import at.zocks.zleep.R
import at.zocks.zleep.domain.model.EPOCH_LENGTH
import at.zocks.zleep.domain.model.Night
import at.zocks.zleep.domain.model.NightData
import at.zocks.zleep.domain.model.NightSource
import at.zocks.zleep.domain.model.SleepStage
import at.zocks.zleep.domain.model.StageEpoch
import at.zocks.zleep.domain.repository.NightRepository
import at.zocks.zleep.testing.UI_TIMEOUT_MS
import at.zocks.zleep.testing.waitUntilDisplayed
import at.zocks.zleep.testing.waitUntilLoaded
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.ZonedDateTime
import java.time.ZoneId
import javax.inject.Inject

/** Einschlafzeit von Hand korrigieren und die Korrektur wieder aufheben. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
class NightCorrectionTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var nights: NightRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        val start = ZonedDateTime.of(2026, 10, 4, 22, 30, 0, 0, ZoneId.systemDefault()).toInstant()
        val stages = List(16 * 60) { i ->
            StageEpoch(start.plus(EPOCH_LENGTH.multipliedBy(i.toLong())), if (i < 30 || i > 900) SleepStage.AWAKE else SleepStage.LIGHT)
        }
        runBlocking {
            nights.insertCompleteNight(
                NightData(
                    Night(0, start, start.plus(Duration.ofHours(8)), start.plusSeconds(900), start.plusSeconds(27_000), NightSource.SIMULATOR, null, emptyList()),
                    emptyList(),
                    stages,
                    emptyList(),
                    emptyList(),
                ),
            )
        }
        composeRule.waitUntilLoaded()
    }

    private fun waitForTag(tag: String) = composeRule.waitUntil(UI_TIMEOUT_MS) {
        composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun correctAndResetTheSleepWindow() {
        composeRule.onNodeWithTag("card_last_night").performClick()
        composeRule.waitUntilDisplayed("screen_night_detail")
        waitForTag("edit_onset")

        // Uhr-Dialog öffnet mit der bisherigen Zeit; Speichern übernimmt sie als Korrektur.
        composeRule.onNodeWithTag("edit_onset").performClick()
        waitForTag("confirm_time")
        composeRule.onNodeWithTag("confirm_time").performClick()
        val manual = composeRule.activity.getString(R.string.night_window_manual)
        composeRule.waitUntil(UI_TIMEOUT_MS) { composeRule.onAllNodes(hasText(manual)).fetchSemanticsNodes().isNotEmpty() }

        composeRule.onNodeWithTag("reset_window").performClick()
        composeRule.waitUntil(UI_TIMEOUT_MS) { composeRule.onAllNodes(hasText(manual)).fetchSemanticsNodes().isEmpty() }
    }
}
