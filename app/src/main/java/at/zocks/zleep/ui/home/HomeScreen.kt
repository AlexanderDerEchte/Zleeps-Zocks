package at.zocks.zleep.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.zocks.zleep.R
import at.zocks.zleep.ui.components.BigActionButton
import at.zocks.zleep.ui.components.EmptyState
import at.zocks.zleep.ui.components.ScreenColumn
import at.zocks.zleep.ui.components.SectionTitle
import at.zocks.zleep.ui.components.ZocksCard
import at.zocks.zleep.ui.components.ZocksLogo
import at.zocks.zleep.ui.theme.Dimens
import at.zocks.zleep.ui.theme.ZocksTheme
import at.zocks.zleep.ui.theme.ZocksThemeExt
import java.time.LocalTime

@Composable
fun HomeScreen(
    onOpenHeat: () -> Unit,
    onOpenMassage: () -> Unit,
    onStartNight: () -> Unit,
    onPairSocks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val greeting = remember { greetingFor(LocalTime.now()) }

    ScreenColumn(title = null, modifier = modifier.testTag("screen_home")) {
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

        ZocksCard(title = stringResource(R.string.home_last_night)) {
            EmptyState(
                icon = Icons.Outlined.NightsStay,
                title = stringResource(R.string.home_no_night_title),
                body = stringResource(R.string.home_no_night_body),
            )
        }

        ZocksCard(title = stringResource(R.string.home_socks)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
                SockStatus(R.string.sock_left, Modifier.weight(1f))
                SockStatus(R.string.sock_right, Modifier.weight(1f))
            }
            OutlinedButton(
                onClick = onPairSocks,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.SpaceL)
                    .heightIn(min = Dimens.ThumbTarget),
            ) {
                Text(stringResource(R.string.action_pair_socks), style = MaterialTheme.typography.labelLarge)
            }
        }

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
        BigActionButton(
            text = stringResource(R.string.action_start_night),
            icon = Icons.Filled.Bedtime,
            onClick = onStartNight,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("action_start_night"),
        )
    }
}

@Composable
private fun SockStatus(@StringRes side: Int, modifier: Modifier = Modifier) {
    val sideLabel = stringResource(side)
    val status = stringResource(R.string.sock_disconnected)
    val description = stringResource(R.string.sock_status_description, sideLabel, status)
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.BluetoothDisabled,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
        Column(Modifier.padding(start = Dimens.SpaceS)) {
            Text(sideLabel, style = MaterialTheme.typography.titleMedium)
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    ZocksTheme { HomeScreen({}, {}, {}, {}) }
}
