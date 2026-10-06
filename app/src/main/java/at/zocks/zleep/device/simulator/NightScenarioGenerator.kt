package at.zocks.zleep.device.simulator

import at.zocks.zleep.domain.model.EPOCH_LENGTH
import at.zocks.zleep.domain.model.SleepStage
import java.time.Instant
import javax.inject.Inject
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.random.Random

/** Stellschrauben einer simulierten Nacht. Standardwerte entsprechen einer gesunden, erholsamen Nacht. */
data class NightProfile(
    val sleepLatencyMinutes: Double = 15.0,
    val totalSleepMinutes: Double = 450.0,
    val restingHeartRate: Double = 56.0,
    val baseRmssdMs: Double = 45.0,
    /** Multiplikator für Tiefschlaf (z. B. 1.2 nach Sport). */
    val deepFactor: Double = 1.0,
    /** Multiplikator für REM (z. B. 0.7 nach Alkohol). */
    val remFactor: Double = 1.0,
    /** Zusätzliche, zufällig verteilte Wachphasen. */
    val extraAwakenings: Int = 0,
    val baseSkinTemperatureC: Double = 31.0,
)

/**
 * Erzeugt realistische Nachtverläufe.
 *
 * Hypnogramm: Einschlaflatenz (wach), dann Schlafzyklen von 85–110 Minuten aus
 * Leichtschlaf → Tiefschlaf → Leichtschlaf → REM. Der Tiefschlaf nimmt von Zyklus zu
 * Zyklus ab, REM nimmt zu. Zwischen Zyklen gibt es gelegentlich kurze Wachphasen.
 *
 * Physiologie je Epoche:
 * - Puls: Ruhepuls + Phasen-Offset (wach +14, leicht +4, tief −2, REM +8, REM unruhiger),
 *   sinkt über die Nacht um ~3 Schläge.
 * - HRV (RMSSD): Basis × Phasenfaktor (wach 0.65, leicht 1.0, tief 1.45, REM 0.75).
 * - Fußtemperatur: steigt nach dem Einschlafen um ~2,8 °C (distale Gefäßerweiterung).
 * - Bewegung: wach deutlich, leicht wenig mit Zuckungen, tief und REM fast keine.
 * - SpO2: 95–98 %, im REM gelegentlich kleine Senken.
 */
class NightScenarioGenerator @Inject constructor() {

    fun generate(start: Instant, profile: NightProfile, random: Random): NightScenario {
        val stages = hypnogram(profile, random)
        return NightScenario(start, physiology(stages, profile, random))
    }

    private data class Segment(val stage: SleepStage, val minutes: Double)

    internal fun hypnogram(profile: NightProfile, random: Random): List<SleepStage> {
        val segments = mutableListOf(Segment(SleepStage.AWAKE, profile.sleepLatencyMinutes.coerceAtLeast(1.0)))
        var slept = 0.0
        var cycle = 0
        while (slept < profile.totalSleepMinutes) {
            val cycleLength = random.nextDouble(85.0, 110.0)
            val deep = DEEP_MINUTES[cycle.coerceAtMost(DEEP_MINUTES.lastIndex)] *
                profile.deepFactor * random.nextDouble(0.8, 1.2)
            val rem = REM_MINUTES[cycle.coerceAtMost(REM_MINUTES.lastIndex)] *
                profile.remFactor * random.nextDouble(0.8, 1.2)
            val light = (cycleLength - deep - rem).coerceAtLeast(15.0)
            listOf(
                Segment(SleepStage.LIGHT, light * 0.6),
                Segment(SleepStage.DEEP, deep),
                Segment(SleepStage.LIGHT, light * 0.4),
                Segment(SleepStage.REM, rem),
            ).filter { it.minutes >= 1.0 }.forEach { segment ->
                val remaining = profile.totalSleepMinutes - slept
                if (remaining > 0) {
                    val minutes = segment.minutes.coerceAtMost(remaining)
                    segments += segment.copy(minutes = minutes)
                    slept += minutes
                }
            }
            if (slept < profile.totalSleepMinutes && random.chance(0.35)) {
                segments += Segment(SleepStage.AWAKE, random.nextDouble(1.0, 4.0))
            }
            cycle++
        }
        repeat(profile.extraAwakenings) {
            // Nicht in die Einschlaflatenz und nicht ans Ende, damit Einschlafen/Aufwachen eindeutig bleiben.
            if (segments.size > 3) {
                val position = random.nextInt(2, segments.size - 1)
                segments.add(position, Segment(SleepStage.AWAKE, random.nextDouble(2.0, 8.0)))
            }
        }
        segments += Segment(SleepStage.AWAKE, random.nextDouble(3.0, 10.0))

        return segments.flatMap { segment ->
            val epochs = (segment.minutes * EPOCHS_PER_MINUTE).roundToInt().coerceAtLeast(1)
            List(epochs) { segment.stage }
        }
    }

    private fun physiology(stages: List<SleepStage>, profile: NightProfile, random: Random): List<Physiology> {
        val onsetIndex = stages.indexOfFirst { it != SleepStage.AWAKE }.coerceAtLeast(0)
        val skinApproach = 1 - exp(-0.5 / SKIN_WARMUP_MINUTES)
        var heartRate = profile.restingHeartRate + HR_OFFSET.getValue(SleepStage.AWAKE)
        var rmssd = profile.baseRmssdMs * RMSSD_FACTOR.getValue(SleepStage.AWAKE)
        var skin = profile.baseSkinTemperatureC
        var hrNoise = 0.0

        return stages.mapIndexed { index, stage ->
            val progress = index.toDouble() / stages.size
            val asleepPhase = index >= onsetIndex && stage != SleepStage.AWAKE

            hrNoise = 0.85 * hrNoise + random.nextGaussian(sd = if (stage == SleepStage.REM) 1.8 else 0.9)
            val hrTarget = profile.restingHeartRate + HR_OFFSET.getValue(stage) - 3.0 * progress
            heartRate += (hrTarget - heartRate) * 0.3
            val hr = (heartRate + hrNoise).coerceIn(38.0, 140.0)

            val rmssdTarget = profile.baseRmssdMs * RMSSD_FACTOR.getValue(stage)
            rmssd += (rmssdTarget - rmssd) * 0.25
            val hrv = (rmssd * (1 + random.nextGaussian(sd = 0.07))).coerceIn(8.0, 160.0)

            val skinTarget = when {
                asleepPhase -> profile.baseSkinTemperatureC + 2.8 - 0.6 * progress
                index >= onsetIndex -> profile.baseSkinTemperatureC + 2.2 - 0.6 * progress
                else -> profile.baseSkinTemperatureC
            }
            skin += (skinTarget - skin) * skinApproach
            val skinTemperature = skin + random.nextGaussian(sd = 0.03)

            val motion = when (stage) {
                SleepStage.AWAKE -> random.nextDouble(0.12, 0.8)
                SleepStage.LIGHT -> if (random.chance(0.05)) random.nextDouble(0.1, 0.3) else random.nextDouble(0.0, 0.06)
                SleepStage.DEEP -> random.nextDouble(0.0, 0.01)
                SleepStage.REM -> if (random.chance(0.03)) random.nextDouble(0.05, 0.12) else random.nextDouble(0.0, 0.015)
            }

            val spo2Dip = if (stage == SleepStage.REM && random.chance(0.04)) random.nextDouble(1.0, 2.5) else 0.0
            val spo2 = (96.6 + random.nextGaussian(sd = 0.5) - spo2Dip).coerceIn(90.0, 99.5)

            Physiology(stage, hr, hrv, spo2, skinTemperature, motion)
        }
    }

    /** Wacher Mensch (z. B. abends vor dem Schlafen) für den Echtzeitmodus. */
    fun awake(profile: NightProfile, random: Random): Physiology = Physiology(
        stage = SleepStage.AWAKE,
        heartRateBpm = profile.restingHeartRate + 14 + random.nextGaussian(sd = 2.0),
        hrvRmssdMs = profile.baseRmssdMs * 0.65 * (1 + random.nextGaussian(sd = 0.08)),
        spo2Percent = 97.0 + random.nextGaussian(sd = 0.4),
        skinTemperatureC = profile.baseSkinTemperatureC - 0.3 + random.nextGaussian(sd = 0.05),
        motion = random.nextDouble(0.1, 0.7),
    )

    private companion object {
        val EPOCHS_PER_MINUTE = 60.0 / EPOCH_LENGTH.seconds
        const val SKIN_WARMUP_MINUTES = 40.0
        val DEEP_MINUTES = listOf(38.0, 26.0, 14.0, 6.0, 0.0)
        val REM_MINUTES = listOf(8.0, 16.0, 22.0, 28.0, 32.0, 35.0)
        val HR_OFFSET = mapOf(
            SleepStage.AWAKE to 14.0,
            SleepStage.LIGHT to 4.0,
            SleepStage.DEEP to -2.0,
            SleepStage.REM to 8.0,
        )
        val RMSSD_FACTOR = mapOf(
            SleepStage.AWAKE to 0.65,
            SleepStage.LIGHT to 1.0,
            SleepStage.DEEP to 1.45,
            SleepStage.REM to 0.75,
        )
    }
}
