package at.zocks.zleep.domain.analysis

import at.zocks.zleep.domain.model.ConnectionGap
import at.zocks.zleep.domain.model.NightEvent
import at.zocks.zleep.domain.model.NightEventType
import at.zocks.zleep.domain.model.SleepStage
import at.zocks.zleep.domain.model.SleepStage.AWAKE
import at.zocks.zleep.domain.model.SleepStage.DEEP
import at.zocks.zleep.domain.model.SleepStage.LIGHT
import at.zocks.zleep.domain.model.SleepStage.REM
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.testing.TEST_NIGHT_START
import at.zocks.zleep.testing.minutes
import at.zocks.zleep.testing.testNightData
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset

class NightSummarizerTest {

    private val zone = ZoneOffset.UTC
    private val now = TEST_NIGHT_START.plus(Duration.ofDays(1))

    /** 20 min wach, 60 leicht, 60 tief, 3 wach, 30 REM, 30 s wach, 60 leicht, 10 wach. */
    private val plan = listOf(
        AWAKE to minutes(20),
        LIGHT to minutes(60),
        DEEP to minutes(60),
        AWAKE to minutes(3),
        REM to minutes(30),
        AWAKE to 1,
        LIGHT to minutes(60),
        AWAKE to minutes(10),
    )

    @Test
    fun `computes the key figures of a night`() {
        val data = testNightData(
            plan,
            heartRate = { stage -> if (stage == DEEP) 50.0 else if (stage == AWAKE) 70.0 else 56.0 },
            events = listOf(NightEvent(type = NightEventType.HEAT, side = SockSide.BOTH, start = TEST_NIGHT_START, end = null)),
            gaps = listOf(ConnectionGap(side = SockSide.LEFT, start = TEST_NIGHT_START, end = TEST_NIGHT_START.plusSeconds(300))),
        )
        val summary = NightSummarizer.summarize(data, zone, now)

        assertThat(summary.nightDate).isEqualTo(LocalDate.of(2026, 10, 5))
        assertThat(summary.sleepLatency).isEqualTo(Duration.ofMinutes(20))
        assertThat(summary.totalSleep).isEqualTo(Duration.ofMinutes(210))
        assertThat(summary.wakeAfterOnset).isEqualTo(Duration.ofSeconds(210))
        // Die 30-Sekunden-Wachphase zählt nicht als Aufwachen.
        assertThat(summary.awakenings).isEqualTo(1)
        assertThat(summary.timeInBed).isEqualTo(Duration.ofSeconds(243 * 60 + 30))
        assertThat(summary.efficiency!!).isWithin(0.001).of(210.0 / 243.5)
        assertThat(summary.stageMinutes).containsExactly(AWAKE, 34, LIGHT, 120, DEEP, 60, REM, 30)
        assertThat(summary.stageShare(DEEP)).isEqualTo(29)
        assertThat(summary.restorativeShare!!).isWithin(0.001).of(90.0 / 210)
        // Ruhepuls: ruhigster 5-Minuten-Abschnitt, beide Socken gemittelt (50 und 52).
        assertThat(summary.restingHeartRateBpm).isWithin(0.001).of(51.0)
        assertThat(summary.heatUsed).isTrue()
        assertThat(summary.massageUsed).isFalse()
        assertThat(summary.gapDuration).isEqualTo(Duration.ofMinutes(5))
    }

    @Test
    fun `without a sleep window nothing is guessed`() {
        val summary = NightSummarizer.summarize(testNightData(plan, withWindow = false), zone, now)

        assertThat(summary.totalSleep).isNull()
        assertThat(summary.sleepLatency).isNull()
        assertThat(summary.efficiency).isNull()
        assertThat(summary.wakeAfterOnset).isNull()
        assertThat(summary.awakenings).isNull()
        assertThat(summary.restingHeartRateBpm).isNull()
        // Durchschnittswerte über die ganze Nacht sind trotzdem bekannt.
        assertThat(summary.avgHeartRateBpm).isNotNull()
    }

    @Test
    fun `missing pulse stays unavailable`() {
        val summary = NightSummarizer.summarize(testNightData(plan, heartRate = { null }), zone, now)

        assertThat(summary.restingHeartRateBpm).isNull()
        assertThat(summary.avgHeartRateBpm).isNull()
        assertThat(summary.avgHrvRmssdMs).isNotNull()
    }

    @Test
    fun `resting pulse needs five minutes of sleep`() {
        val short = listOf(AWAKE to minutes(10), LIGHT to 9, AWAKE to minutes(10))
        assertThat(NightSummarizer.summarize(testNightData(short), zone, now).restingHeartRateBpm).isNull()

        val enough = listOf(AWAKE to minutes(10), LIGHT to 10, AWAKE to minutes(10))
        assertThat(NightSummarizer.summarize(testNightData(enough), zone, now).restingHeartRateBpm).isWithin(0.001).of(56.0)
    }

    @Test
    fun `a night belongs to the day it started on, shifted by twelve hours`() {
        val afterMidnight = testNightData(plan, start = TEST_NIGHT_START.plus(Duration.ofHours(5)))
        assertThat(NightSummarizer.summarize(afterMidnight, zone, now).nightDate).isEqualTo(LocalDate.of(2026, 10, 5))
    }

    @Test
    fun `stages without a stage list fall back to the window`() {
        val data = testNightData(plan).let { it.copy(stages = emptyList()) }
        val summary = NightSummarizer.summarize(data, zone, now)

        assertThat(summary.totalSleep).isEqualTo(Duration.between(data.night.sleepOnset, data.night.finalWake))
        assertThat(summary.wakeAfterOnset).isNull()
        assertThat(summary.stageMinutes).isEmpty()
        assertThat(summary.restorativeShare).isNull()
        assertThat(summary.stageShare(SleepStage.DEEP)).isNull()
    }
}
