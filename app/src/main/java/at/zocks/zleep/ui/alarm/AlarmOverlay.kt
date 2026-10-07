package at.zocks.zleep.ui.alarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import at.zocks.zleep.R
import at.zocks.zleep.domain.alarm.AlarmState
import at.zocks.zleep.domain.alarm.WakeReason
import at.zocks.zleep.ui.components.BigActionButton
import at.zocks.zleep.ui.components.ZocksLogo
import at.zocks.zleep.ui.format.currentLocale
import at.zocks.zleep.ui.format.formatTime
import at.zocks.zleep.ui.theme.Dimens
import at.zocks.zleep.ui.theme.MetricTextStyle
import kotlinx.coroutines.delay
import java.time.Instant

/** Vollbild, solange der Wecker klingelt: große Uhrzeit, „Aus“ und „Schlummern“. */
@Composable
fun AlarmOverlay(ringing: AlarmState.Ringing, use24h: Boolean, onDismiss: () -> Unit, onSnooze: () -> Unit) {
    val locale = currentLocale()
    val now by produceState(Instant.now()) {
        while (true) {
            delay(TICK_MS)
            value = Instant.now()
        }
    }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(Dimens.ScreenPadding)
            .testTag("alarm_overlay"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL, Alignment.CenterVertically),
    ) {
        ZocksLogo()
        Text(
            stringResource(R.string.alarm_notification_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(formatTime(now, use24h, locale), style = MetricTextStyle.copy(fontSize = 72.sp))
        Text(
            stringResource(if (ringing.reason == WakeReason.LIGHT_SLEEP) R.string.alarm_reason_light else R.string.alarm_reason_time),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
        BigActionButton(
            text = stringResource(R.string.alarm_dismiss),
            icon = Icons.Filled.AlarmOff,
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.SpaceXl)
                .testTag("alarm_dismiss"),
        )
        BigActionButton(
            text = stringResource(R.string.alarm_snooze),
            icon = Icons.Filled.Snooze,
            onClick = onSnooze,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("alarm_snooze"),
        )
    }
    }
}

private const val TICK_MS = 10_000L
