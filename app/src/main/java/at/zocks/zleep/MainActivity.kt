package at.zocks.zleep

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import at.zocks.zleep.ui.ZocksApp
import at.zocks.zleep.ui.theme.ZocksTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        showOverLockScreenIfAlarm(intent)
        setContent {
            ZocksTheme {
                ZocksApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        showOverLockScreenIfAlarm(intent)
    }

    /** Der klingelnde Wecker darf über dem Sperrbildschirm erscheinen und den Bildschirm einschalten. */
    private fun showOverLockScreenIfAlarm(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_ALARM, false) != true) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
    }

    companion object {
        const val EXTRA_ALARM = "at.zocks.zleep.extra.ALARM"
    }
}
