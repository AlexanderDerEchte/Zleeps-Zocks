package at.zocks.zleep.di

import at.zocks.zleep.device.recording.DefaultDeviceClock
import at.zocks.zleep.device.recording.ServiceRecordingLauncher
import at.zocks.zleep.domain.analysis.HeuristicSleepStageClassifier
import at.zocks.zleep.domain.analysis.SleepStageClassifier
import at.zocks.zleep.domain.device.SockPairProvider
import at.zocks.zleep.domain.heat.HeatController
import at.zocks.zleep.domain.massage.MassageController
import at.zocks.zleep.domain.recording.DeviceClock
import at.zocks.zleep.domain.recording.NightAnalyzer
import at.zocks.zleep.domain.recording.NightRecorder
import at.zocks.zleep.domain.recording.RecordingLauncher
import at.zocks.zleep.domain.repository.NightRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RecordingModule {

    @Provides
    fun sleepStageClassifier(): SleepStageClassifier = HeuristicSleepStageClassifier()

    @Provides
    @Singleton
    fun nightAnalyzer(
        nights: NightRepository,
        classifier: SleepStageClassifier,
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ) = NightAnalyzer(nights, classifier, dispatcher)

    @Provides
    @Singleton
    fun nightRecorder(
        pairProvider: SockPairProvider,
        nights: NightRepository,
        analyzer: NightAnalyzer,
        heatController: HeatController,
        massageController: MassageController,
        clock: DeviceClock,
        @ApplicationScope scope: CoroutineScope,
    ) = NightRecorder(pairProvider, nights, analyzer, heatController, massageController, clock, scope)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RecordingBindings {
    @Binds
    abstract fun deviceClock(impl: DefaultDeviceClock): DeviceClock

    @Binds
    abstract fun recordingLauncher(impl: ServiceRecordingLauncher): RecordingLauncher
}
