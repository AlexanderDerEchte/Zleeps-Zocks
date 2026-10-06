package at.zocks.zleep.di

import at.zocks.zleep.device.DefaultSockPairProvider
import at.zocks.zleep.device.simulator.DemoDataSeeder
import at.zocks.zleep.device.simulator.SimulationEngine
import at.zocks.zleep.domain.device.SockPairProvider
import at.zocks.zleep.domain.simulator.DemoDataController
import at.zocks.zleep.domain.simulator.SimulatorController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DeviceModule {
    @Binds
    abstract fun sockPairProvider(impl: DefaultSockPairProvider): SockPairProvider

    @Binds
    abstract fun simulatorController(impl: SimulationEngine): SimulatorController

    @Binds
    abstract fun demoDataController(impl: DemoDataSeeder): DemoDataController
}
