package at.zocks.zleep.di

import at.zocks.zleep.domain.control.ControlCoordinator
import at.zocks.zleep.domain.device.SockPairProvider
import at.zocks.zleep.domain.heat.HeatController
import at.zocks.zleep.domain.heat.PreheatScheduler
import at.zocks.zleep.domain.massage.MassageController
import at.zocks.zleep.domain.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import java.time.Clock
import javax.inject.Singleton

/** Die Regler sind reines Kotlin (domain) und werden hier verdrahtet. */
@Module
@InstallIn(SingletonComponent::class)
object ControlModule {

    @Provides
    @Singleton
    fun heatController(pairProvider: SockPairProvider, @ApplicationScope scope: CoroutineScope, clock: Clock) =
        HeatController(pairProvider, scope, clock)

    @Provides
    @Singleton
    fun massageController(pairProvider: SockPairProvider, @ApplicationScope scope: CoroutineScope, clock: Clock) =
        MassageController(pairProvider, scope, clock)

    @Provides
    @Singleton
    fun controlCoordinator(
        settingsRepository: SettingsRepository,
        heatController: HeatController,
        pairProvider: SockPairProvider,
        scheduler: PreheatScheduler,
        @ApplicationScope scope: CoroutineScope,
        clock: Clock,
    ) = ControlCoordinator(settingsRepository, heatController, pairProvider, scheduler, scope, clock)
}
