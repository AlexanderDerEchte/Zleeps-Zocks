package at.zocks.zleep.ui.recording

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import at.zocks.zleep.R
import at.zocks.zleep.ui.components.BigActionButton
import at.zocks.zleep.ui.components.SwitchRow
import at.zocks.zleep.ui.format.currentLocale
import at.zocks.zleep.ui.format.formatDuration
import at.zocks.zleep.ui.format.formatLocalTime
import java.time.Duration
import java.time.LocalTime
import at.zocks.zleep.ui.theme.Dimens

/**
 * Checkliste vor der Nacht. Erklärt, wofür jede Berechtigung gebraucht wird, und fragt sie
 * erst auf Wunsch an. Starten geht auch ohne – dann mit Hinweis auf die Einschränkung.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NightStartSheet(
    socksConnected: Boolean,
    options: TonightOptions,
    onRoutineWithNight: (Boolean) -> Unit,
    onAlarmEnabled: (Boolean) -> Unit,
    onConnectSocks: () -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // Nach Rückkehr aus Systemdialogen/-einstellungen neu prüfen.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val checks = remember(refresh) { PermissionChecks(context) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
                .navigationBarsPadding()
                .testTag("night_start_sheet"),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
        ) {
            Text(
                stringResource(R.string.start_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.start_sheet_body), color = MaterialTheme.colorScheme.onSurfaceVariant)

            CheckRow(
                done = socksConnected,
                title = stringResource(R.string.start_check_socks),
                body = if (socksConnected) null else stringResource(R.string.start_check_socks_missing),
                actionLabel = stringResource(R.string.action_connect_socks),
                onAction = onConnectSocks,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                CheckRow(
                    done = checks.notifications,
                    title = stringResource(R.string.start_check_notifications),
                    body = stringResource(R.string.start_check_notifications_body),
                    actionLabel = stringResource(R.string.start_check_allow),
                    onAction = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                CheckRow(
                    done = checks.nearbyDevices,
                    title = stringResource(R.string.start_check_nearby),
                    body = stringResource(R.string.start_check_nearby_body),
                    actionLabel = stringResource(R.string.start_check_allow),
                    onAction = { permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) },
                )
            }
            CheckRow(
                done = checks.batteryUnrestricted,
                title = stringResource(R.string.start_check_battery),
                body = stringResource(R.string.start_check_battery_body),
                actionLabel = stringResource(R.string.start_check_open_settings),
                onAction = { context.openBatterySettings() },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !checks.nearbyDevices) {
                Text(
                    stringResource(R.string.recording_background_limited),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            TonightSection(options, onRoutineWithNight, onAlarmEnabled)
            BigActionButton(
                text = stringResource(R.string.start_sheet_start),
                icon = Icons.Filled.Bedtime,
                onClick = onStart,
                enabled = socksConnected,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Dimens.SpaceL)
                    .testTag("start_recording"),
            )
        }
    }
}

/** Was zur Nacht dazugehört: Abendroutine und smarter Wecker (aus den Einstellungen). */
data class TonightOptions(
    val routineWithNight: Boolean = false,
    val routineMinutes: Int = 0,
    val alarmEnabled: Boolean = false,
    val wakeTime: LocalTime = LocalTime.of(6, 45),
    val alarmWindowMinutes: Int = 30,
    val use24h: Boolean = true,
)

@Composable
private fun TonightSection(options: TonightOptions, onRoutineWithNight: (Boolean) -> Unit, onAlarmEnabled: (Boolean) -> Unit) {
    val locale = currentLocale()
    Text(
        stringResource(R.string.start_sheet_tonight),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
    SwitchRow(
        title = stringResource(R.string.start_routine_title),
        body = stringResource(R.string.start_routine_body, formatDuration(Duration.ofMinutes(options.routineMinutes.toLong()))),
        checked = options.routineWithNight,
        onCheckedChange = onRoutineWithNight,
        testTag = "start_routine",
    )
    SwitchRow(
        title = stringResource(R.string.start_alarm_title),
        body = stringResource(
            R.string.start_alarm_body,
            formatLocalTime(options.wakeTime.minusMinutes(options.alarmWindowMinutes.toLong()), options.use24h, locale),
            formatLocalTime(options.wakeTime, options.use24h, locale),
        ),
        checked = options.alarmEnabled,
        onCheckedChange = onAlarmEnabled,
        testTag = "start_alarm",
    )
}

@Composable
private fun CheckRow(done: Boolean, title: String, body: String?, actionLabel: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = Dimens.ThumbTarget)) {
        Icon(
            if (done) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = Dimens.SpaceM)
                .semantics(mergeDescendants = true) {},
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (done) {
                Text(stringResource(R.string.start_check_done), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            } else if (body != null) {
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!done) {
            FilledTonalButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(actionLabel) }
        }
    }
}

/** Momentaufnahme der relevanten Berechtigungen und Einstellungen. */
private class PermissionChecks(context: Context) {
    val notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.isGranted(Manifest.permission.POST_NOTIFICATIONS)
    val nearbyDevices = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.isGranted(Manifest.permission.BLUETOOTH_CONNECT)
    val batteryUnrestricted = context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true

    private fun Context.isGranted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

/** Öffnet die Liste der Akku-Optimierung, ersatzweise die App-Einstellungen. */
private fun Context.openBatterySettings() {
    val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val app = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(list) }.onFailure { runCatching { startActivity(app) } }
}
