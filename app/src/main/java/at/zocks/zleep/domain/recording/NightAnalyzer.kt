package at.zocks.zleep.domain.recording

import at.zocks.zleep.domain.analysis.NightSummaryUpdater
import at.zocks.zleep.domain.analysis.SleepStageClassifier
import at.zocks.zleep.domain.analysis.SleepWindow
import at.zocks.zleep.domain.analysis.SleepWindowDetector
import at.zocks.zleep.domain.model.NightSource
import at.zocks.zleep.domain.model.StageEpoch
import at.zocks.zleep.domain.repository.NightRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

data class AnalysisResult(val stages: List<StageEpoch>, val window: SleepWindow?)

/**
 * Schätzt Schlafphasen und Schlaffenster einer Nacht und speichert beides.
 * Demo-Nächte behalten ihre vorgegebenen Phasen; ein von Hand korrigiertes Fenster bleibt.
 * Ist die Nacht abgeschlossen, werden danach ihre Kennzahlen neu berechnet.
 */
class NightAnalyzer(
    private val nights: NightRepository,
    private val classifier: SleepStageClassifier,
    private val summaryUpdater: NightSummaryUpdater,
    private val computeDispatcher: CoroutineDispatcher,
) {
    suspend fun analyze(nightId: Long): AnalysisResult? {
        val data = nights.getNightData(nightId) ?: return null
        val stages = if (data.night.source == NightSource.DEMO) {
            data.stages
        } else {
            withContext(computeDispatcher) { classifier.classify(data.measurements) }
                .also { nights.saveStages(nightId, it) }
        }
        val window = withContext(computeDispatcher) { SleepWindowDetector.detect(stages) }
        if (window != null) nights.updateSleepWindow(nightId, window.sleepOnset, window.finalWake, manual = false)
        if (data.night.end != null) summaryUpdater.refresh(nightId)
        return AnalysisResult(stages, window)
    }
}
