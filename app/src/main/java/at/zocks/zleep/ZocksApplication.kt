package at.zocks.zleep

import android.app.Application
import at.zocks.zleep.domain.alarm.SmartAlarmController
import at.zocks.zleep.domain.control.ControlCoordinator
import at.zocks.zleep.domain.health.HealthExporter
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ZocksApplication : Application() {

    @Inject lateinit var controlCoordinator: ControlCoordinator

    @Inject lateinit var smartAlarm: SmartAlarmController

    @Inject lateinit var healthExporter: HealthExporter

    override fun onCreate() {
        super.onCreate()
        controlCoordinator.start()
        // Wecker und Export laufen im App-Scope, auch wenn nur der Aufzeichnungsdienst lebt.
        smartAlarm.start()
        healthExporter.start()
    }
}
