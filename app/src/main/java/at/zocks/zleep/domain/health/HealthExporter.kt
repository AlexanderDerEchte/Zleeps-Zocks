package at.zocks.zleep.domain.health

import at.zocks.zleep.domain.recording.LiveRecording
import at.zocks.zleep.domain.recording.RecordingState
import at.zocks.zleep.domain.repository.NightRepository
import at.zocks.zleep.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Überträgt Nächte nach Health Connect: auf Wunsch jede Nacht automatisch nach dem Ende
 * der Aufzeichnung, sonst alle auf Knopfdruck. Nur echte, abgeschlossene Nächte
 * ([HealthExportMapper.isExportable]).
 */
class HealthExporter(
    private val nights: NightRepository,
    private val gateway: HealthConnectGateway,
    private val settings: SettingsRepository,
    private val recorder: LiveRecording,
    private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            var wasActive = recorder.state.value is RecordingState.Active
            recorder.state.collect { state ->
                val finished = (state as? RecordingState.Idle)?.lastNightId
                if (wasActive && finished != null && settings.settings.first().healthConnectAutoExport) {
                    runCatching { export(finished) }
                }
                wasActive = state is RecordingState.Active
            }
        }
    }

    /** Überträgt eine Nacht; `false`, wenn sie nicht übertragbar ist oder die Freigabe fehlt. */
    suspend fun export(nightId: Long): Boolean {
        if (!ready()) return false
        val session = nights.getNightData(nightId)?.let(HealthExportMapper::map) ?: return false
        gateway.write(session)
        return true
    }

    /** Überträgt alle übertragbaren Nächte; liefert ihre Anzahl. */
    suspend fun exportAll(): Int {
        if (!ready()) return 0
        var count = 0
        nights.observeNights().first().filter { it.end != null }.forEach { night ->
            val session = nights.getNightData(night.id)?.let(HealthExportMapper::map) ?: return@forEach
            gateway.write(session)
            count++
        }
        return count
    }

    private suspend fun ready() = gateway.availability() == HealthAvailability.AVAILABLE && gateway.hasPermissions()
}
