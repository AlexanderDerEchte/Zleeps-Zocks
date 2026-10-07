package at.zocks.zleep.device.simulator

import at.zocks.zleep.di.ApplicationScope
import at.zocks.zleep.di.DefaultDispatcher
import at.zocks.zleep.domain.analysis.NightSummaryUpdater
import at.zocks.zleep.domain.model.NightSource
import at.zocks.zleep.domain.model.Tag
import at.zocks.zleep.domain.repository.NightRepository
import at.zocks.zleep.domain.repository.TagRepository
import at.zocks.zleep.domain.simulator.DemoDataController
import at.zocks.zleep.domain.simulator.DemoDataStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DemoDataSeeder @Inject constructor(
    private val nightRepository: NightRepository,
    private val tagRepository: TagRepository,
    private val generator: DemoNightsGenerator,
    private val summaryUpdater: NightSummaryUpdater,
    private val clock: Clock,
    @param:DefaultDispatcher private val computeDispatcher: CoroutineDispatcher,
    @ApplicationScope scope: CoroutineScope,
) : DemoDataController {

    private val loading = MutableStateFlow(LoadingState())

    override val status: StateFlow<DemoDataStatus> =
        combine(nightRepository.observeCount(NightSource.DEMO), loading) { count, state ->
            DemoDataStatus(demoNightCount = count, loading = state.active, progress = state.progress)
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), DemoDataStatus(0, loading = false, progress = 0f))

    override suspend fun loadDemoNights() {
        if (loading.value.active) return
        loading.value = LoadingState(active = true, progress = 0f)
        try {
            nightRepository.deleteBySource(NightSource.DEMO)
            val tags = Tag.BuiltInKeys.mapNotNull { key -> tagRepository.getByKey(key)?.let { key to it } }.toMap()
            val zone = clock.zone
            val now = clock.instant().atZone(zone)
            // Letzte Nacht = die Nacht, die gestern Abend begonnen hat.
            val lastNight = now.toLocalDate().minusDays(1)
            // Erzeugen ist rechenintensiv (≈ 60 000 Epochen) – nicht auf dem Main-Thread.
            val nights = withContext(computeDispatcher) {
                generator.generate(lastNight, DEMO_NIGHTS, zone, tags, seed = clock.millis())
            }
            nights.forEachIndexed { index, night ->
                val id = nightRepository.insertCompleteNight(night)
                summaryUpdater.save(night.copy(night = night.night.copy(id = id)))
                loading.update { it.copy(progress = (index + 1f) / nights.size) }
            }
        } finally {
            loading.value = LoadingState()
        }
    }

    override suspend fun clearDemoNights() = nightRepository.deleteBySource(NightSource.DEMO)

    private data class LoadingState(val active: Boolean = false, val progress: Float = 0f)

    companion object {
        const val DEMO_NIGHTS = 30
    }
}
