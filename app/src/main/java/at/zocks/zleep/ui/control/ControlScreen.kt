package at.zocks.zleep.ui.control

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import at.zocks.zleep.R
import at.zocks.zleep.ui.components.EmptyState
import at.zocks.zleep.ui.components.ScreenColumn
import at.zocks.zleep.ui.components.ZocksCard
import at.zocks.zleep.ui.navigation.ControlSection
import at.zocks.zleep.ui.theme.Dimens
import at.zocks.zleep.ui.theme.ZocksTheme
import at.zocks.zleep.ui.theme.ZocksThemeExt

@Composable
fun ControlScreen(
    initialSection: ControlSection,
    onPairSocks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var section by rememberSaveable(initialSection) { mutableStateOf(initialSection) }

    ScreenColumn(title = stringResource(R.string.nav_control), modifier = modifier.testTag("screen_control")) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ControlSection.entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = section == entry,
                    onClick = { section = entry },
                    shape = SegmentedButtonDefaults.itemShape(index, ControlSection.entries.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = entry.accentContainer(),
                        activeContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier
                        .heightIn(min = Dimens.ThumbTarget)
                        .testTag("control_tab_${entry.name.lowercase()}"),
                ) {
                    Text(stringResource(entry.label), style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        ZocksCard {
            when (section) {
                ControlSection.HEAT -> EmptyState(
                    icon = Icons.Outlined.Whatshot,
                    iconTint = ZocksThemeExt.colors.heat,
                    title = stringResource(R.string.control_not_connected_title),
                    body = stringResource(R.string.control_not_connected_heat),
                    actionLabel = stringResource(R.string.action_pair_socks),
                    onAction = onPairSocks,
                    modifier = Modifier.testTag("control_heat_content"),
                )
                ControlSection.MASSAGE -> EmptyState(
                    icon = Icons.Outlined.Spa,
                    iconTint = ZocksThemeExt.colors.massage,
                    title = stringResource(R.string.control_not_connected_title),
                    body = stringResource(R.string.control_not_connected_massage),
                    actionLabel = stringResource(R.string.action_pair_socks),
                    onAction = onPairSocks,
                    modifier = Modifier.testTag("control_massage_content"),
                )
            }
        }
    }
}

private val ControlSection.label: Int
    get() = when (this) {
        ControlSection.HEAT -> R.string.control_heat
        ControlSection.MASSAGE -> R.string.control_massage
    }

@Composable
private fun ControlSection.accentContainer() = when (this) {
    ControlSection.HEAT -> ZocksThemeExt.colors.heatContainer
    ControlSection.MASSAGE -> ZocksThemeExt.colors.massageContainer
}

@Preview
@Composable
private fun ControlScreenPreview() {
    ZocksTheme { ControlScreen(ControlSection.HEAT, {}) }
}
