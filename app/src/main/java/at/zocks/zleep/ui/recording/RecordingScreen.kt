package at.zocks.zleep.ui.recording

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.zocks.zleep.R
import at.zocks.zleep.domain.recording.RecordingState
import at.zocks.zleep.ui.components.InfoBadge
import at.zocks.zleep.ui.components.LoadingState
import at.zocks.zleep.ui.format.currentLocale
import at.zocks.zleep.ui.format.formatTime
import at.zocks.zleep.ui.theme.Dimens
import at.zocks.zleep.ui.theme.MetricTextStyle
import at.zocks.zleep.ui.theme.ZocksThemeExt

/**
 * Nachtmodus: fast schwarz, große Uhr, wenig Text. Nach dem Beenden geht es direkt zur Nacht;
 * wurde die Aufzeichnung verworfen, zurück.
 */
@Composable
fun RecordingRoute(
    onBack: () -> Unit,
    onFinished: (Long) -> Unit,
    viewModel: RecordingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var seenActive by rememberSaveable { mutableStateOf(false) }
    val recording = state.recording
    LaunchedEffect(recording) {
        when {
            recording is RecordingState.Active -> seenActive = true
            seenActive && recording is RecordingState.Idle -> recording.lastNightId?.let(onFinished) ?: onBack()
        }
    }
    RecordingScreen(state, viewModel::onEvent, onBack)
}

@Composable
fun RecordingScreen(state: RecordingUiState, onEvent: (RecordingEvent) -> Unit, onBack: () -> Unit) {
    val locale = currentLocale()
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("screen_recording"),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(Dimens.SpaceS).testTag("action_back")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
        val active = state.recording as? RecordingState.Active
        if (active == null) {
            LoadingState(Modifier.align(Alignment.Center))
            return@Box
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceXxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                formatTime(active.now, state.use24HourClock, locale),
                style = MetricTextStyle.copy(fontSize = 88.sp, fontWeight = FontWeight.ExtraLight),
                // Gedämpft, damit der Bildschirm nachts nicht blendet.
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("recording_clock"),
            )
            if (active.simulated) InfoBadge(stringResource(R.string.recording_simulated), color = ZocksThemeExt.colors.sleep)
            RecordingStatusLines(active, state.use24HourClock, state.temperatureUnit)
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = { onEvent(RecordingEvent.RequestStop) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.ThumbTarget)
                    .testTag("night_mode_stop"),
            ) {
                Text(stringResource(R.string.recording_stop), style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    if (state.confirmStop) {
        AlertDialog(
            onDismissRequest = { onEvent(RecordingEvent.DismissStop) },
            title = { Text(stringResource(R.string.recording_stop_confirm_title)) },
            text = { Text(stringResource(R.string.recording_stop_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { onEvent(RecordingEvent.ConfirmStop) }, modifier = Modifier.testTag("confirm_stop")) {
                    Text(stringResource(R.string.recording_stop))
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(RecordingEvent.DismissStop) }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
