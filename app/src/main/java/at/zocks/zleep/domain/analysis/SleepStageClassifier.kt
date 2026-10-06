package at.zocks.zleep.domain.analysis

import at.zocks.zleep.domain.model.EpochMeasurement
import at.zocks.zleep.domain.model.StageEpoch

/**
 * Schätzt Schlafphasen aus den Epochen einer Nacht. Austauschbar, damit spätere, bessere
 * Verfahren (z. B. ein trainiertes Modell) ohne Änderungen am Rest eingesetzt werden können.
 *
 * Epochen ohne verwertbare Messwerte (Verbindungslücken) bekommen keine Phase.
 */
interface SleepStageClassifier {
    fun classify(measurements: List<EpochMeasurement>): List<StageEpoch>
}
