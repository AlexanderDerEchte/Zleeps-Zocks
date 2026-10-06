package at.zocks.zleep.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.zocks.zleep.R
import at.zocks.zleep.domain.model.DeviceMode
import at.zocks.zleep.domain.model.Night
import at.zocks.zleep.domain.model.NightSource
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.domain.recording.RecordingState
import at.zocks.zleep.ui.components.BigActionButton
import at.zocks.zleep.ui.components.EmptyState
import at.zocks.zleep.ui.components.InfoBadge
import at.zocks.zleep.ui.components.LoadingState
import at.zocks.zleep.ui.components.ScreenColumn
import at.zocks.zleep.ui.components.SectionTitle
import at.zocks.zleep.ui.components.SockStatusView
import at.zocks.zleep.ui.components.UserMessageEffect
import at.zocks.zleep.ui.components.ZocksCard
import at.zocks.zleep.ui.components.ZocksLogo
import at.zocks.zleep.ui.format.currentLocale
import at.zocks.zleep.ui.format.formatDuration
import at.zocks.zleep.ui.format.formatShortDate
import at.zocks.zleep.ui.format.formatTime
import at.zocks.zleep.ui.recording.NightStartSheet
import at.zocks.zleep.ui.recording.RecordingCard
import at.zocks.zleep.ui.theme.Dimens
import at.zocks.zleep.ui.theme.MetricTextStyle
import at.zocks.zleep.ui.theme.ZocksTheme
import at.zocks.zleep.ui.theme.ZocksThemeExt
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun HomeRoute(
    onOpenHeat: () -> Unit,
    onOpenMassage: () -> Unit,
    onOpenNight: (Long) -> Unit,
    onOpenNightMode: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    UserMessageEffect(state.userMessage) { viewModel.onEvent(HomeEvent.MessageShown) }
    HomeScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenHeat = onOpenHeat,
        onOpenMassage = onOpenMassage,
        onOpenNight = onOpenNight,
        onOpenNightMode = onOpenNightMode,
    )
    if (state.showStartSheet) {
        NightStartSheet(
            socksConnected = state.pairStatus?.anyConnected == true,
            onConnectSocks = { viewModel.onEvent(HomeEvent.ConnectSocks) },
            onStart = {
                viewModel.onEvent(HomeEvent.ConfirmStart)
                if (state.pairStatus?.anyConnected == true) onOpenNightMode()
            },
            onDismiss = { viewModel.onEvent(HomeEvent.DismissStartSheet) },
        )
    }
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    onOpenHeat: () -> Unit,
    onOpenMassage: () -> Unit,
    onOpenNight: (Long) -> Unit,
    onOpenNightMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val greeting = remember { greetingFor(LocalTime.now()) }
    val scrollState = rememberScrollState()
    val isRecording = state.recording is RecordingState.Active
    // Die laufende Nacht steht ganz oben – beim Start bzw. bei der Rückkehr dorthin scrollen.
    LaunchedEffect(isRecording) {
        if (isRecording) scrollState.animateScrollTo(0)
    }

    ScreenColumn(title = null, modifier = modifier.testTag("screen_home"), scrollState = scrollState) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ZocksLogo(size = 44.dp)
            Column(Modifier.padding(start = Dimens.SpaceM)) {
                Text(stringResource(greeting), style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.app_slogan),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val recording = state.recording as? RecordingState.Active
        if (recording != null) {
            RecordingCard(
                state = recording,
                use24h = state.use24HourClock,
                unit = state.temperatureUnit,
                onOpenNightMode = onOpenNightMode,
                onStop = { onEvent(HomeEvent.StopNight) },
            )
        }

        when {
            state.loading -> ZocksCard { LoadingState() }
            state.lastNight != null -> LastNightCard(state.lastNight, state.use24HourClock, onOpenNight)
            else -> ZocksCard(title = stringResource(R.string.home_last_night)) {
                EmptyState(
                    icon = Icons.Outlined.NightsStay,
                    title = stringResource(R.string.home_no_night_title),
                    body = stringResource(R.string.home_no_night_body),
                )
            }
        }

        SocksCard(state, onEvent)

        SectionTitle(stringResource(R.string.home_quick_actions))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
            BigActionButton(
                text = stringResource(R.string.action_heat),
                icon = Icons.Filled.Whatshot,
                onClick = onOpenHeat,
                containerColor = ZocksThemeExt.colors.heat,
                contentColor = ZocksThemeExt.colors.onHeat,
                modifier = Modifier
                    .weight(1f)
                    .testTag("action_heat"),
            )
            BigActionButton(
                text = stringResource(R.string.action_massage),
                icon = Icons.Filled.Spa,
                onClick = onOpenMassage,
                containerColor = ZocksThemeExt.colors.massage,
                contentColor = ZocksThemeExt.colors.onMassage,
                modifier = Modifier
                    .weight(1f)
                    .testTag("action_massage"),
            )
        }
        if (recording == null) {
            BigActionButton(
                text = stringResource(R.string.action_start_night),
                icon = Icons.Filled.Bedtime,
                onClick = { onEvent(HomeEvent.StartNight) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("action_start_night"),
            )
        }
    }
}

@Composable
private fun LastNightCard(night: Night, use24h: Boolean, onOpenNight: (Long) -> Unit) {
    val locale = currentLocale()
    val zone = ZoneId.systemDefault()
    val date = formatShortDate(night.nightOf(zone), locale)
    ZocksCard(
        title = stringResource(R.string.home_last_night_of, date),
        onClick = { onOpenNight(night.id) },
        modifier = Modifier.testTag("card_last_night"),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.night_sleep_duration),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val window = night.sleepWindow
                Text(
                    text = if (window != null) formatDuration(window) else stringResource(R.string.value_not_available),
                    style = MetricTextStyle,
                    color = ZocksThemeExt.colors.sleep,
                )
                val from = night.sleepOnset ?: night.start
                val to = night.finalWake ?: night.end
                if (to != null) {
                    Text(
                        stringResource(R.string.time_range, formatTime(from, use24h, locale, zone), formatTime(to, use24h, locale, zone)),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (night.source == NightSource.DEMO) InfoBadge(stringResource(R.string.badge_demo))
        }
    }
}

@Composable
private fun SocksCard(state: HomeUiState, onEvent: (HomeEvent) -> Unit) {
    val status = state.pairStatus
    ZocksCard(title = stringResource(R.string.home_socks)) {
        if (state.deviceMode == DeviceMode.SIMULATOR) {
            InfoBadge(stringResource(R.string.badge_simulator), Modifier.padding(bottom = Dimens.SpaceM))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
            SockStatusView(status?.left, SockSide.LEFT, Modifier.weight(1f))
            SockStatusView(status?.right, SockSide.RIGHT, Modifier.weight(1f))
        }
        val connected = status?.anyConnected == true
        OutlinedButton(
            onClick = { onEvent(if (connected) HomeEvent.DisconnectSocks else HomeEvent.ConnectSocks) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.SpaceL)
                .heightIn(min = Dimens.ThumbTarget)
                .testTag(if (connected) "action_disconnect_socks" else "action_connect_socks"),
        ) {
            Text(
                stringResource(if (connected) R.string.action_disconnect_socks else R.string.action_connect_socks),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@StringRes
internal fun greetingFor(time: LocalTime): Int = when (time.hour) {
    in 5..10 -> R.string.greeting_morning
    in 11..17 -> R.string.greeting_day
    in 18..21 -> R.string.greeting_evening
    else -> R.string.greeting_night
}

@Preview
@Composable
private fun HomeScreenPreview() {
    val start = Instant.parse("2026-10-05T20:45:00Z")
    ZocksTheme {
        HomeScreen(
            state = HomeUiState(
                loading = false,
                lastNight = Night(
                    id = 1,
                    start = start,
                    end = start.plusSeconds(8 * 3600),
                    sleepOnset = start.plusSeconds(900),
                    finalWake = start.plusSeconds(7 * 3600 + 1800),
                    source = NightSource.DEMO,
                    note = null,
                    tags = emptyList(),
                ),
            ),
            onEvent = {},
            onOpenHeat = {},
            onOpenMassage = {},
            onOpenNight = {},
            onOpenNightMode = {},
        )
    }
}
