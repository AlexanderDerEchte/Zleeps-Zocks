package at.zocks.zleep.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.zocks.zleep.MainActivity
import at.zocks.zleep.domain.analysis.NightSummaryUpdater
import at.zocks.zleep.domain.model.SleepStage.AWAKE
import at.zocks.zleep.domain.model.SleepStage.DEEP
import at.zocks.zleep.domain.model.SleepStage.LIGHT
import at.zocks.zleep.domain.model.SleepStage.REM
import at.zocks.zleep.domain.model.Tag
import at.zocks.zleep.domain.repository.NightRepository
import at.zocks.zleep.domain.repository.TagRepository
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/** Nächte-Tab (Liste, Kalender, Trends, Erkenntnisse) und Bearbeiten einer Nacht. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
class NightsFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var nights: NightRepository

    @Inject lateinit var tags: TagRepository

    @Inject lateinit var summaries: NightSummaryUpdater

    private val zone = ZoneId.systemDefault()
    private val yesterday = LocalDate.now(zone).minusDays(1)

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    /**
     * Legt [count] Nächte bis gestern an. Die ersten [withCaffeine] tragen den Tag Koffein und
     * brauchen 40 statt 10 Minuten zum Einschlafen.
     */
    private fun insertNights(count: Int, withCaffeine: Int = 0) = runBlocking {
        val caffeine = tags.getByKey(Tag.CAFFEINE)!!
        repeat(count) { i ->
            val date = yesterday.minusDays(i.toLong())
            val tagged = i < withCaffeine
            val data = testNightData(
                plan = listOf(
                    AWAKE to minutes(if (tagged) 40 else 10),
                    LIGHT to minutes(150),
                    DEEP to minutes(90),
                    REM to minutes(90),
                    LIGHT to minutes(90),
                    AWAKE to minutes(10),
                ),
                id = 0,
                start = date.atTime(22, 30).atZone(zone).toInstant(),
                tags = if (tagged) listOf(caffeine) else emptyList(),
            )
            val id = nights.insertCompleteNight(data)
            summaries.save(data.copy(night = data.night.copy(id = id)))
        }
    }

    /** Wartet auf ein Test-Tag; im ungemergten Baum, damit auch Teile anklickbarer Karten zählen. */
    private fun waitForTag(tag: String) = waitFor(hasTestTag(tag), unmerged = true)

    private fun waitFor(matcher: SemanticsMatcher, unmerged: Boolean = false) = composeRule.waitUntil(UI_TIMEOUT_MS) {
        composeRule.onAllNodes(matcher, useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty()
    }

    private fun openNightsTab() {
        composeRule.waitUntilLoaded()
        composeRule.onNodeWithTag("nav_nights").performClick()
        composeRule.waitUntilDisplayed("screen_nights")
        composeRule.waitUntilLoaded()
    }

    @Test
    fun homeShowsTheScoreOfLastNight() {
        insertNights(1)
        composeRule.waitUntilLoaded()
        waitForTag("home_score")
    }

    @Test
    fun calendarOpensANight() {
        insertNights(3)
        openNightsTab()
        composeRule.onNodeWithTag("nights_view_1").performClick()
        composeRule.waitUntilDisplayed("nights_calendar")
        if (YearMonth.from(yesterday) != YearMonth.now(zone)) composeRule.onNodeWithTag("calendar_previous").performClick()

        val day = "calendar_day_$yesterday"
        composeRule.onNodeWithTag("screen_nights").performScrollToNode(hasTestTag(day))
        composeRule.onNodeWithTag(day).performClick()
        composeRule.waitUntilDisplayed("screen_night_detail")
        waitForTag("night_score")
    }

    @Test
    fun trendsShowChartsAndAssociations() {
        insertNights(16, withCaffeine = 6)
        openNightsTab()
        composeRule.onNodeWithTag("nights_view_2").performClick()
        waitForTag("trend_stats")
        composeRule.onNodeWithTag("screen_nights").performScrollToNode(hasTestTag("trend_charts"))
        composeRule.onAllNodes(hasTestTag("trend_chart")).onFirst().performScrollTo()

        composeRule.onNodeWithTag("trend_table_toggle").performScrollTo().performClick()
        waitForTag("trend_table")

        composeRule.onNodeWithTag("screen_nights").performScrollToNode(hasTestTag("insights"))
        waitForTag("insight_card")
    }

    @Test
    fun trendsExplainWhenThereAreTooFewNights() {
        insertNights(3)
        openNightsTab()
        composeRule.onNodeWithTag("nights_view_2").performClick()
        composeRule.onNodeWithTag("screen_nights").performScrollToNode(hasTestTag("insights"))
        waitForTag("insights_not_enough")
    }

    @Test
    fun tagsNotesAndTableInNightDetail() {
        insertNights(1)
        openNightsTab()
        composeRule.onAllNodes(hasTestTag("night_item")).onFirst().performClick()
        composeRule.waitUntilDisplayed("screen_night_detail")
        waitForTag("score_breakdown")
        val id = runBlocking { nights.observeNights().first().single().id }

        // Bedingungen über die Oberfläche prüfen: blockierende Datenbankabfragen in waitUntil
        // halten den Main-Thread auf, und die Speicherung käme erst nach dem Timeout dran.
        composeRule.onNodeWithTag("tag_chip_${Tag.CAFFEINE}").performScrollTo().performClick()
        waitFor(hasTestTag("tag_chip_${Tag.CAFFEINE}") and isSelected())

        composeRule.onNodeWithTag("action_add_tag").performScrollTo().performClick()
        waitForTag("tag_name_field")
        composeRule.onNodeWithTag("tag_name_field").performTextInput("Lesen")
        composeRule.onNodeWithTag("confirm_tag").performClick()
        waitFor(hasText("Lesen") and isSelected())

        composeRule.onNodeWithTag("note_field").performScrollTo().performTextInput("Spaziergang")
        composeRule.onNodeWithTag("action_save_note").performScrollTo().performClick()
        composeRule.waitUntil(UI_TIMEOUT_MS) { composeRule.onAllNodes(hasTestTag("action_save_note")).fetchSemanticsNodes().isEmpty() }

        val night = runBlocking { nights.observeNight(id).first()!! }
        assertThat(night.tags.map { it.key ?: it.label }).containsExactly(Tag.CAFFEINE, "Lesen")
        assertThat(night.note).isEqualTo("Spaziergang")

        composeRule.onNodeWithTag("chart_table_toggle").performScrollTo().performClick()
        waitForTag("chart_table")
    }
}
