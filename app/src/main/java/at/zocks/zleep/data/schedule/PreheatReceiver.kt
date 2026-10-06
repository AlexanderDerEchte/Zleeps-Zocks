package at.zocks.zleep.data.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import at.zocks.zleep.di.ApplicationScope
import at.zocks.zleep.domain.control.ControlCoordinator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Startet das geplante Vorwärmen und plant den nächsten Tag. */
@AndroidEntryPoint
class PreheatReceiver : BroadcastReceiver() {

    @Inject lateinit var coordinator: ControlCoordinator

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_PREHEAT -> coordinator.runPreheat()
                    Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> coordinator.reschedulePreheat()
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PREHEAT = "at.zocks.zleep.action.PREHEAT"
    }
}
