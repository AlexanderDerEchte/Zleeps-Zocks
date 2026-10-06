package at.zocks.zleep.ui.massage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.zocks.zleep.R
import at.zocks.zleep.domain.massage.BuiltInMassagePrograms
import at.zocks.zleep.domain.massage.MassageController
import at.zocks.zleep.domain.massage.MassageProgram
import at.zocks.zleep.domain.massage.MassageProgramType
import at.zocks.zleep.domain.massage.MassageSession
import at.zocks.zleep.domain.massage.MassageTempo
import at.zocks.zleep.domain.model.MassagePreferences
import at.zocks.zleep.domain.model.MassageZone
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.domain.repository.MassageProgramRepository
import at.zocks.zleep.domain.repository.SettingsRepository
import at.zocks.zleep.ui.components.UiText
import at.zocks.zleep.ui.format.sideLabelRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

data class MassageUiState(
    val loading: Boolean = true,
    /** Favoriten zuerst, dann eingebaute, dann eigene Programme. */
    val programs: List<MassageProgram> = emptyList(),
    val prefs: MassagePreferences = MassagePreferences(),
    val session: MassageSession? = null,
    val now: Instant = Instant.EPOCH,
    val editorOpen: Boolean = false,
    val userMessage: UiText? = null,
) {
    val selected: MassageProgram? get() = programs.firstOrNull { it.id == prefs.programId } ?: programs.firstOrNull()
    val remaining: Duration? get() = session?.let { Duration.between(now, it.endsAt).coerceAtLeast(Duration.ZERO) }

    fun isFavorite(program: MassageProgram) = program.id in prefs.favorites

    companion object {
        val DURATION_OPTIONS = listOf(5, 10, 15, 20, 30)
        const val INTENSITY_STEP = 10
    }
}

sealed interface MassageEvent {
    data class Select(val programId: String) : MassageEvent
    data class ToggleFavorite(val programId: String) : MassageEvent
    data class ChangeIntensity(val steps: Int) : MassageEvent
    data class SetDuration(val minutes: Int) : MassageEvent
    data class SetSide(val side: SockSide) : MassageEvent
    data object Start : MassageEvent
    data object Stop : MassageEvent
    data object OpenEditor : MassageEvent
    data object CloseEditor : MassageEvent
    data class SaveCustom(
        val name: String,
        val type: MassageProgramType,
        val tempo: MassageTempo,
        val zones: Set<MassageZone>,
    ) : MassageEvent
    data class DeleteCustom(val programId: String) : MassageEvent
    data object MessageShown : MassageEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MassageViewModel @Inject constructor(
    private val controller: MassageController,
    private val settingsRepository: SettingsRepository,
    private val programRepository: MassageProgramRepository,
    private val clock: Clock,
) : ViewModel() {

    private val local = MutableStateFlow(LocalState())

    private val now: Flow<Instant> = controller.state.map { it.session != null }.distinctUntilChanged().flatMapLatest { running ->
        if (running) {
            flow {
                while (true) {
                    emit(clock.instant())
                    delay(TICK_MS)
                }
            }
        } else {
            flowOf(clock.instant())
        }
    }

    val uiState: StateFlow<MassageUiState> = combine(
        settingsRepository.settings,
        programRepository.observeCustomPrograms(),
        controller.state,
        now,
        local,
    ) { settings, custom, control, time, localState ->
        val favorites = settings.massage.favorites
        MassageUiState(
            loading = false,
            programs = (BuiltInMassagePrograms.all + custom).sortedBy { it.id !in favorites },
            prefs = settings.massage,
            session = control.session,
            now = time,
            editorOpen = localState.editorOpen,
            userMessage = localState.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MassageUiState())

    fun onEvent(event: MassageEvent) {
        when (event) {
            is MassageEvent.Select -> {
                val program = uiState.value.programs.firstOrNull { it.id == event.programId } ?: return
                // Beim Wechsel die empfohlene Intensität und Dauer des Programms übernehmen.
                updatePrefs { it.copy(programId = program.id, intensity = program.intensity, durationMinutes = program.durationMinutes) }
            }
            is MassageEvent.ToggleFavorite -> updatePrefs {
                it.copy(favorites = if (event.programId in it.favorites) it.favorites - event.programId else it.favorites + event.programId)
            }
            is MassageEvent.ChangeIntensity -> launch {
                // Atomar auf dem gespeicherten Wert, damit schnelles Tippen keine Schritte verliert.
                settingsRepository.update { settings ->
                    val intensity = (settings.massage.intensity + event.steps * MassageUiState.INTENSITY_STEP)
                        .coerceIn(MassageController.MIN_INTENSITY, 100)
                    settings.copy(massage = settings.massage.copy(intensity = intensity))
                }
                controller.setIntensity(settingsRepository.settings.first().massage.intensity)
            }
            is MassageEvent.SetDuration -> updatePrefs { it.copy(durationMinutes = event.minutes) }
            is MassageEvent.SetSide -> updatePrefs { it.copy(side = event.side) }
            MassageEvent.Start -> launch {
                val state = uiState.value
                val program = state.selected ?: return@launch
                val started = controller.start(program, state.prefs.intensity, state.prefs.durationMinutes, state.prefs.side)
                val skipped = controller.state.value.skipped
                when {
                    !started -> message(UiText.of(R.string.snackbar_pair_first))
                    skipped.isNotEmpty() -> message(UiText.of(R.string.massage_skipped, UiText.of(sideLabelRes(skipped.first()))))
                }
            }
            MassageEvent.Stop -> launch { controller.stop(soft = true) }
            MassageEvent.OpenEditor -> local.update { it.copy(editorOpen = true) }
            MassageEvent.CloseEditor -> local.update { it.copy(editorOpen = false) }
            is MassageEvent.SaveCustom -> launch {
                val prefs = uiState.value.prefs
                val id = programRepository.save(
                    MassageProgram(
                        id = MassageProgram.CUSTOM_PREFIX,
                        type = event.type,
                        name = event.name,
                        intensity = prefs.intensity,
                        durationMinutes = prefs.durationMinutes,
                        tempo = event.tempo,
                        zones = event.zones,
                    ),
                )
                updatePrefs { it.copy(programId = id, favorites = it.favorites + id) }
                local.update { it.copy(editorOpen = false) }
                message(UiText.of(R.string.massage_custom_saved))
            }
            is MassageEvent.DeleteCustom -> launch {
                programRepository.delete(event.programId)
                updatePrefs {
                    it.copy(
                        favorites = it.favorites - event.programId,
                        programId = if (it.programId == event.programId) BuiltInMassagePrograms.RELAX.id else it.programId,
                    )
                }
                message(UiText.of(R.string.massage_custom_deleted))
            }
            MassageEvent.MessageShown -> local.update { it.copy(message = null) }
        }
    }

    private fun message(text: UiText) = local.update { it.copy(message = text) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { message(UiText.of(R.string.error_generic)) }
        }
    }

    private fun updatePrefs(transform: (MassagePreferences) -> MassagePreferences) = launch {
        settingsRepository.update { it.copy(massage = transform(it.massage)) }
    }

    private data class LocalState(val editorOpen: Boolean = false, val message: UiText? = null)

    private companion object {
        const val TICK_MS = 15_000L
    }
}
