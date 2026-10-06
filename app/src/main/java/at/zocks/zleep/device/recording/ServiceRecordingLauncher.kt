package at.zocks.zleep.device.recording

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import at.zocks.zleep.di.ApplicationScope
import at.zocks.zleep.domain.recording.NightRecorder
import at.zocks.zleep.domain.recording.RecordingLauncher
import at.zocks.zleep.domain.recording.RecordingState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Startet die Aufzeichnung im App-Scope und den Dienst, der den Prozess wach hält. */
@Singleton
class ServiceRecordingLauncher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val recorder: NightRecorder,
    @param:ApplicationScope private val scope: CoroutineScope,
) : RecordingLauncher {

    override fun start() {
        scope.launch { recorder.start() }
        startService(NightRecordingService.ACTION_START)
    }

    override fun stop() {
        scope.launch { recorder.stop() }
    }

    override fun resumeIfNeeded() {
        if (recorder.state.value is RecordingState.Active) return
        scope.launch {
            if (recorder.resumeIfNeeded()) startService(NightRecordingService.ACTION_START)
        }
    }

    private fun startService(action: String) {
        val intent = Intent(context, NightRecordingService::class.java).setAction(action)
        runCatching {
            if (NightRecordingService.canRunInForeground(context)) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
