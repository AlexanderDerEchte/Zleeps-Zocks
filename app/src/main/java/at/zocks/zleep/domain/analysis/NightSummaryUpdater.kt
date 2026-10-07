package at.zocks.zleep.domain.analysis

import at.zocks.zleep.domain.model.NightData
import at.zocks.zleep.domain.repository.NightRepository
import at.zocks.zleep.domain.repository.NightSummaryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hält die gespeicherten Kennzahlen aktuell. Neu berechnet wird nach jeder Auswertung einer
 * abgeschlossenen Nacht, nach einer Korrektur des Schlaffensters und nach dem Laden von
 * Demo-Nächten. Zusätzlich ergänzt [start] fehlende Zusammenfassungen, sobald neue
 * abgeschlossene Nächte auftauchen (z. B. nach einem Update oder einem Abbruch).
 */
class NightSummaryUpdater(
    private val nights: NightRepository,
    private val summaries: NightSummaryRepository,
    private val clock: Clock,
    private val computeDispatcher: CoroutineDispatcher,
    private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)

    /** Beobachtet die Nächte im App-Scope; mehrfacher Aufruf ist harmlos. */
    @OptIn(FlowPreview::class)
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            nights.observeNights()
                .map { list -> list.filter { it.end != null }.map { it.id }.toSet() }
                .distinctUntilChanged()
                .debounce(SETTLE_MS)
                .collectLatest { refreshMissing() }
        }
    }

    /** Berechnet die Kennzahlen einer Nacht neu; laufende Nächte werden übersprungen. */
    suspend fun refresh(nightId: Long) {
        val data = nights.getNightData(nightId) ?: return
        save(data)
    }

    /** Speichert die Kennzahlen aus bereits geladenen Daten (z. B. frisch erzeugte Demo-Nächte). */
    suspend fun save(data: NightData) {
        if (data.night.end == null) return
        val summary = withContext(computeDispatcher) { NightSummarizer.summarize(data, clock.zone, clock.instant()) }
        summaries.save(summary)
    }

    /** Ergänzt fehlende Zusammenfassungen. */
    suspend fun refreshMissing() {
        summaries.nightsWithoutSummary().forEach { refresh(it) }
    }

    private companion object {
        /** Kurz warten, bis z. B. das Laden der Demo-Nächte durch ist (die speichern selbst). */
        const val SETTLE_MS = 1_000L
    }
}
