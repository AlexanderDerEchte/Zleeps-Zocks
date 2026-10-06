package at.zocks.zleep.domain.analysis

import at.zocks.zleep.domain.model.EPOCH_LENGTH
import at.zocks.zleep.domain.model.SleepStage
import at.zocks.zleep.domain.model.StageEpoch
import java.time.Instant

data class SleepWindow(val sleepOnset: Instant, val finalWake: Instant)

/**
 * Einschlafen = Beginn des ersten stabilen Schlafs: ab hier sind in den nächsten
 * [WINDOW_EPOCHS] Epochen (10 min) mindestens [ONSET_SLEEP_SHARE] Schlaf.
 * Aufwachen = Ende der letzten Schlafepoche, vor der in den [WINDOW_EPOCHS] Epochen
 * mindestens [WAKE_SLEEP_SHARE] Schlaf lag. Die Regel ist morgens bewusst milder: Wer nach
 * kurzem Aufwachen noch einmal wegdämmert, schläft noch.
 * Kurze Wachphasen dazwischen gehören zum Schlaffenster.
 */
object SleepWindowDetector {

    const val WINDOW_EPOCHS = 20
    const val ONSET_SLEEP_SHARE = 0.8
    const val WAKE_SLEEP_SHARE = 0.6

    fun detect(stages: List<StageEpoch>): SleepWindow? {
        if (stages.size < WINDOW_EPOCHS) return null
        val asleep = stages.map { it.stage != SleepStage.AWAKE }
        val neededForOnset = (WINDOW_EPOCHS * ONSET_SLEEP_SHARE).toInt()
        val neededForWake = (WINDOW_EPOCHS * WAKE_SLEEP_SHARE).toInt()

        val onset = (0..stages.size - WINDOW_EPOCHS).firstOrNull { i ->
            asleep[i] && asleep.subList(i, i + WINDOW_EPOCHS).count { it } >= neededForOnset
        } ?: return null
        val last = (stages.lastIndex downTo onset + WINDOW_EPOCHS - 1).firstOrNull { j ->
            asleep[j] && asleep.subList(j - WINDOW_EPOCHS + 1, j + 1).count { it } >= neededForWake
        } ?: return null

        return SleepWindow(stages[onset].start, stages[last].start.plus(EPOCH_LENGTH))
    }
}
