package at.zocks.zleep.ui.pairing

import at.zocks.zleep.domain.device.BluetoothAvailability
import at.zocks.zleep.domain.device.DiscoveredSock
import at.zocks.zleep.domain.device.SockScanner
import at.zocks.zleep.domain.model.DeviceMode
import at.zocks.zleep.domain.model.PairedSocks
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.domain.model.UserSettings
import at.zocks.zleep.testing.FakePairProvider
import at.zocks.zleep.testing.FakeSettingsRepository
import at.zocks.zleep.testing.FakeSockDevice
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PairingViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeScanner : SockScanner {
        var availability = BluetoothAvailability.READY
        var fail = false
        var scans = 0
        var keepScanning = false
        override val requiredPermissions = listOf("android.permission.BLUETOOTH_SCAN")
        override fun availability() = availability
        override fun scan(): Flow<List<DiscoveredSock>> = flow {
            scans++
            if (fail) error("Scan fehlgeschlagen")
            emit(listOf(DiscoveredSock("A", "Zocks", SockSide.LEFT, 90, -50), DiscoveredSock("B", null, null, null, -70)))
            if (keepScanning) awaitCancellation()
        }
    }

    private val scanner = FakeScanner()
    private val settings = FakeSettingsRepository(UserSettings(deviceMode = DeviceMode.BLE))
    private val pair = FakePairProvider(FakeSockDevice(SockSide.LEFT), FakeSockDevice(SockSide.RIGHT))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = PairingViewModel(scanner, settings, pair)

    @Test
    fun refreshScansOnceWhenReady() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.onEvent(PairingEvent.Refresh)
        advanceUntilIdle()
        assertThat(vm.uiState.value.phase).isEqualTo(ScanPhase.DONE)
        assertThat(vm.uiState.value.found.map { it.address }).containsExactly("A", "B").inOrder()
        assertThat(vm.uiState.value.permissions).containsExactly("android.permission.BLUETOOTH_SCAN")

        // Erneutes Öffnen (onResume) sucht nicht von selbst noch einmal.
        vm.onEvent(PairingEvent.Refresh)
        advanceUntilIdle()
        assertThat(scanner.scans).isEqualTo(1)
    }

    @Test
    fun withoutPermissionNothingIsScanned() = runTest(dispatcher) {
        scanner.availability = BluetoothAvailability.NO_PERMISSION
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.onEvent(PairingEvent.Refresh)
        vm.onEvent(PairingEvent.Scan)
        advanceUntilIdle()
        assertThat(vm.uiState.value.availability).isEqualTo(BluetoothAvailability.NO_PERMISSION)
        assertThat(vm.uiState.value.phase).isEqualTo(ScanPhase.IDLE)
        assertThat(scanner.scans).isEqualTo(0)
    }

    @Test
    fun failedScanShowsError() = runTest(dispatcher) {
        scanner.fail = true
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.onEvent(PairingEvent.Scan)
        advanceUntilIdle()
        assertThat(vm.uiState.value.phase).isEqualTo(ScanPhase.FAILED)
    }

    @Test
    fun scanStopsAfterTimeout() = runTest(dispatcher) {
        scanner.keepScanning = true
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.onEvent(PairingEvent.Scan)
        advanceTimeBy(PairingViewModel.SCAN_DURATION_MS - 1)
        assertThat(vm.uiState.value.phase).isEqualTo(ScanPhase.SCANNING)
        advanceTimeBy(2)
        assertThat(vm.uiState.value.phase).isEqualTo(ScanPhase.DONE)
        assertThat(vm.uiState.value.found).hasSize(2)
    }

    @Test
    fun assigningASockToTheOtherSideMovesIt() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.onEvent(PairingEvent.Assign("A", SockSide.LEFT))
        advanceUntilIdle()
        assertThat(settings.settings.value.pairedSocks).isEqualTo(PairedSocks(left = "A"))

        vm.onEvent(PairingEvent.Assign("A", SockSide.RIGHT))
        advanceUntilIdle()
        assertThat(settings.settings.value.pairedSocks).isEqualTo(PairedSocks(right = "A"))
        assertThat(vm.uiState.value.sideOf("A")).isEqualTo(SockSide.RIGHT)

        vm.onEvent(PairingEvent.Unpair(SockSide.RIGHT))
        advanceUntilIdle()
        assertThat(settings.settings.value.pairedSocks.isEmpty).isTrue()
    }

    @Test
    fun connectStopsTheScanAndConnectsThePair() = runTest(dispatcher) {
        scanner.keepScanning = true
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.onEvent(PairingEvent.Scan)
        advanceTimeBy(1_000)
        vm.onEvent(PairingEvent.Connect)
        advanceUntilIdle()
        assertThat(vm.uiState.value.phase).isEqualTo(ScanPhase.DONE)
        assertThat(vm.uiState.value.pairStatus?.bothConnected).isTrue()
    }
}
