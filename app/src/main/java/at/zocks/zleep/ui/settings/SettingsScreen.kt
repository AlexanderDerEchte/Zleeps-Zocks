package at.zocks.zleep.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.health.connect.client.PermissionController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.zocks.zleep.BuildConfig
import at.zocks.zleep.R
import at.zocks.zleep.domain.alarm.AlarmMethod
import at.zocks.zleep.domain.health.HealthAvailability
import at.zocks.zleep.domain.model.AlarmPreferences
import at.zocks.zleep.domain.model.DeviceMode
import at.zocks.zleep.domain.model.needsPairing
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.domain.model.TemperatureUnit
import at.zocks.zleep.ui.components.BigSegmentedChoice
import at.zocks.zleep.ui.components.BigStepper
import at.zocks.zleep.ui.components.Labeled
import at.zocks.zleep.ui.components.LoadingState
import at.zocks.zleep.ui.components.ScreenColumn
import at.zocks.zleep.ui.components.SockStatusView
import at.zocks.zleep.ui.components.SwitchRow
import at.zocks.zleep.ui.components.TimeSettingRow
import at.zocks.zleep.ui.components.UserMessageEffect
import at.zocks.zleep.ui.components.ZocksCard
import at.zocks.zleep.ui.format.formatDuration
import at.zocks.zleep.ui.format.tagLabel
import at.zocks.zleep.ui.theme.Dimens
import java.time.Duration
import java.time.LocalDate

@Composable
fun SettingsRoute(onOpenDeveloperOptions: () -> Unit, onOpenPairing: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    UserMessageEffect(state.userMessage) { viewModel.onEvent(SettingsEvent.MessageShown) }
    // Nach Rückkehr aus Systemeinstellungen (Wecker-Freigabe, Health Connect) neu prüfen.
    LifecycleResumeEffect(Unit) {
        viewModel.onEvent(SettingsEvent.Refresh)
        onPauseOrDispose { }
    }
    val resources = LocalResources.current
    val tagNamer: TagNamer = { tag -> tagLabel(resources, tag) }
    val exportNights = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CSV)) { uri ->
        if (uri != null) viewModel.onEvent(SettingsEvent.Export(ExportKind.NIGHTS, uri.toString(), tagNamer))
    }
    val exportMeasurements = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CSV)) { uri ->
        if (uri != null) viewModel.onEvent(SettingsEvent.Export(ExportKind.MEASUREMENTS, uri.toString(), tagNamer))
    }
    val healthPermissions = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        viewModel.onEvent(SettingsEvent.Refresh)
    }
    SettingsScreen(
        state = state,
        onEvent = { event ->
            // Ohne gekoppelte Socke führt „Verbinden“ zuerst zum Koppeln.
            if (event == SettingsEvent.ConnectSocks && state.settings.needsPairing) onOpenPairing() else viewModel.onEvent(event)
        },
        actions = SettingsActions(
            exportNights = { exportNights.launch("zocks-zleep-naechte-${LocalDate.now()}.csv") },
            exportMeasurements = { exportMeasurements.launch("zocks-zleep-messwerte-${LocalDate.now()}.csv") },
            connectHealth = { healthPermissions.launch(state.healthPermissions) },
            installHealth = { context.openHealthConnectInStore() },
            allowExactAlarms = { context.openExactAlarmSettings() },
            openNotificationSettings = { context.openNotificationSettings() },
            openDeveloperOptions = onOpenDeveloperOptions,
            openPairing = onOpenPairing,
        ),
    )
}

/** Was der Bildschirm im System auslöst (Dateiauswahl, Freigaben, Store). */
data class SettingsActions(
    val exportNights: () -> Unit = {},
    val exportMeasurements: () -> Unit = {},
    val connectHealth: () -> Unit = {},
    val installHealth: () -> Unit = {},
    val allowExactAlarms: () -> Unit = {},
    val openNotificationSettings: () -> Unit = {},
    val openDeveloperOptions: () -> Unit = {},
    val openPairing: () -> Unit = {},
)

@Composable
fun SettingsScreen(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit, actions: SettingsActions, modifier: Modifier = Modifier) {
    ScreenColumn(title = stringResource(R.string.nav_settings), modifier = modifier.testTag("screen_settings")) {
        if (state.loading) {
            LoadingState()
            return@ScreenColumn
        }
        GeneralCard(state, onEvent, actions)
        AlarmCard(state, onEvent, actions)
        DevicesCard(state, onEvent, actions)
        DataCard(state, onEvent, actions)
        AboutCard(state, onEvent, actions)
        WellnessNotice()
    }
}

@Composable
private fun GeneralCard(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit, actions: SettingsActions) {
    val settings = state.settings
    ZocksCard(title = stringResource(R.string.settings_section_general)) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
            Labeled(stringResource(R.string.settings_temperature)) {
                BigSegmentedChoice(
                    options = TemperatureUnit.entries,
                    selected = settings.temperatureUnit,
                    label = { stringResource(if (it == TemperatureUnit.CELSIUS) R.string.unit_celsius else R.string.unit_fahrenheit) },
                    onSelect = { onEvent(SettingsEvent.SetTemperatureUnit(it)) },
                    testTagPrefix = "settings_unit",
                )
            }
            SwitchRow(
                title = stringResource(R.string.settings_24h),
                body = null,
                checked = settings.use24HourClock,
                onCheckedChange = { onEvent(SettingsEvent.SetUse24h(it)) },
                testTag = "settings_24h",
            )
            Labeled(stringResource(R.string.settings_sleep_goal)) {
                BigStepper(
                    value = formatDuration(Duration.ofMinutes(settings.sleepGoalMinutes.toLong())),
                    decreaseLabel = stringResource(R.string.settings_sleep_goal_shorter),
                    increaseLabel = stringResource(R.string.settings_sleep_goal_longer),
                    onDecrease = { onEvent(SettingsEvent.SetSleepGoal(settings.sleepGoalMinutes - GOAL_STEP)) },
                    onIncrease = { onEvent(SettingsEvent.SetSleepGoal(settings.sleepGoalMinutes + GOAL_STEP)) },
                    testTag = "settings_goal",
                    valueStyle = MaterialTheme.typography.headlineLarge,
                )
            }
            TimeSettingRow(
                label = stringResource(R.string.settings_bedtime),
                time = settings.bedtime,
                use24h = settings.use24HourClock,
                onChange = { onEvent(SettingsEvent.SetBedtime(it)) },
                testTag = "settings_bedtime",
            )
            TimeSettingRow(
                label = stringResource(R.string.settings_wake_time),
                time = settings.wakeTime,
                use24h = settings.use24HourClock,
                onChange = { onEvent(SettingsEvent.SetWakeTime(it)) },
                testTag = "settings_wake_time",
            )
            SettingsRow(
                Icons.Outlined.Notifications,
                stringResource(R.string.settings_notifications),
                stringResource(R.string.settings_notifications_body),
                onClick = actions.openNotificationSettings,
            )
        }
    }
}

@Composable
private fun AlarmCard(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit, actions: SettingsActions) {
    val alarm = state.settings.alarm
    ZocksCard(title = stringResource(R.string.settings_section_alarm), modifier = Modifier.testTag("settings_alarm")) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
            SwitchRow(
                title = stringResource(R.string.start_alarm_title),
                body = stringResource(R.string.alarm_enabled_body),
                checked = alarm.enabled,
                onCheckedChange = { onEvent(SettingsEvent.SetAlarmEnabled(it)) },
                testTag = "settings_alarm_enabled",
            )
            if (alarm.enabled) {
                Labeled(stringResource(R.string.alarm_window)) {
                    BigSegmentedChoice(
                        options = AlarmPreferences.WINDOW_OPTIONS,
                        selected = alarm.windowMinutes,
                        label = { stringResource(R.string.duration_m, it) },
                        onSelect = { onEvent(SettingsEvent.SetAlarmWindow(it)) },
                        testTagPrefix = "settings_alarm_window",
                    )
                }
                Labeled(stringResource(R.string.alarm_method)) {
                    Column(Modifier.selectableGroup()) {
                        AlarmMethod.entries.forEach { method ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .selectable(selected = alarm.method == method, role = Role.RadioButton) {
                                        onEvent(SettingsEvent.SetAlarmMethod(method))
                                    }
                                    .testTag("settings_alarm_method_${method.name.lowercase()}"),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = alarm.method == method, onClick = null)
                                Text(alarmMethodLabel(method), modifier = Modifier.padding(start = Dimens.SpaceM))
                            }
                        }
                    }
                }
                if (!state.exactAlarmAllowed) {
                    Text(stringResource(R.string.alarm_exact_missing), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(onClick = actions.allowExactAlarms, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.action_allow))
                    }
                }
            }
        }
    }
}

@Composable
private fun alarmMethodLabel(method: AlarmMethod): String = stringResource(
    when (method) {
        AlarmMethod.SOUND -> R.string.alarm_method_sound
        AlarmMethod.MASSAGE -> R.string.alarm_method_massage
        AlarmMethod.MASSAGE_THEN_SOUND -> R.string.alarm_method_both
    },
)

@Composable
private fun DevicesCard(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit, actions: SettingsActions) {
    val connected = state.pairStatus?.anyConnected == true
    ZocksCard(title = stringResource(R.string.settings_section_devices)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
            SockStatusView(state.pairStatus?.left, SockSide.LEFT, Modifier.weight(1f))
            SockStatusView(state.pairStatus?.right, SockSide.RIGHT, Modifier.weight(1f))
        }
        OutlinedButton(
            onClick = { onEvent(if (connected) SettingsEvent.DisconnectSocks else SettingsEvent.ConnectSocks) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.SpaceL)
                .heightIn(min = Dimens.ThumbTarget)
                .testTag(if (connected) "settings_disconnect" else "settings_connect"),
        ) {
            Text(
                stringResource(
                    when {
                        connected -> R.string.action_disconnect_socks
                        state.settings.needsPairing -> R.string.action_pair_socks
                        else -> R.string.action_connect_socks
                    },
                ),
            )
        }
        if (state.settings.deviceMode == DeviceMode.BLE) {
            SettingsRow(
                Icons.Outlined.Bluetooth,
                stringResource(R.string.settings_pair_socks),
                stringResource(R.string.settings_pair_socks_value),
                onClick = actions.openPairing,
                testTag = "settings_pair",
            )
        }
        SettingsRow(
            Icons.Outlined.Code,
            stringResource(R.string.settings_device_mode),
            stringResource(
                if (state.settings.deviceMode == DeviceMode.SIMULATOR) R.string.settings_device_mode_simulator else R.string.settings_device_mode_ble,
            ),
            onClick = actions.openDeveloperOptions.takeIf { state.developerOptionsVisible },
        )
    }
}

@Composable
private fun DataCard(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit, actions: SettingsActions) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    ZocksCard(title = stringResource(R.string.settings_section_data), modifier = Modifier.testTag("settings_data")) {
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = Dimens.SpaceS))
        SettingsRow(
            Icons.Outlined.FileDownload,
            stringResource(R.string.settings_export_nights),
            stringResource(R.string.settings_export_nights_body),
            onClick = actions.exportNights.takeUnless { state.busy },
            testTag = "settings_export_nights",
        )
        SettingsRow(
            Icons.Outlined.FileDownload,
            stringResource(R.string.settings_export_measurements),
            stringResource(R.string.settings_export_measurements_body),
            onClick = actions.exportMeasurements.takeUnless { state.busy },
            testTag = "settings_export_measurements",
        )
        HealthConnectSection(state, onEvent, actions)
        SettingsRow(
            Icons.Outlined.DeleteOutline,
            stringResource(R.string.settings_delete_data),
            stringResource(R.string.settings_delete_body),
            iconTint = MaterialTheme.colorScheme.error,
            onClick = { confirmDelete = true },
            testTag = "settings_delete",
        )
        HintRow(Icons.Outlined.CloudOff, stringResource(R.string.settings_offline_hint))
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEvent(SettingsEvent.DeleteAllNights)
                        confirmDelete = false
                    },
                    modifier = Modifier.testTag("confirm_delete"),
                ) { Text(stringResource(R.string.delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun HealthConnectSection(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit, actions: SettingsActions) {
    val (status, action) = when {
        state.healthAvailability == HealthAvailability.NOT_SUPPORTED -> stringResource(R.string.hc_not_supported) to null
        state.healthAvailability == HealthAvailability.NOT_INSTALLED ->
            stringResource(R.string.hc_not_installed) to (stringResource(R.string.hc_install) to actions.installHealth)
        state.healthAvailability == HealthAvailability.UPDATE_REQUIRED ->
            stringResource(R.string.hc_update_required) to (stringResource(R.string.hc_install) to actions.installHealth)
        !state.healthGranted -> stringResource(R.string.hc_not_connected) to (stringResource(R.string.hc_connect) to actions.connectHealth)
        else -> stringResource(R.string.hc_connected) to null
    }
    Column(Modifier.testTag("settings_health_connect")) {
        SettingsRow(Icons.Outlined.FavoriteBorder, stringResource(R.string.settings_health_connect), status)
        action?.let { (label, onClick) ->
            FilledTonalButton(
                onClick = onClick,
                modifier = Modifier
                    .padding(start = Dimens.SpaceL)
                    .heightIn(min = 48.dp),
            ) { Text(label) }
        }
        if (state.healthGranted) {
            SwitchRow(
                title = stringResource(R.string.hc_auto),
                body = null,
                checked = state.settings.healthConnectAutoExport,
                onCheckedChange = { onEvent(SettingsEvent.SetHealthAutoExport(it)) },
                testTag = "settings_hc_auto",
            )
            TextButton(onClick = { onEvent(SettingsEvent.ExportToHealthConnect) }, enabled = !state.busy) {
                Text(stringResource(R.string.hc_export_now))
            }
        }
    }
}

@Composable
private fun AboutCard(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit, actions: SettingsActions) {
    // Wie bei Android: siebenmal auf die Version tippen schaltet die Entwickleroptionen frei.
    var versionTaps by rememberSaveable { mutableIntStateOf(0) }
    ZocksCard(title = stringResource(R.string.settings_section_about)) {
        SettingsRow(
            Icons.Outlined.Info,
            stringResource(R.string.settings_version),
            BuildConfig.VERSION_NAME,
            onClick = if (state.developerOptionsVisible) {
                null
            } else {
                {
                    versionTaps++
                    if (versionTaps >= DEVELOPER_TAPS) onEvent(SettingsEvent.UnlockDeveloperOptions)
                }
            },
            showArrow = false,
            testTag = "settings_version",
        )
        SettingsRow(
            Icons.Outlined.Replay,
            stringResource(R.string.settings_onboarding_again),
            null,
            onClick = { onEvent(SettingsEvent.ShowOnboardingAgain) },
            testTag = "settings_onboarding_again",
        )
        if (state.developerOptionsVisible) {
            SettingsRow(
                Icons.Outlined.Code,
                stringResource(R.string.settings_developer),
                stringResource(R.string.dev_mode_simulator_body),
                onClick = actions.openDeveloperOptions,
                testTag = "settings_developer",
            )
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    value: String?,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    testTag: String? = null,
    showArrow: Boolean = onClick != null,
) {
    val base = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = value?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        leadingContent = { Icon(icon, contentDescription = null, tint = iconTint) },
        trailingContent = if (showArrow) {
            { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
        } else {
            null
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = base
            .semantics(mergeDescendants = true) {}
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    )
}

@Composable
private fun HintRow(icon: ImageVector, text: String) {
    Row(Modifier.padding(top = Dimens.SpaceS), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Dimens.SpaceS),
        )
    }
}

/** Pflichthinweis: Wellnessprodukt, kein Medizinprodukt. */
@Composable
private fun WellnessNotice() {
    ZocksCard(modifier = Modifier.testTag("wellness_notice")) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Icon(Icons.Outlined.Spa, null, tint = MaterialTheme.colorScheme.tertiary)
            Text(
                text = stringResource(R.string.wellness_title) + "\n" + stringResource(R.string.wellness_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Dimens.SpaceM),
            )
        }
    }
}

private const val CSV = "text/csv"
private const val DEVELOPER_TAPS = 7
private const val GOAL_STEP = 15
private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

private fun Context.openHealthConnectInStore() {
    val store = Intent(Intent.ACTION_VIEW, "market://details?id=$HEALTH_CONNECT_PACKAGE&url=healthconnect%3A%2F%2Fonboarding".toUri())
    val web = Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE".toUri())
    try {
        startActivity(store.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        runCatching { startActivity(web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

private fun Context.openExactAlarmSettings() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    runCatching {
        startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:$packageName".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun Context.openNotificationSettings() {
    runCatching {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
