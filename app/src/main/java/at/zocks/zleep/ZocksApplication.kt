package at.zocks.zleep

import android.app.Application
import at.zocks.zleep.domain.control.ControlCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ZocksApplication : Application() {

    @Inject lateinit var controlCoordinator: ControlCoordinator

    override fun onCreate() {
        super.onCreate()
        controlCoordinator.start()
    }
}
