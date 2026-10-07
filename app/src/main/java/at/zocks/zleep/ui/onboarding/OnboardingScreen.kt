package at.zocks.zleep.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.zocks.zleep.R
import at.zocks.zleep.domain.model.DeviceMode
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.ui.components.BigActionButton
import at.zocks.zleep.ui.components.BigStepper
import at.zocks.zleep.ui.components.Labeled
import at.zocks.zleep.ui.components.LocalSnackbarHostState
import at.zocks.zleep.ui.components.SockStatusView
import at.zocks.zleep.ui.components.SwitchRow
import at.zocks.zleep.ui.components.TimeSettingRow
import at.zocks.zleep.ui.components.UserMessageEffect
import at.zocks.zleep.ui.components.ZocksCard
import at.zocks.zleep.ui.pairing.PairingPanel
import at.zocks.zleep.ui.components.ZocksLogo
import at.zocks.zleep.ui.format.currentLocale
import at.zocks.zleep.ui.format.formatDuration
import at.zocks.zleep.ui.format.formatLocalTime
import at.zocks.zleep.ui.theme.Dimens
import at.zocks.zleep.ui.theme.ZocksThemeExt
import java.time.Duration

@Composable
fun OnboardingRoute(viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CompositionLocalProvider(LocalSnackbarHostState provides snackbar) {
        UserMessageEffect(state.userMessage) { viewModel.onEvent(OnboardingEvent.MessageShown) }
        OnboardingScreen(state, viewModel::onEvent, pairingContent = { PairingPanel() })
        SnackbarHost(snackbar)
    }
}

/** Einrichtung beim ersten Start: Erklärung, Socken koppeln, Schlafzeiten. Jeder Schritt lässt sich überspringen. */
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onEvent: (OnboardingEvent) -> Unit,
    pairingContent: @Composable () -> Unit = {},
) {
    val steps = OnboardingStep.entries
    val isLast = state.step == steps.last()
    // Liegt außerhalb des Scaffolds: Surface setzt Hintergrund und Textfarbe.
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .testTag("screen_onboarding"),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.onboarding_step, state.step.ordinal + 1, steps.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onEvent(OnboardingEvent.Finish) }, modifier = Modifier.testTag("onboarding_later")) {
                Text(stringResource(R.string.onboarding_later))
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
        ) {
            when (state.step) {
                OnboardingStep.WELCOME -> WelcomeStep()
                OnboardingStep.PAIR -> PairStep(state, onEvent, pairingContent)
                OnboardingStep.TIMES -> TimesStep(state, onEvent)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(Dimens.ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
        ) {
            if (state.step != steps.first()) {
                OutlinedButton(
                    onClick = { onEvent(OnboardingEvent.Back) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = Dimens.ThumbTarget)
                        .testTag("onboarding_back"),
                ) { Text(stringResource(R.string.onboarding_back), style = MaterialTheme.typography.labelLarge) }
            }
            BigActionButton(
                text = stringResource(if (isLast) R.string.onboarding_finish else R.string.onboarding_next),
                icon = Icons.Outlined.Bedtime,
                onClick = { onEvent(if (isLast) OnboardingEvent.Finish else OnboardingEvent.Next) },
                modifier = Modifier
                    .weight(1f)
                    .testTag(if (isLast) "onboarding_finish" else "onboarding_next"),
            )
        }
    }
    }
}

@Composable
private fun StepTitle(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun WelcomeStep() {
    ZocksLogo(size = 72.dp, modifier = Modifier.padding(top = Dimens.SpaceXl))
    StepTitle(stringResource(R.string.onboarding_welcome_title), stringResource(R.string.onboarding_welcome_body))
    Feature(Icons.Outlined.Whatshot, ZocksThemeExt.colors.heat, stringResource(R.string.onboarding_feature_heat))
    Feature(Icons.Outlined.Spa, ZocksThemeExt.colors.massage, stringResource(R.string.onboarding_feature_massage))
    Feature(Icons.Outlined.Bedtime, ZocksThemeExt.colors.sleep, stringResource(R.string.onboarding_feature_sleep))
    Text(
        stringResource(R.string.wellness_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Dimens.SpaceM),
    )
}

@Composable
private fun Feature(icon: ImageVector, tint: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Dimens.SpaceM))
    }
}

@Composable
private fun PairStep(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit, pairingContent: @Composable () -> Unit) {
    // Echte Socken: suchen und links/rechts zuordnen – derselbe Ablauf wie in den Einstellungen.
    if (state.settings.deviceMode == DeviceMode.BLE) {
        StepTitle(stringResource(R.string.onboarding_pair_title), stringResource(R.string.onboarding_pair_body_ble))
        pairingContent()
        return
    }
    StepTitle(stringResource(R.string.onboarding_pair_title), stringResource(R.string.onboarding_pair_body))
    ZocksCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
            SockStatusView(state.pairStatus?.left, SockSide.LEFT, Modifier.weight(1f))
            SockStatusView(state.pairStatus?.right, SockSide.RIGHT, Modifier.weight(1f))
        }
    }
    val bothConnected = state.pairStatus?.bothConnected == true
    if (bothConnected) {
        Text(stringResource(R.string.onboarding_pair_done), color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("onboarding_paired"))
    } else {
        OutlinedButton(
            onClick = { onEvent(OnboardingEvent.ConnectSocks) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.ThumbTarget)
                .testTag("onboarding_connect"),
        ) { Text(stringResource(R.string.action_connect_socks), style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
private fun TimesStep(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit) {
    val settings = state.settings
    val locale = currentLocale()
    StepTitle(stringResource(R.string.onboarding_times_title), stringResource(R.string.onboarding_times_body))
    ZocksCard {
        TimeSettingRow(
            label = stringResource(R.string.settings_bedtime),
            time = settings.bedtime,
            use24h = settings.use24HourClock,
            onChange = { onEvent(OnboardingEvent.SetBedtime(it)) },
            testTag = "onboarding_bedtime",
        )
        TimeSettingRow(
            label = stringResource(R.string.settings_wake_time),
            time = settings.wakeTime,
            use24h = settings.use24HourClock,
            onChange = { onEvent(OnboardingEvent.SetWakeTime(it)) },
            testTag = "onboarding_wake_time",
        )
        Labeled(stringResource(R.string.settings_sleep_goal)) {
            BigStepper(
                value = formatDuration(Duration.ofMinutes(settings.sleepGoalMinutes.toLong())),
                decreaseLabel = stringResource(R.string.settings_sleep_goal_shorter),
                increaseLabel = stringResource(R.string.settings_sleep_goal_longer),
                onDecrease = { onEvent(OnboardingEvent.SetSleepGoal(settings.sleepGoalMinutes - OnboardingViewModel.GOAL_STEP)) },
                onIncrease = { onEvent(OnboardingEvent.SetSleepGoal(settings.sleepGoalMinutes + OnboardingViewModel.GOAL_STEP)) },
                canDecrease = settings.sleepGoalMinutes > OnboardingViewModel.MIN_GOAL,
                canIncrease = settings.sleepGoalMinutes < OnboardingViewModel.MAX_GOAL,
                testTag = "onboarding_goal",
                valueStyle = MaterialTheme.typography.headlineLarge,
            )
        }
    }
    ZocksCard {
        val windowStart = settings.wakeTime.minusMinutes(settings.alarm.windowMinutes.toLong())
        SwitchRow(
            title = stringResource(R.string.start_alarm_title),
            body = stringResource(
                R.string.start_alarm_body,
                formatLocalTime(windowStart, settings.use24HourClock, locale),
                formatLocalTime(settings.wakeTime, settings.use24HourClock, locale),
            ),
            checked = settings.alarm.enabled,
            onCheckedChange = { onEvent(OnboardingEvent.SetAlarm(it)) },
            testTag = "onboarding_alarm",
        )
    }
}
