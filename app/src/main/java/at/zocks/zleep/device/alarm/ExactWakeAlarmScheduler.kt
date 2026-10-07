package at.zocks.zleep.device.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import at.zocks.zleep.MainActivity
import at.zocks.zleep.domain.alarm.WakeAlarmScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ausfallsicherung des smarten Weckers über den Systemwecker. Mit Freigabe für exakte
 * Wecker als „Wecker“ (`setAlarmClock`, erscheint auch in der Statusleiste); ohne Freigabe
 * so genau, wie das System erlaubt – dann kann er sich um einige Minuten verspäten.
 */
@Singleton
class ExactWakeAlarmScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : WakeAlarmScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(at: Instant) {
        val trigger = at.toEpochMilli()
        if (canScheduleExact()) {
            val show = PendingIntent.getActivity(
                context,
                REQUEST_SHOW,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, show), fire())
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, fire())
        }
    }

    override fun cancel() {
        alarmManager.cancel(fire())
    }

    override fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun fire(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_FIRE,
        Intent(context, WakeAlarmReceiver::class.java).setAction(WakeAlarmReceiver.ACTION_WAKE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val REQUEST_FIRE = 4_301
        const val REQUEST_SHOW = 4_302
    }
}
