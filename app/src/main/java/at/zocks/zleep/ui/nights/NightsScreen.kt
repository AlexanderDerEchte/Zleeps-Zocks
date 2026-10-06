package at.zocks.zleep.ui.nights

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import at.zocks.zleep.R
import at.zocks.zleep.ui.components.EmptyState
import at.zocks.zleep.ui.components.ScreenColumn
import at.zocks.zleep.ui.theme.ZocksTheme

@Composable
fun NightsScreen(modifier: Modifier = Modifier) {
    ScreenColumn(title = stringResource(R.string.nav_nights), modifier = modifier.testTag("screen_nights")) {
        EmptyState(
            icon = Icons.Outlined.CalendarMonth,
            title = stringResource(R.string.nights_empty_title),
            body = stringResource(R.string.nights_empty_body),
        )
    }
}

@Preview
@Composable
private fun NightsScreenPreview() {
    ZocksTheme { NightsScreen() }
}
