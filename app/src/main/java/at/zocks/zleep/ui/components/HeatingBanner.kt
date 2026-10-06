package at.zocks.zleep.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import at.zocks.zleep.R
import at.zocks.zleep.ui.format.formatDuration
import at.zocks.zleep.ui.theme.Dimens
import at.zocks.zleep.ui.theme.ZocksThemeExt
import java.time.Duration

/** Deutliche, App-weite Anzeige, solange eine Socke heizt – mit Stopp direkt daneben. */
@Composable
fun HeatingBanner(remaining: Duration?, onStop: () -> Unit) {
    AnimatedVisibility(visible = remaining != null, enter = expandVertically(), exit = shrinkVertically()) {
        Surface(color = ZocksThemeExt.colors.heat, contentColor = ZocksThemeExt.colors.onHeat) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .heightIn(min = 56.dp)
                    .padding(start = Dimens.ScreenPadding, end = Dimens.SpaceS)
                    .testTag("heating_banner"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Whatshot, contentDescription = null)
                Text(
                    text = stringResource(R.string.heat_banner, formatDuration(remaining ?: Duration.ZERO)),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Dimens.SpaceM)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
                TextButton(
                    onClick = onStop,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("heating_banner_stop"),
                ) {
                    Text(
                        stringResource(R.string.heat_banner_stop),
                        style = MaterialTheme.typography.labelLarge,
                        color = ZocksThemeExt.colors.onHeat,
                    )
                }
            }
        }
    }
}
