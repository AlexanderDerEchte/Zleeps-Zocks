package at.zocks.zleep.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.zocks.zleep.R
import at.zocks.zleep.domain.device.BluetoothAvailability
import at.zocks.zleep.domain.device.DiscoveredSock
import at.zocks.zleep.domain.device.PairStatus
import at.zocks.zleep.domain.device.SockPairProvider
import at.zocks.zleep.domain.device.SockScanner
import at.zocks.zleep.domain.model.PairedSocks
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** Wie weit die Suche ist. */
enum class ScanPhase { IDLE, SCANNING, DONE, FAILED }

data class PairingUiState(
    val loading: Boolean = true,
    val availability: BluetoothAvailability = BluetoothAvailability.READY,
    val permissions: List<String> = emptyList(),
    val phase: ScanPhase = ScanPhase.IDLE,
    val found: List<DiscoveredSock> = emptyList(),
    val paired: PairedSocks = PairedSocks(),
    val pairStatus: PairStatus? = null,
    val userMessage: Int? = null,
) {
    /** Gekoppelte Socken, die gerade nicht in Reichweite gefunden wurden, bleiben trotzdem sichtbar. */
    fun sideOf(address: String): SockSide? = when (address) {
        paired.left -> SockSide.LEFT
        paired.right -> SockSide.RIGHT
        else -> null
    }
}

sealed interface PairingEvent {
    /** Verfügbarkeit neu prüfen (nach Rückkehr aus Freigabe-Dialog oder Systemeinstellungen). */
    data object Refresh : PairingEvent
    data object Scan : PairingEvent
    data class Assign(val address: String, val side: SockSide) : PairingEvent
    data class Unpair(val side: SockSide) : PairingEvent
    data object Connect : PairingEvent
    data object MessageShown : PairingEvent
}

/**
 * Socken suchen und der linken bzw. rechten Seite zuordnen. Die Zuordnung landet in den
 * Einstellungen ([PairedSocks]); daraus baut der `SockPairProvider` das echte Paar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PairingViewModel @Inject constructor(
    private val scanner: SockScanner,
    private val settingsRepository: SettingsRepository,
    private val pairProvider: SockPairProvider,
) : ViewModel() {

    private data class Local(
        val availability: BluetoothAvailability,
        val phase: ScanPhase = ScanPhase.IDLE,
        val found: List<DiscoveredSock> = emptyList(),
        val message: Int? = null,
    )

    private val local = MutableStateFlow(Local(scanner.availability()))
    private var scanJob: Job? = null

    val uiState: StateFlow<PairingUiState> = combine(
        local,
        settingsRepository.settings,
        pairProvider.pair.flatMapLatest { it.status },
    ) { l, settings, status ->
        PairingUiState(
            loading = false,
            availability = l.availability,
            permissions = scanner.requiredPermissions,
            phase = l.phase,
            found = l.found,
            paired = settings.pairedSocks,
            pairStatus = status,
            userMessage = l.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PairingUiState())

    fun onEvent(event: PairingEvent) {
        when (event) {
            PairingEvent.Refresh -> refresh()
            PairingEvent.Scan -> scan()
            is PairingEvent.Assign -> viewModelScope.launch {
                settingsRepository.update { settings ->
                    // Eine Socke kann nur eine Seite sein: von der anderen Seite ggf. lösen.
                    val other = if (event.side == SockSide.LEFT) SockSide.RIGHT else SockSide.LEFT
                    val socks = settings.pairedSocks
                    val cleared = if (socks.of(other) == event.address) socks.with(other, null) else socks
                    settings.copy(pairedSocks = cleared.with(event.side, event.address))
                }
            }
            is PairingEvent.Unpair -> viewModelScope.launch {
                settingsRepository.update { it.copy(pairedSocks = it.pairedSocks.with(event.side, null)) }
            }
            PairingEvent.Connect -> viewModelScope.launch {
                stopScan()
                runCatching { pairProvider.pair.value.connect() }.onFailure { message(R.string.error_generic) }
            }
            PairingEvent.MessageShown -> message(null)
        }
    }

    private fun refresh() {
        val availability = scanner.availability()
        local.update { it.copy(availability = availability) }
        // Beim ersten Öffnen mit fertigem Bluetooth gleich suchen.
        if (availability == BluetoothAvailability.READY && local.value.phase == ScanPhase.IDLE) scan()
        if (availability != BluetoothAvailability.READY) stopScan()
    }

    private fun scan() {
        if (scanJob?.isActive == true) return
        val availability = scanner.availability()
        local.update { it.copy(availability = availability) }
        if (availability != BluetoothAvailability.READY) return
        local.update { it.copy(phase = ScanPhase.SCANNING, found = emptyList()) }
        scanJob = viewModelScope.launch {
            val phase = try {
                withTimeoutOrNull(SCAN_DURATION_MS) {
                    scanner.scan().collect { found -> local.update { it.copy(found = found) } }
                }
                ScanPhase.DONE
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ScanPhase.FAILED
            }
            local.update { it.copy(phase = phase) }
        }
    }

    private fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        local.update { if (it.phase == ScanPhase.SCANNING) it.copy(phase = ScanPhase.DONE) else it }
    }

    private fun message(id: Int?) = local.update { it.copy(message = id) }

    companion object {
        /** Länger als nötig suchen kostet Akku und blockiert andere Bluetooth-Vorgänge. */
        const val SCAN_DURATION_MS = 30_000L
    }
}
