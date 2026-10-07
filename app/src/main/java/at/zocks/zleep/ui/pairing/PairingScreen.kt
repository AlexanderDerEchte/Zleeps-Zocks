package at.zocks.zleep.ui.pairing

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.zocks.zleep.R
import at.zocks.zleep.domain.device.BluetoothAvailability
import at.zocks.zleep.domain.device.DiscoveredSock
import at.zocks.zleep.domain.model.PairedSocks
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.ui.components.DetailTopBar
import at.zocks.zleep.ui.components.EmptyState
import at.zocks.zleep.ui.components.ErrorState
import at.zocks.zleep.ui.components.LoadingState
import at.zocks.zleep.ui.components.ScreenColumn
import at.zocks.zleep.ui.components.SockStatusView
import at.zocks.zleep.ui.components.UserMessageEffect
import at.zocks.zleep.ui.components.ZocksCard
import at.zocks.zleep.ui.theme.Dimens
import at.zocks.zleep.ui.theme.ZocksTheme

/** Eigener Bildschirm zum Koppeln (aus Start, Steuerung und Einstellungen). */
@Composable
fun PairingRoute(onBack: () -> Unit, viewModel: PairingViewModel = hiltViewModel()) {
    Scaffold(
        topBar = { DetailTopBar(stringResource(R.string.pairing_title), onBack) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        modifier = Modifier.testTag("screen_pairing"),
    ) { padding ->
        Box(Modifier.padding(padding)) {
            ScreenColumn(title = null) { PairingPanel(viewModel) }
        }
    }
}

/** Koppeln samt Berechtigungen – auch eingebettet in die Einrichtung. */
@Composable
fun PairingPanel(viewModel: PairingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    UserMessageEffect(state.userMessage) { viewModel.onEvent(PairingEvent.MessageShown) }
    // Nach Rückkehr aus Freigabe-Dialog oder Systemeinstellungen neu prüfen.
    LifecycleResumeEffect(Unit) {
        viewModel.onEvent(PairingEvent.Refresh)
        onPauseOrDispose { }
    }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        // Abgelehnt und Android fragt nicht mehr nach: nur noch über die App-Einstellungen.
        permanentlyDenied = result.values.any { !it } && activity != null &&
            result.filterValues { !it }.keys.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        viewModel.onEvent(PairingEvent.Refresh)
    }
    val enableBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onEvent(PairingEvent.Refresh)
    }
    PairingContent(
        state = state,
        onEvent = viewModel::onEvent,
        actions = PairingActions(
            requestPermissions = {
                if (permanentlyDenied) context.openAppSettings() else permissions.launch(state.permissions.toTypedArray())
            },
            enableBluetooth = {
                if (context.canRequestBluetoothEnable()) {
                    runCatching { enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                        .onFailure { context.openBluetoothSettings() }
                } else {
                    context.openBluetoothSettings()
                }
            },
        ),
        permanentlyDenied = permanentlyDenied,
    )
}

/** Was der Bildschirm im System auslöst. */
data class PairingActions(
    val requestPermissions: () -> Unit = {},
    val enableBluetooth: () -> Unit = {},
)

@Composable
fun PairingContent(
    state: PairingUiState,
    onEvent: (PairingEvent) -> Unit,
    actions: PairingActions,
    modifier: Modifier = Modifier,
    permanentlyDenied: Boolean = false,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL)) {
        if (state.loading) {
            LoadingState()
            return@Column
        }
        when (state.availability) {
            BluetoothAvailability.UNSUPPORTED -> EmptyState(
                icon = Icons.Outlined.BluetoothDisabled,
                title = stringResource(R.string.pairing_unsupported_title),
                body = stringResource(R.string.pairing_unsupported_body),
                modifier = Modifier.testTag("pairing_unsupported"),
            )
            BluetoothAvailability.NO_PERMISSION -> EmptyState(
                icon = Icons.Outlined.Bluetooth,
                title = stringResource(R.string.pairing_permission_title),
                body = permissionExplanation(permanentlyDenied),
                actionLabel = stringResource(if (permanentlyDenied) R.string.action_open_app_settings else R.string.action_allow),
                onAction = actions.requestPermissions,
                modifier = Modifier.testTag("pairing_permission"),
            )
            BluetoothAvailability.OFF -> EmptyState(
                icon = Icons.Outlined.BluetoothDisabled,
                title = stringResource(R.string.pairing_bluetooth_off_title),
                body = stringResource(R.string.pairing_bluetooth_off_body),
                actionLabel = stringResource(R.string.action_enable_bluetooth),
                onAction = actions.enableBluetooth,
                modifier = Modifier.testTag("pairing_bluetooth_off"),
            )
            BluetoothAvailability.READY -> {
                PairedCard(state, onEvent)
                NearbyCard(state, onEvent)
            }
        }
    }
}

@Composable
private fun permissionExplanation(permanentlyDenied: Boolean): String {
    val body = stringResource(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) R.string.pairing_permission_body else R.string.pairing_permission_body_location,
    )
    return if (permanentlyDenied) body + "\n\n" + stringResource(R.string.pairing_permission_denied) else body
}

@Composable
private fun PairedCard(state: PairingUiState, onEvent: (PairingEvent) -> Unit) {
    ZocksCard(title = stringResource(R.string.pairing_paired_title), modifier = Modifier.testTag("pairing_paired")) {
        SockSide.entries.filter { it != SockSide.BOTH }.forEach { side ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimens.SpaceXs),
            ) {
                if (state.paired.of(side) != null) {
                    SockStatusView(state.pairStatus?.of(side), side, Modifier.weight(1f))
                    TextButton(
                        onClick = { onEvent(PairingEvent.Unpair(side)) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("unpair_${side.name.lowercase()}"),
                    ) { Text(stringResource(R.string.action_unpair)) }
                } else {
                    val sideLabel = sideLabel(side)
                    Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                        Text(sideLabel, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.pairing_not_paired),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        val status = state.pairStatus
        if (!state.paired.isEmpty && status?.bothConnected != true) {
            Button(
                onClick = { onEvent(PairingEvent.Connect) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.SpaceM)
                    .heightIn(min = Dimens.ThumbTarget)
                    .testTag("pairing_connect"),
            ) { Text(stringResource(R.string.action_connect_socks), style = MaterialTheme.typography.labelLarge) }
        } else if (status?.bothConnected == true) {
            Text(
                stringResource(R.string.onboarding_pair_done),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = Dimens.SpaceM)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("pairing_done"),
            )
        }
    }
}

@Composable
private fun NearbyCard(state: PairingUiState, onEvent: (PairingEvent) -> Unit) {
    ZocksCard(title = stringResource(R.string.pairing_nearby_title), modifier = Modifier.testTag("pairing_nearby")) {
        when {
            state.phase == ScanPhase.FAILED -> ErrorState(
                message = stringResource(R.string.pairing_scan_failed),
                onRetry = { onEvent(PairingEvent.Scan) },
            )
            state.phase == ScanPhase.DONE && state.found.isEmpty() -> EmptyState(
                icon = Icons.Outlined.SearchOff,
                title = stringResource(R.string.pairing_none_title),
                body = stringResource(R.string.pairing_none_body),
                actionLabel = stringResource(R.string.action_scan_again),
                onAction = { onEvent(PairingEvent.Scan) },
                modifier = Modifier.testTag("pairing_none"),
            )
            else -> {
                if (state.phase == ScanPhase.SCANNING) {
                    Text(
                        stringResource(R.string.pairing_scanning),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    LinearProgressIndicator(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = Dimens.SpaceS)
                            .testTag("pairing_scanning"),
                    )
                }
                if (state.found.isNotEmpty()) {
                    Text(
                        stringResource(R.string.pairing_assign_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.found.forEachIndexed { index, sock ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    FoundSockRow(index, sock, state.sideOf(sock.address), onEvent)
                }
                if (state.phase == ScanPhase.DONE) {
                    TextButton(
                        onClick = { onEvent(PairingEvent.Scan) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("pairing_scan_again"),
                    ) { Text(stringResource(R.string.action_scan_again)) }
                }
            }
        }
    }
}

@Composable
private fun FoundSockRow(index: Int, sock: DiscoveredSock, assigned: SockSide?, onEvent: (PairingEvent) -> Unit) {
    val name = sock.name ?: stringResource(R.string.pairing_sock_unnamed)
    val details = buildList {
        sock.side?.let { add(stringResource(R.string.pairing_reports_side, sideLabel(it))) }
        sock.batteryPercent?.let { add(stringResource(R.string.battery_level, it)) }
        add(stringResource(signalLabel(sock.rssi)))
    }.joinToString(" · ")
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.SpaceS)
            .testTag("found_sock_$index"),
    ) {
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.padding(top = Dimens.SpaceS), horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
            listOf(SockSide.LEFT, SockSide.RIGHT).forEach { side ->
                val label = sideLabel(side)
                val description = stringResource(R.string.pairing_assign_description, name, label)
                val isAssigned = assigned == side
                val buttonModifier = Modifier
                    .weight(1f)
                    .heightIn(min = Dimens.ThumbTarget)
                    .semantics {
                        contentDescription = description
                        selected = isAssigned
                    }
                    .testTag("assign_${side.name.lowercase()}_$index")
                if (isAssigned) {
                    // Häkchen statt nur Farbe: die Auswahl ist auch ohne Farbsehen erkennbar.
                    FilledTonalButton(onClick = {}, modifier = buttonModifier) {
                        Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.padding(end = Dimens.SpaceS))
                        Text(label)
                    }
                } else {
                    OutlinedButton(onClick = { onEvent(PairingEvent.Assign(sock.address, side)) }, modifier = buttonModifier) {
                        Text(label)
                    }
                }
            }
        }
    }
}

@Composable
private fun sideLabel(side: SockSide): String =
    stringResource(if (side == SockSide.RIGHT) R.string.sock_right else R.string.sock_left)

private fun signalLabel(rssi: Int): Int = when {
    rssi >= STRONG_SIGNAL_DBM -> R.string.pairing_signal_strong
    rssi >= MEDIUM_SIGNAL_DBM -> R.string.pairing_signal_medium
    else -> R.string.pairing_signal_weak
}

private const val STRONG_SIGNAL_DBM = -60
private const val MEDIUM_SIGNAL_DBM = -80

/** Ab Android 13 darf die App Bluetooth nur mit „Geräte in der Nähe“ einschalten lassen. */
private fun Context.canRequestBluetoothEnable(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
    ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

private fun Context.openBluetoothSettings() {
    runCatching { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).withNewTaskIfNeeded(this)) }
}

private fun Context.openAppSettings() {
    runCatching {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()).withNewTaskIfNeeded(this),
        )
    }
}

private fun Intent.withNewTaskIfNeeded(context: Context): Intent =
    if (context is Activity) this else addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

@Preview
@Composable
private fun PairingPreview() {
    ZocksTheme {
        PairingContent(
            state = PairingUiState(
                loading = false,
                phase = ScanPhase.SCANNING,
                found = listOf(
                    DiscoveredSock("AA:BB:CC:DD:EE:01", "Zocks L", SockSide.LEFT, 80, -55),
                    DiscoveredSock("AA:BB:CC:DD:EE:02", null, null, null, -85),
                ),
                paired = PairedSocks(left = "AA:BB:CC:DD:EE:01"),
            ),
            onEvent = {},
            actions = PairingActions(),
        )
    }
}
