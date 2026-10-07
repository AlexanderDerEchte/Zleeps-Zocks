package at.zocks.zleep.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import at.zocks.zleep.R
import at.zocks.zleep.ui.format.currentLocale
import at.zocks.zleep.ui.format.formatLocalTime
import at.zocks.zleep.ui.theme.Dimens
import java.time.LocalTime

/** Uhrzeit-Einstellung; antippen öffnet die Uhr. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSettingRow(
    label: String,
    time: LocalTime,
    use24h: Boolean,
    onChange: (LocalTime) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.ThumbTarget)
            .clickable(onClickLabel = label) { editing = true }
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatLocalTime(time, use24h, currentLocale()), style = MaterialTheme.typography.titleLarge)
        }
        Icon(Icons.Outlined.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
    if (editing) {
        val picker = rememberTimePickerState(time.hour, time.minute, is24Hour = use24h)
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(label) },
            text = { TimePicker(picker) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onChange(LocalTime.of(picker.hour, picker.minute))
                        editing = false
                    },
                    modifier = Modifier.testTag("${testTag}_confirm"),
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
