package at.zocks.zleep.device.recording

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import at.zocks.zleep.di.ApplicationScope
import at.zocks.zleep.domain.recording.NightRecorder
import at.zocks.zleep.domain.recording.RecordingState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hält die App während der Nachtaufzeichnung als Vordergrunddienst am Leben (mit
 * Benachrichtigung und Teil-Wakelock). Die eigentliche Aufzeichnung macht [NightRecorder].
 * Beendet das System den Prozess, startet der Dienst neu (START_STICKY) und setzt die
 * offene Nacht fort.
 */
@AndroidEntryPoint
class NightRecordingService : Service() {

    @Inject lateinit var recorder: NightRecorder

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    private var observer: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                scope.launch { recorder.stop() }
                return START_NOT_STICKY
            }
            else -> {
                goForeground()
                if (intent == null) {
                    // Vom System neu gestartet: offene Nacht fortsetzen oder beenden.
                    scope.launch { if (!recorder.resumeIfNeeded()) stopSelf() }
                }
                observeRecorder()
            }
        }
        return START_STICKY
    }

    private fun goForeground() {
        RecordingNotifications.ensureChannel(this)
        val notification = RecordingNotifications.build(this, recorder.state.value as? RecordingState.Active)
        if (canRunInForeground(this)) {
            runCatching {
                ServiceCompat.startForeground(
                    this,
                    RecordingNotifications.NOTIFICATION_ID,
                    notification,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
                )
            }
        }
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "zocks:night-recording")
                .apply { acquire(WAKE_LOCK_TIMEOUT_MS) }
        }
    }

    private fun observeRecorder() {
        if (observer != null) return
        observer = scope.launch {
            var seenActive = false
            // Startet die Aufzeichnung nicht, den Dienst nicht ewig laufen lassen.
            launch {
                delay(START_TIMEOUT_MS)
                if (!seenActive) finish()
            }
            recorder.state
                .map { state -> (state as? RecordingState.Active)?.let { RecordingNotifications.text(this@NightRecordingService, it) } }
                .distinctUntilChanged()
                .collect { text ->
                    val active = recorder.state.value as? RecordingState.Active
                    when {
                        text != null && active != null -> {
                            seenActive = true
                            if (canPostNotifications()) {
                                try {
                                    NotificationManagerCompat.from(this@NightRecordingService).notify(
                                        RecordingNotifications.NOTIFICATION_ID,
                                        RecordingNotifications.build(this@NightRecordingService, active),
                                    )
                                } catch (_: SecurityException) {
                                    // Berechtigung inzwischen entzogen: Aufzeichnung läuft ohne Aktualisierung weiter.
                                }
                            }
                        }
                        // Erst nach einer gelaufenen Aufzeichnung beenden, nicht schon vor ihrem Start.
                        seenActive -> finish()
                    }
                }
        }
    }

    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun canPostNotifications(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        observer?.cancel()
        observer = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "at.zocks.zleep.action.START_RECORDING"
        const val ACTION_STOP = "at.zocks.zleep.action.STOP_RECORDING"
        private const val WAKE_LOCK_TIMEOUT_MS = 16 * 60 * 60 * 1000L
        private const val START_TIMEOUT_MS = 30_000L

        /**
         * Ab Android 14 braucht ein Dienst vom Typ „verbundenes Gerät“ die Berechtigung
         * „Geräte in der Nähe“. Ohne sie läuft die Aufzeichnung nur, solange die App offen ist.
         */
        fun canRunInForeground(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }
}
