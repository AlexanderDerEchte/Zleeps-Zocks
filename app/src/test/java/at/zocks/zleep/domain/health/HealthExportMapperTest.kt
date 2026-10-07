package at.zocks.zleep.domain.health

import at.zocks.zleep.domain.model.NightSource
import at.zocks.zleep.domain.model.SleepStage
import at.zocks.zleep.domain.model.SleepStage.AWAKE
import at.zocks.zleep.domain.model.SleepStage.DEEP
import at.zocks.zleep.domain.model.SleepStage.LIGHT
import at.zocks.zleep.testing.TEST_NIGHT_START
import at.zocks.zleep.testing.minutes
import at.zocks.zleep.testing.testNightData
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration

class HealthExportMapperTest {

    private val plan = listOf(AWAKE to minutes(10), LIGHT to minutes(20), DEEP to minutes(10), AWAKE to minutes(5))

    @Test
    fun `maps a real night with merged stages and averaged pulse`() {
        val session = HealthExportMapper.map(testNightData(plan, id = 7))!!

        assertThat(session.id).isEqualTo("zocks-night-7")
        assertThat(session.start).isEqualTo(TEST_NIGHT_START)
        assertThat(session.end).isEqualTo(TEST_NIGHT_START.plus(Duration.ofMinutes(45)))
        assertThat(session.stages.map { it.stage }).containsExactly(AWAKE, LIGHT, DEEP, AWAKE).inOrder()
        assertThat(session.stages[1].start).isEqualTo(TEST_NIGHT_START.plus(Duration.ofMinutes(10)))
        assertThat(session.stages[1].end).isEqualTo(TEST_NIGHT_START.plus(Duration.ofMinutes(30)))
        // Pro Epoche ein Wert: links 55, rechts 57 → 56.
        assertThat(session.heartRate).hasSize(90)
        assertThat(session.heartRate[30].bpm).isEqualTo(56)
    }

    @Test
    fun `epochs without pulse are left out instead of guessed`() {
        val session = HealthExportMapper.map(testNightData(plan, heartRate = { if (it == SleepStage.DEEP) null else 60.0 }))!!
        assertThat(session.heartRate).hasSize(70)
    }

    @Test
    fun `demo, simulator and running nights are not exported`() {
        val data = testNightData(plan)
        assertThat(HealthExportMapper.map(data.copy(night = data.night.copy(source = NightSource.DEMO)))).isNull()
        assertThat(HealthExportMapper.map(data.copy(night = data.night.copy(source = NightSource.SIMULATOR)))).isNull()
        assertThat(HealthExportMapper.map(data.copy(night = data.night.copy(end = null)))).isNull()
        assertThat(HealthExportMapper.isExportable(data)).isTrue()
    }
}
