package at.zocks.zleep.device.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import at.zocks.zleep.MainActivity
import at.zocks.zleep.R
import at.zocks.zleep.domain.alarm.AlarmNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zeigt den klingelnden Wecker als Benachrichtigung mit Vollbild-Anzeige. Mit Ton läuft sie
 * auf einem eigenen Kanal mit Weckerton, der sich wiederholt (`FLAG_INSISTENT`), bis
 * „Aus“ oder „Schlummern“ gewählt wird; ohne Ton (sanfte Massage) bleibt sie still.
 */
@Singleton
class NotificationAlarmNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AlarmNotifier {

    private val manager = context.getSystemService(NotificationManager::class.java)

    override fun show(sound: Boolean) {
        ensureChannels()
        val notification = build(sound)
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Ohne Erlaubnis für Benachrichtigungen bleibt die Anzeige in der App.
        }
    }

    override fun dismiss() {
        manager.cancel(NOTIFICATION_ID)
    }

    private fun build(sound: Boolean): Notification {
        val open = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_ALARM, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, if (sound) CHANNEL_SOUND else CHANNEL_SILENT)
            .setSmallIcon(R.drawable.ic_stat_zocks)
            .setContentTitle(context.getString(R.string.alarm_notification_title))
            .setContentText(context.getString(if (sound) R.string.alarm_notification_text else R.string.alarm_notification_text_massage))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .addAction(0, context.getString(R.string.alarm_snooze), action(WakeAlarmReceiver.ACTION_SNOOZE, REQUEST_SNOOZE))
            .addAction(0, context.getString(R.string.alarm_dismiss), action(WakeAlarmReceiver.ACTION_DISMISS, REQUEST_DISMISS))
        if (!sound) builder.setSilent(true)
        return builder.build().apply { if (sound) flags = flags or Notification.FLAG_INSISTENT }
    }

    private fun action(action: String, requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, WakeAlarmReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun ensureChannels() {
        if (manager.getNotificationChannel(CHANNEL_SOUND) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_SOUND, context.getString(R.string.alarm_channel_sound), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.alarm_channel_sound_description)
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    enableVibration(true)
                    setBypassDnd(true)
                },
            )
        }
        if (manager.getNotificationChannel(CHANNEL_SILENT) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_SILENT, context.getString(R.string.alarm_channel_silent), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.alarm_channel_silent_description)
                    setSound(null, null)
                    enableVibration(false)
                },
            )
        }
    }

    private companion object {
        const val CHANNEL_SOUND = "alarm_sound"
        const val CHANNEL_SILENT = "alarm_silent"
        const val NOTIFICATION_ID = 4_401
        const val REQUEST_OPEN = 4_402
        const val REQUEST_SNOOZE = 4_403
        const val REQUEST_DISMISS = 4_404
    }
}
