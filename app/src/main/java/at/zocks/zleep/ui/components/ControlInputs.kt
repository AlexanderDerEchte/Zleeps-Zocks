package at.zocks.zleep.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.ui.format.sideLabelRes
import at.zocks.zleep.ui.theme.Dimens
import at.zocks.zleep.ui.theme.MetricTextStyle

/** Große Auswahl mit gleich breiten Segmenten, im Halbdunkel mit dem Daumen gut treffbar. */
@Composable
fun <T> BigSegmentedChoice(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    testTagPrefix: String? = null,
    enabled: Boolean = true,
) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = activeColor,
                    activeContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                icon = {},
                modifier = Modifier
                    .heightIn(min = Dimens.ThumbTarget)
                    .then(if (testTagPrefix != null) Modifier.testTag("${testTagPrefix}_$index") else Modifier),
            ) {
                Text(label(option), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

/** Beide / Links / Rechts. */
@Composable
fun SideChoice(
    selected: SockSide,
    onSelect: (SockSide) -> Unit,
    activeColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    BigSegmentedChoice(
        options = listOf(SockSide.BOTH, SockSide.LEFT, SockSide.RIGHT),
        selected = selected,
        label = { stringResource(sideLabelRes(it)) },
        onSelect = onSelect,
        activeColor = activeColor,
        modifier = modifier,
        testTagPrefix = "side",
        enabled = enabled,
    )
}

/** Großer Wert mit − und + daneben, z. B. Zieltemperatur. */
@Composable
fun BigStepper(
    value: String,
    decreaseLabel: String,
    increaseLabel: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true,
    color: Color = MaterialTheme.colorScheme.onSurface,
    testTag: String? = null,
    valueStyle: TextStyle = MetricTextStyle,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FilledTonalIconButton(
            onClick = onDecrease,
            enabled = canDecrease,
            modifier = Modifier
                .size(Dimens.ThumbTarget)
                .then(if (testTag != null) Modifier.testTag("${testTag}_decrease") else Modifier),
        ) {
            Icon(Icons.Filled.Remove, contentDescription = decreaseLabel)
        }
        Text(
            text = value,
            style = valueStyle,
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .then(if (testTag != null) Modifier.testTag("${testTag}_value") else Modifier),
        )
        FilledTonalIconButton(
            onClick = onIncrease,
            enabled = canIncrease,
            modifier = Modifier
                .size(Dimens.ThumbTarget)
                .then(if (testTag != null) Modifier.testTag("${testTag}_increase") else Modifier),
        ) {
            Icon(Icons.Filled.Add, contentDescription = increaseLabel)
        }
    }
}
