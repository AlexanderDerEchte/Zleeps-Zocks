package at.zocks.zleep.ui.nights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import at.zocks.zleep.R
import at.zocks.zleep.ui.components.ZocksCard
import at.zocks.zleep.ui.format.currentLocale
import at.zocks.zleep.ui.format.formatNightDate
import at.zocks.zleep.ui.theme.Dimens
import at.zocks.zleep.ui.theme.ZocksThemeExt
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields

/** Stufen der Flächenfarbe nach Score (eine Farbe, heller = niedriger). Die Zahl steht immer dabei. */
private val ScoreSteps = listOf(0 to 0.12f, 50 to 0.22f, 70 to 0.33f, 85 to 0.45f)

private fun scoreAlpha(score: Int): Float = ScoreSteps.last { score >= it.first }.second

/** Monatskalender: jede Nacht mit ihrem Score, antippen öffnet die Nacht. */
@Composable
fun NightsCalendar(state: NightsUiState.Content, onEvent: (NightsEvent) -> Unit, onOpenNight: (Long) -> Unit) {
    val locale = currentLocale()
    val month = state.month
    // Ohne Land im Gebietsschema gilt die ISO-Woche (Montag zuerst).
    val firstDayOfWeek = (if (locale.country.isEmpty()) WeekFields.ISO else WeekFields.of(locale)).firstDayOfWeek
    val monthTitle = DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(month)

    ZocksCard(modifier = Modifier.testTag("nights_calendar")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onEvent(NightsEvent.PreviousMonth) }, modifier = Modifier.testTag("calendar_previous")) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.calendar_previous))
            }
            Text(
                monthTitle.replaceFirstChar { it.titlecase(locale) },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
                    .testTag("calendar_month"),
            )
            IconButton(
                onClick = { onEvent(NightsEvent.NextMonth) },
                enabled = month < state.currentMonth,
                modifier = Modifier.testTag("calendar_next"),
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.calendar_next))
            }
        }

        // Wochentage, beginnend mit dem ersten Tag der Woche im Gebietsschema
        Row(Modifier.padding(top = Dimens.SpaceS).clearAndSetSemantics {}) {
            (0L until 7L).map { firstDayOfWeek.plus(it) }.forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        val first = month.atDay(1)
        val leading = ((first.dayOfWeek.value - firstDayOfWeek.value) + 7) % 7
        val cells: List<LocalDate?> = List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        Column(Modifier.padding(top = Dimens.SpaceXs), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
            cells.chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
                    week.forEach { date ->
                        if (date == null) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            DayCell(date, state.calendar[date], onOpenNight, Modifier.weight(1f))
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        if (state.calendar.isEmpty()) {
            Text(
                stringResource(R.string.calendar_no_nights),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.SpaceM),
            )
        } else {
            ScoreScaleLegend(Modifier.padding(top = Dimens.SpaceM))
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, item: NightListItem?, onOpenNight: (Long) -> Unit, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val dateLabel = formatNightDate(date, locale)
    val shape = RoundedCornerShape(8.dp)
    val sleep = ZocksThemeExt.colors.sleep
    val description = when {
        item == null -> stringResource(R.string.calendar_day_empty, dateLabel)
        item.score != null -> stringResource(R.string.calendar_day_with_score, dateLabel, item.score)
        else -> stringResource(R.string.calendar_day_without_score, dateLabel)
    }
    val openLabel = stringResource(R.string.night_open_detail, dateLabel)
    val base = modifier
        .height(56.dp)
        .clip(shape)
    val styled = when {
        item == null -> base
        item.score != null -> base.background(sleep.copy(alpha = scoreAlpha(item.score)))
        else -> base.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    }
    Column(
        modifier = styled
            .testTag("calendar_day_$date")
            .then(if (item != null) Modifier.clickable(onClickLabel = openLabel) { onOpenNight(item.night.id) } else Modifier)
            .clearAndSetSemantics {
                contentDescription = description
                if (item != null) role = Role.Button
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (item == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        if (item?.score != null) {
            Text(item.score.toString(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ScoreScaleLegend(modifier: Modifier = Modifier) {
    val sleep = ZocksThemeExt.colors.sleep
    Row(modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        ScoreSteps.forEach { (_, alpha) -> Swatch(sleep.copy(alpha = alpha)) }
        Text(
            stringResource(R.string.calendar_legend),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Dimens.SpaceS),
        )
    }
}

@Composable
private fun Swatch(color: Color) {
    Box(
        Modifier
            .padding(end = 2.dp)
            .size(14.dp)
            .background(color, RoundedCornerShape(3.dp)),
    )
}
