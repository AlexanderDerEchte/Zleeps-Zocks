package at.zocks.zleep.device.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import at.zocks.zleep.di.ApplicationScope
import at.zocks.zleep.domain.alarm.SmartAlarmController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Systemwecker (Fensterende, Schlummern) und die Knöpfe „Aus“/„Schlummern“ der Benachrichtigung. */
@AndroidEntryPoint
class WakeAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var alarm: SmartAlarmController

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_WAKE -> alarm.onSystemAlarm()
                    ACTION_DISMISS -> alarm.dismiss()
                    ACTION_SNOOZE -> alarm.snooze()
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_WAKE = "at.zocks.zleep.action.WAKE"
        const val ACTION_DISMISS = "at.zocks.zleep.action.ALARM_DISMISS"
        const val ACTION_SNOOZE = "at.zocks.zleep.action.ALARM_SNOOZE"
    }
}
