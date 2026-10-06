package at.zocks.zleep.device.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import at.zocks.zleep.MainActivity
import at.zocks.zleep.R
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.domain.recording.RecordingState
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Benachrichtigung der laufenden Nachtaufzeichnung (ruhig, ohne Ton). */
object RecordingNotifications {
    const val CHANNEL_ID = "night_recording"
    const val NOTIFICATION_ID = 4_101

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.recording_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.recording_channel_description)
                setShowBadge(false)
            },
        )
    }

    fun build(context: Context, state: RecordingState.Active?): Notification {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            context,
            1,
            Intent(context, NightRecordingService::class.java).setAction(NightRecordingService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_zocks)
            .setContentTitle(context.getString(R.string.recording_notification_title))
            .setContentText(text(context, state))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.recording_stop), stop)
            .build()
    }

    /** Kurzer Status; ändert sich höchstens minütlich, damit die Benachrichtigung ruhig bleibt. */
    fun text(context: Context, state: RecordingState.Active?): String {
        if (state == null) return context.getString(R.string.recording_starting)
        val locale: Locale = context.resources.configuration.locales[0]
        val since = DateTimeFormatter.ofPattern("HH:mm", locale).format(state.startedAt.atZone(ZoneId.systemDefault()))
        val status = when {
            state.openGaps.isNotEmpty() -> context.getString(
                R.string.recording_connection_lost,
                context.getString(if (SockSide.LEFT in state.openGaps) R.string.sock_left else R.string.sock_right),
            )
            state.asleep -> context.getString(R.string.recording_status_asleep)
            else -> context.getString(R.string.recording_status_awake)
        }
        return context.getString(R.string.recording_notification_text, since, status)
    }
}
