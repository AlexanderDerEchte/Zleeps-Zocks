package at.zocks.zleep.domain.analysis

import at.zocks.zleep.device.simulator.DemoNightsGenerator
import at.zocks.zleep.device.simulator.NightScenarioGenerator
import at.zocks.zleep.domain.model.SleepStage
import java.time.LocalDate
import java.time.ZoneId

/** Gemeinsame Auswertung des Klassifikators gegen die Wahrheit des Simulators (für Tests). */
object ClassifierEvaluation {

    data class Result(
        val accuracy: Double,
        /** Zeilen: wahre Phase, Spalten: geschätzte Phase. */
        val confusion: Map<SleepStage, Map<SleepStage, Int>>,
    ) {
        fun recall(stage: SleepStage): Double {
            val row = confusion[stage].orEmpty()
            val total = row.values.sum()
            return if (total == 0) 0.0 else (row[stage] ?: 0).toDouble() / total
        }

        fun precision(stage: SleepStage): Double {
            val predicted = confusion.values.sumOf { it[stage] ?: 0 }
            return if (predicted == 0) 0.0 else (confusion[stage]?.get(stage) ?: 0).toDouble() / predicted
        }
    }

    fun simulatedNights(count: Int, seed: Long) = DemoNightsGenerator(NightScenarioGenerator())
        .generate(LocalDate.of(2026, 10, 5), count, ZoneId.of("Europe/Vienna"), emptyMap(), seed)

    fun evaluate(classifier: SleepStageClassifier, nights: Int = 20, seed: Long = 77): Result {
        val confusion = SleepStage.entries.associateWith { SleepStage.entries.associateWith { 0 }.toMutableMap() }
        var hits = 0
        var total = 0
        simulatedNights(nights, seed).forEach { night ->
            val truth = night.stages.associate { it.start to it.stage }
            classifier.classify(night.measurements).forEach { estimate ->
                val actual = truth[estimate.start] ?: return@forEach
                confusion.getValue(actual)[estimate.stage] = confusion.getValue(actual).getValue(estimate.stage) + 1
                if (actual == estimate.stage) hits++
                total++
            }
        }
        return Result(hits.toDouble() / total, confusion)
    }
}
