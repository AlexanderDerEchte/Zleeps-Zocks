package at.zocks.zleep.device.simulator

import at.zocks.zleep.domain.model.ConnectionGap
import at.zocks.zleep.domain.model.EPOCH_LENGTH
import at.zocks.zleep.domain.model.EpochMeasurement
import at.zocks.zleep.domain.model.Night
import at.zocks.zleep.domain.model.NightData
import at.zocks.zleep.domain.model.NightEvent
import at.zocks.zleep.domain.model.NightEventType
import at.zocks.zleep.domain.model.NightSource
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.domain.model.StageEpoch
import at.zocks.zleep.domain.model.Tag
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Erzeugt einen Demo-Datensatz, in dem Trends und Zusammenhänge sichtbar werden:
 * - Koffein, Stress und Bildschirmzeit verlängern das Einschlafen, Koffein senkt Tiefschlaf.
 * - Sport erhöht den Tiefschlaf, Alkohol senkt REM und erhöht den Ruhepuls.
 * - Wärme und Massage am Abend verkürzen das Einschlafen etwas.
 * - Am Wochenende später ins Bett und länger geschlafen.
 * Die Zusammenhänge sind bewusst eingebaut und nur für die Demo gedacht.
 */
class DemoNightsGenerator @Inject constructor(
    private val scenarioGenerator: NightScenarioGenerator,
) {

    fun generate(
        lastNight: LocalDate,
        count: Int,
        zone: ZoneId,
        tags: Map<String, Tag>,
        seed: Long = 42,
    ): List<NightData> = (0 until count).map { offset ->
        val date = lastNight.minusDays((count - 1 - offset).toLong())
        generateNight(date, zone, tags, Random(seed * 1_000 + offset))
    }

    private fun generateNight(date: LocalDate, zone: ZoneId, tags: Map<String, Tag>, random: Random): NightData {
        val weekend = date.dayOfWeek == DayOfWeek.FRIDAY || date.dayOfWeek == DayOfWeek.SATURDAY
        val caffeine = random.chance(0.25)
        val sport = random.chance(0.3)
        val alcohol = random.chance(if (weekend) 0.35 else 0.05)
        val stress = random.chance(0.2)
        val lateMeal = random.chance(0.15)
        val screen = random.chance(0.3)
        val heat = random.chance(0.4)
        val massage = random.chance(0.35)

        fun Boolean.of(value: Double) = if (this) value else 0.0

        val profile = NightProfile(
            sleepLatencyMinutes = (12 + random.nextGaussian(sd = 4.0) + caffeine.of(14.0) + stress.of(8.0) +
                screen.of(5.0) - heat.of(6.0) - massage.of(4.0)).coerceIn(3.0, 60.0),
            totalSleepMinutes = if (weekend) random.nextGaussian(470.0, 35.0) else random.nextGaussian(440.0, 30.0),
            restingHeartRate = 55 + caffeine.of(2.0) + alcohol.of(5.0) + stress.of(2.0) - sport.of(1.0) +
                random.nextGaussian(sd = 1.5),
            baseRmssdMs = 48 - alcohol.of(10.0) - stress.of(6.0) + sport.of(3.0) + random.nextGaussian(sd = 3.0),
            deepFactor = 1 + sport.of(0.18) - caffeine.of(0.2) - alcohol.of(0.1) + random.nextGaussian(sd = 0.08),
            remFactor = 1 - alcohol.of(0.35) - stress.of(0.1) + random.nextGaussian(sd = 0.08),
            extraAwakenings = random.nextInt(0, 2) + (if (alcohol) 2 else 0) + (if (stress) 1 else 0) + (if (lateMeal) 1 else 0),
            baseSkinTemperatureC = 30.8 + random.nextGaussian(sd = 0.3),
        )

        val bedtimeMinutes = if (weekend) random.nextGaussian(23 * 60 + 40.0, 40.0) else random.nextGaussian(22 * 60 + 40.0, 25.0)
        val start = date.atTime(LocalTime.of(0, 0)).plusMinutes(bedtimeMinutes.roundToInt().toLong()).atZone(zone).toInstant()
        val scenario = scenarioGenerator.generate(start, profile, random)

        val heatEnd = start.plus(Duration.ofMinutes(random.nextLong(20, 36)))
        val events = buildList {
            if (heat) add(NightEvent(type = NightEventType.HEAT, side = SockSide.BOTH, start = start, end = heatEnd, detail = "level=3"))
            if (massage) {
                val massageStart = start.plus(Duration.ofMinutes(random.nextLong(2, 8)))
                add(
                    NightEvent(
                        type = NightEventType.MASSAGE,
                        side = SockSide.BOTH,
                        start = massageStart,
                        end = massageStart.plus(Duration.ofMinutes(random.nextLong(10, 16))),
                        detail = "program=relax",
                    ),
                )
            }
        }

        // Gelegentlich ein kurzer Verbindungsabbruch an einer Socke.
        val gap = if (random.chance(0.15)) {
            val side = if (random.nextBoolean()) SockSide.LEFT else SockSide.RIGHT
            val gapStart = scenario.epochStart(random.nextInt(scenario.epochs.size / 4, scenario.epochs.size * 3 / 4))
            ConnectionGap(side = side, start = gapStart, end = gapStart.plus(Duration.ofMinutes(random.nextLong(2, 7))))
        } else {
            null
        }

        val measurements = scenario.epochs.flatMapIndexed { index, physiology ->
            val epochStart = scenario.epochStart(index)
            listOf(SockSide.LEFT, SockSide.RIGHT).mapNotNull { side ->
                if (gap != null && gap.side == side && !epochStart.isBefore(gap.start) && epochStart.isBefore(gap.end)) {
                    return@mapNotNull null
                }
                val heated = heat && epochStart.isBefore(heatEnd)
                val sideOffset = if (side == SockSide.LEFT) 0.1 else -0.1
                EpochMeasurement(
                    side = side,
                    start = epochStart,
                    heartRateBpm = physiology.heartRateBpm + random.nextGaussian(sd = 0.6),
                    hrvRmssdMs = physiology.hrvRmssdMs * (1 + random.nextGaussian(sd = 0.03)),
                    spo2Percent = physiology.spo2Percent + random.nextGaussian(sd = 0.2),
                    skinTemperatureC = physiology.skinTemperatureC + sideOffset + (if (heated) 2.4 else 0.0) +
                        random.nextGaussian(sd = 0.03),
                    motion = (physiology.motion + random.nextGaussian(sd = 0.004)).coerceIn(0.0, 1.0),
                    sampleCount = SAMPLES_PER_EPOCH,
                )
            }
        }

        val nightTags = listOfNotNull(
            tags[Tag.CAFFEINE].takeIf { caffeine },
            tags[Tag.SPORT].takeIf { sport },
            tags[Tag.ALCOHOL].takeIf { alcohol },
            tags[Tag.STRESS].takeIf { stress },
            tags[Tag.LATE_MEAL].takeIf { lateMeal },
            tags[Tag.SCREEN_TIME].takeIf { screen },
        )

        return NightData(
            night = Night(
                id = 0,
                start = start,
                end = scenario.end,
                sleepOnset = scenario.sleepOnset,
                finalWake = scenario.finalWake,
                source = NightSource.DEMO,
                note = null,
                tags = nightTags,
            ),
            measurements = measurements,
            stages = scenario.epochs.mapIndexed { index, physiology -> StageEpoch(scenario.epochStart(index), physiology.stage) },
            events = events,
            gaps = listOfNotNull(gap),
        )
    }

    private companion object {
        val SAMPLES_PER_EPOCH = (EPOCH_LENGTH.seconds / SimulationEngine.STEP.seconds).toInt()
    }
}
