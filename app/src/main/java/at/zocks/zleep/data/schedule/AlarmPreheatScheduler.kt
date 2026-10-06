package at.zocks.zleep.data.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import at.zocks.zleep.domain.heat.PreheatScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plant das Vorwärmen mit einem nicht exakten Wecker (Zeitfenster [WINDOW_MS]).
 * Für Vorwärmen reicht das und es braucht keine Berechtigung für exakte Wecker.
 */
@Singleton
class AlarmPreheatScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PreheatScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(at: Instant) {
        alarmManager.setWindow(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), WINDOW_MS, pendingIntent())
    }

    override fun cancel() {
        alarmManager.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, PreheatReceiver::class.java).setAction(PreheatReceiver.ACTION_PREHEAT),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val REQUEST_CODE = 4_201
        const val WINDOW_MS = 5 * 60 * 1000L
    }
}
