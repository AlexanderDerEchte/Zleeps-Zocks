package at.zocks.zleep.di

import at.zocks.zleep.data.export.DocumentExportSink
import at.zocks.zleep.data.health.HealthConnectGatewayImpl
import at.zocks.zleep.device.alarm.ExactWakeAlarmScheduler
import at.zocks.zleep.device.alarm.NotificationAlarmNotifier
import at.zocks.zleep.domain.alarm.AlarmNotifier
import at.zocks.zleep.domain.alarm.SmartAlarmController
import at.zocks.zleep.domain.alarm.WakeAlarmScheduler
import at.zocks.zleep.domain.device.SockPairProvider
import at.zocks.zleep.domain.export.CsvExporter
import at.zocks.zleep.domain.export.ExportSink
import at.zocks.zleep.domain.health.HealthConnectGateway
import at.zocks.zleep.domain.health.HealthExporter
import at.zocks.zleep.domain.heat.HeatController
import at.zocks.zleep.domain.massage.MassageController
import at.zocks.zleep.domain.recording.LiveRecording
import at.zocks.zleep.domain.recording.NightRecorder
import at.zocks.zleep.domain.repository.MassageProgramRepository
import at.zocks.zleep.domain.repository.NightRepository
import at.zocks.zleep.domain.repository.NightSummaryRepository
import at.zocks.zleep.domain.repository.SettingsRepository
import at.zocks.zleep.domain.routine.RoutineRunner
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import java.time.Clock
import javax.inject.Singleton

/** Abendroutine, smarter Wecker und Exporte (Phase 6). */
@Module
@InstallIn(SingletonComponent::class)
object EveningModule {

    @Provides
    @Singleton
    fun routineRunner(
        pairProvider: SockPairProvider,
        heat: HeatController,
        massage: MassageController,
        programs: MassageProgramRepository,
        @ApplicationScope scope: CoroutineScope,
        clock: Clock,
    ) = RoutineRunner(pairProvider, heat, massage, programs, scope, clock)

    @Provides
    @Singleton
    fun smartAlarm(
        recording: LiveRecording,
        settings: SettingsRepository,
        nights: NightRepository,
        massage: MassageController,
        notifier: AlarmNotifier,
        scheduler: WakeAlarmScheduler,
        clock: Clock,
        @ApplicationScope scope: CoroutineScope,
    ) = SmartAlarmController(recording, settings, nights, massage, notifier, scheduler, clock, scope)

    @Provides
    @Singleton
    fun healthExporter(
        nights: NightRepository,
        gateway: HealthConnectGateway,
        settings: SettingsRepository,
        recording: LiveRecording,
        @ApplicationScope scope: CoroutineScope,
    ) = HealthExporter(nights, gateway, settings, recording, scope)

    @Provides
    fun csvExporter(nights: NightRepository, summaries: NightSummaryRepository, settings: SettingsRepository) =
        CsvExporter(nights, summaries, settings)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class EveningBindings {
    @Binds
    abstract fun liveRecording(recorder: NightRecorder): LiveRecording

    @Binds
    abstract fun alarmNotifier(impl: NotificationAlarmNotifier): AlarmNotifier

    @Binds
    abstract fun wakeAlarmScheduler(impl: ExactWakeAlarmScheduler): WakeAlarmScheduler

    @Binds
    abstract fun healthConnectGateway(impl: HealthConnectGatewayImpl): HealthConnectGateway

    @Binds
    abstract fun exportSink(impl: DocumentExportSink): ExportSink
}
