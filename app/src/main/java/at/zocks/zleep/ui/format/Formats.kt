package at.zocks.zleep.ui.format

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import at.zocks.zleep.R
import at.zocks.zleep.domain.analysis.ScoreComponent
import at.zocks.zleep.domain.model.NightEventType
import at.zocks.zleep.domain.model.SleepStage
import at.zocks.zleep.domain.model.Tag
import at.zocks.zleep.domain.model.TemperatureUnit
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** Uhrzeit, z. B. „22:41“ oder „10:41 PM“. */
fun formatTime(instant: Instant, use24h: Boolean, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a", locale).format(instant.atZone(zone))

/** Langes Datum einer Nacht, z. B. „Montag, 5. Oktober“. */
fun formatNightDate(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale).format(date)

/** Kurzes Datum, z. B. „Mo., 5. Okt.“. */
fun formatShortDate(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale).format(date)

fun formatDecimal(value: Double, digits: Int, locale: Locale): String = String.format(locale, "%.${digits}f", value)

@Composable
fun formatDuration(duration: Duration): String {
    val minutes = duration.toMinutes().coerceAtLeast(0)
    return if (minutes < 60) {
        stringResource(R.string.duration_m, minutes.toInt())
    } else {
        stringResource(R.string.duration_hm, (minutes / 60).toInt(), (minutes % 60).toInt())
    }
}

@Composable
fun formatTemperature(celsius: Double?, unit: TemperatureUnit): String {
    if (celsius == null) return stringResource(R.string.value_not_available)
    val locale = currentLocale()
    return when (unit) {
        TemperatureUnit.CELSIUS -> stringResource(R.string.value_temperature_c, formatDecimal(celsius, 1, locale))
        TemperatureUnit.FAHRENHEIT ->
            stringResource(R.string.value_temperature_f, formatDecimal(celsius * 9 / 5 + 32, 1, locale))
    }
}

@Composable
fun formatBpm(value: Double?): String =
    value?.let { stringResource(R.string.value_bpm, Math.round(it).toInt()) } ?: stringResource(R.string.value_not_available)

@Composable
fun formatMs(value: Double?): String =
    value?.let { stringResource(R.string.value_ms, Math.round(it).toInt()) } ?: stringResource(R.string.value_not_available)

@Composable
fun formatPercent(value: Double?): String =
    value?.let { stringResource(R.string.value_percent, Math.round(it).toInt()) } ?: stringResource(R.string.value_not_available)

@Composable
fun stageLabel(stage: SleepStage): String = stringResource(
    when (stage) {
        SleepStage.AWAKE -> R.string.stage_awake
        SleepStage.LIGHT -> R.string.stage_light
        SleepStage.DEEP -> R.string.stage_deep
        SleepStage.REM -> R.string.stage_rem
    },
)

@Composable
fun eventLabel(type: NightEventType): String = stringResource(
    when (type) {
        NightEventType.HEAT -> R.string.event_heat
        NightEventType.MASSAGE -> R.string.event_massage
        NightEventType.ROUTINE -> R.string.event_routine
        NightEventType.ALARM -> R.string.event_alarm
        NightEventType.SAFETY_SHUTOFF -> R.string.event_safety_shutoff
    },
)

/** Eingebaute Tags werden übersetzt, eigene zeigen ihren Text. */
@Composable
fun tagLabel(tag: Tag): String = when (tag.key) {
    Tag.CAFFEINE -> stringResource(R.string.tag_caffeine)
    Tag.SPORT -> stringResource(R.string.tag_sport)
    Tag.ALCOHOL -> stringResource(R.string.tag_alcohol)
    Tag.STRESS -> stringResource(R.string.tag_stress)
    Tag.LATE_MEAL -> stringResource(R.string.tag_late_meal)
    Tag.SCREEN_TIME -> stringResource(R.string.tag_screen_time)
    null -> tag.label.orEmpty()
    else -> tag.label ?: tag.key
}

@Composable
fun scoreComponentLabel(component: ScoreComponent): String = stringResource(
    when (component) {
        ScoreComponent.DURATION -> R.string.score_duration
        ScoreComponent.EFFICIENCY -> R.string.score_efficiency
        ScoreComponent.RESTORATIVE -> R.string.score_restorative
        ScoreComponent.LATENCY -> R.string.score_latency
        ScoreComponent.CONTINUITY -> R.string.score_continuity
    },
)

/** Uhrzeit ohne Datum, z. B. „23:40“ oder „11:40 PM“. */
fun formatLocalTime(time: LocalTime, use24h: Boolean, locale: Locale): String =
    DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a", locale).format(time)
