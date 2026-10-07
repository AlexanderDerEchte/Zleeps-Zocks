package at.zocks.zleep.ui.nights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.zocks.zleep.R
import at.zocks.zleep.domain.model.NightSource
import at.zocks.zleep.ui.components.BigSegmentedChoice
import at.zocks.zleep.ui.components.EmptyState
import at.zocks.zleep.ui.components.ErrorState
import at.zocks.zleep.ui.components.InfoBadge
import at.zocks.zleep.ui.components.LoadingState
import at.zocks.zleep.ui.format.currentLocale
import at.zocks.zleep.ui.format.formatDuration
import at.zocks.zleep.ui.format.formatNightDate
import at.zocks.zleep.ui.format.formatTime
import at.zocks.zleep.ui.format.tagLabel
import at.zocks.zleep.ui.theme.Dimens
import at.zocks.zleep.ui.theme.ZocksThemeExt
import java.time.ZoneId

@Composable
fun NightsRoute(onOpenNight: (Long) -> Unit, viewModel: NightsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NightsScreen(state, viewModel::onEvent, onOpenNight)
}

@Composable
fun NightsScreen(
    state: NightsUiState,
    onEvent: (NightsEvent) -> Unit,
    onOpenNight: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_nights"),
        contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceL),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
    ) {
        item {
            Text(
                stringResource(R.string.nav_nights),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .padding(bottom = Dimens.SpaceS)
                    .semantics { heading() },
            )
        }
        when (state) {
            NightsUiState.Loading -> item { LoadingState() }
            NightsUiState.Error -> item { ErrorState(stringResource(R.string.error_generic), onRetry = {}) }
            NightsUiState.Empty -> item {
                EmptyState(
                    icon = Icons.Outlined.CalendarMonth,
                    title = stringResource(R.string.nights_empty_title),
                    body = stringResource(R.string.nights_empty_body),
                )
            }
            is NightsUiState.Content -> {
                item {
                    BigSegmentedChoice(
                        options = NightsView.entries,
                        selected = state.view,
                        label = { viewLabel(it) },
                        onSelect = { onEvent(NightsEvent.SelectView(it)) },
                        testTagPrefix = "nights_view",
                    )
                }
                when (state.view) {
                    NightsView.LIST -> items(state.items, key = { it.night.id }) { item ->
                        NightRow(item, state.use24HourClock, onClick = { onOpenNight(item.night.id) })
                    }
                    NightsView.CALENDAR -> item { NightsCalendar(state, onEvent, onOpenNight) }
                    NightsView.TRENDS -> trendItems(state, onEvent)
                }
            }
        }
    }
}

@Composable
private fun viewLabel(view: NightsView): String = stringResource(
    when (view) {
        NightsView.LIST -> R.string.nights_view_list
        NightsView.CALENDAR -> R.string.nights_view_calendar
        NightsView.TRENDS -> R.string.nights_view_trends
    },
)

@Composable
private fun NightRow(item: NightListItem, use24h: Boolean, onClick: () -> Unit) {
    val night = item.night
    val locale = currentLocale()
    val zone = ZoneId.systemDefault()
    val date = formatNightDate(item.nightDate, locale)
    val openLabel = stringResource(R.string.night_open_detail, date)
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.ThumbTarget)
            .clickable(onClickLabel = openLabel, onClick = onClick)
            .testTag("night_item"),
    ) {
        Row(Modifier.padding(Dimens.CardPadding), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                    Text(date, style = MaterialTheme.typography.titleMedium)
                    if (night.source == NightSource.DEMO) InfoBadge(stringResource(R.string.badge_demo))
                    if (night.isRecording) InfoBadge(stringResource(R.string.night_recording))
                }
                val duration = item.totalSleep ?: night.sleepWindow
                val from = night.sleepOnset ?: night.start
                val to = night.finalWake ?: night.end
                val times = to?.let { stringResource(R.string.time_range, formatTime(from, use24h, locale, zone), formatTime(it, use24h, locale, zone)) }
                Text(
                    listOfNotNull(duration?.let { formatDuration(it) }, times).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (night.tags.isNotEmpty()) {
                    Text(
                        night.tags.map { tagLabel(it) }.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (item.score != null) {
                val scoreLabel = stringResource(R.string.nights_score, item.score)
                Text(
                    item.score.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = ZocksThemeExt.colors.sleep,
                    modifier = Modifier
                        .padding(horizontal = Dimens.SpaceS)
                        .semantics { contentDescription = scoreLabel },
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
