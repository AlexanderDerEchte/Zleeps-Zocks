package at.zocks.zleep.domain.export

import at.zocks.zleep.domain.analysis.NightSummarizer
import at.zocks.zleep.domain.model.SleepStage.AWAKE
import at.zocks.zleep.domain.model.SleepStage.LIGHT
import at.zocks.zleep.domain.model.Tag
import at.zocks.zleep.testing.FakeNightRepository
import at.zocks.zleep.testing.FakeNightSummaryRepository
import at.zocks.zleep.testing.FakeSettingsRepository
import at.zocks.zleep.testing.TEST_NIGHT_START
import at.zocks.zleep.testing.minutes
import at.zocks.zleep.testing.testNightData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Duration
import java.time.ZoneOffset

class CsvExportTest {

    private val zone = ZoneOffset.UTC

    @Test
    fun `escapes separators, quotes and line breaks`() {
        assertThat(CsvFormat.escape("ruhig")).isEqualTo("ruhig")
        assertThat(CsvFormat.escape("Koffein, Sport")).isEqualTo("\"Koffein, Sport\"")
        assertThat(CsvFormat.escape("sagte \"gut\"")).isEqualTo("\"sagte \"\"gut\"\"\"")
        assertThat(CsvFormat.escape("a\nb")).isEqualTo("\"a\nb\"")
        assertThat(CsvFormat.row(listOf("a", null, "c"))).isEqualTo("a,,c\r\n")
    }

    @Test
    fun `a night row has one field per column and leaves missing values empty`() {
        val data = testNightData(listOf(AWAKE to minutes(10), LIGHT to minutes(60), AWAKE to minutes(5)), heartRate = { null })
        val summary = NightSummarizer.summarize(data, zone, TEST_NIGHT_START.plus(Duration.ofDays(1)))
        val row = CsvFormat.nightRow(data.night.copy(note = "müde, aber ok"), summary, Duration.ofHours(8), listOf("Koffein"), zone)
        val columns = CsvFormat.NIGHT_COLUMNS
        assertThat(row).hasSize(columns.size)
        fun field(name: String) = row[columns.indexOf(name)]
        assertThat(field("night_date")).isEqualTo("2026-10-05")
        assertThat(field("start")).isEqualTo("2026-10-05T20:30:00Z")
        assertThat(field("total_sleep_min")).isEqualTo("60")
        assertThat(field("resting_hr_bpm")).isNull()
        assertThat(field("avg_skin_temp_c")).isEqualTo("33.20")
        assertThat(field("tags")).isEqualTo("Koffein")
        assertThat(CsvFormat.row(row)).contains("\"müde, aber ok\"")
    }

    @Test
    fun `measurement rows carry the stage of their epoch`() {
        val data = testNightData(listOf(AWAKE to 1, LIGHT to 1))
        val rows = CsvFormat.measurementRows(data, zone)
        assertThat(rows).hasSize(4)
        assertThat(rows.map { it[2] to it[3] }).containsExactly("left" to "awake", "right" to "awake", "left" to "light", "right" to "light").inOrder()
        assertThat(rows.first()[4]).isEqualTo("70.0")
        assertThat(rows[1][4]).isEqualTo("72.0")
    }

    @Test
    fun `exporter writes all finished nights with translated tags`() = runTest {
        val nights = FakeNightRepository()
        val summaries = FakeNightSummaryRepository(nights)
        val plan = listOf(AWAKE to minutes(10), LIGHT to minutes(60), AWAKE to minutes(5))
        val id = nights.insertCompleteNight(testNightData(plan, id = 0, tags = listOf(Tag(1, Tag.SPORT, null))))
        nights.startNight(TEST_NIGHT_START.plus(Duration.ofDays(1)), at.zocks.zleep.domain.model.NightSource.DEVICE)
        summaries.save(NightSummarizer.summarize(nights.getNightData(id)!!, zone, TEST_NIGHT_START.plus(Duration.ofDays(1))))
        val exporter = CsvExporter(nights, summaries, FakeSettingsRepository())

        val out = StringBuilder()
        assertThat(exporter.writeNights(out, zone) { tag -> if (tag.key == Tag.SPORT) "Sport" else "?" }).isEqualTo(1)
        val lines = out.lines().filter { it.isNotBlank() }
        assertThat(lines.first()).startsWith("night_date,start,end")
        assertThat(lines[1]).contains(",Sport,")

        val measurements = StringBuilder()
        assertThat(exporter.writeMeasurements(measurements, zone)).isEqualTo(1)
        assertThat(measurements.lines().count { it.isNotBlank() }).isEqualTo(1 + 75 * 2 * 2)
    }
}
