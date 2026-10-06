package at.zocks.zleep.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.zocks.zleep.BuildConfig
import at.zocks.zleep.R
import at.zocks.zleep.ui.components.ScreenColumn
import at.zocks.zleep.ui.components.ZocksCard
import at.zocks.zleep.ui.theme.Dimens
import at.zocks.zleep.ui.theme.ZocksTheme

@Composable
fun SettingsScreen(onOpenDeveloperOptions: () -> Unit, modifier: Modifier = Modifier) {
    val soon = stringResource(R.string.settings_coming_soon)

    ScreenColumn(title = stringResource(R.string.nav_settings), modifier = modifier.testTag("screen_settings")) {
        ZocksCard(title = stringResource(R.string.settings_section_general)) {
            SettingsRow(Icons.Outlined.Straighten, stringResource(R.string.settings_units), stringResource(R.string.settings_units_value))
            SettingsRow(Icons.Outlined.Notifications, stringResource(R.string.settings_notifications), soon)
        }
        ZocksCard(title = stringResource(R.string.settings_section_devices)) {
            SettingsRow(Icons.Outlined.Devices, stringResource(R.string.settings_devices), stringResource(R.string.settings_devices_value))
        }
        ZocksCard(title = stringResource(R.string.settings_section_data)) {
            SettingsRow(Icons.Outlined.FileDownload, stringResource(R.string.settings_export_csv), soon)
            SettingsRow(Icons.Outlined.FavoriteBorder, stringResource(R.string.settings_health_connect), soon)
            SettingsRow(
                Icons.Outlined.DeleteOutline,
                stringResource(R.string.settings_delete_data),
                soon,
                iconTint = MaterialTheme.colorScheme.error,
            )
            HintRow(Icons.Outlined.CloudOff, stringResource(R.string.settings_offline_hint))
        }
        ZocksCard(title = stringResource(R.string.settings_section_about)) {
            SettingsRow(Icons.Outlined.Info, stringResource(R.string.settings_version), BuildConfig.VERSION_NAME)
            SettingsRow(
                Icons.Outlined.Code,
                stringResource(R.string.settings_developer),
                stringResource(R.string.dev_mode_simulator_body),
                onClick = onOpenDeveloperOptions,
                testTag = "settings_developer",
            )
        }
        WellnessNotice()
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    value: String,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    testTag: String? = null,
) {
    val base = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = { Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingContent = { Icon(icon, contentDescription = null, tint = iconTint) },
        trailingContent = onClick?.let {
            { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = base
            .semantics(mergeDescendants = true) {}
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    )
}

@Composable
private fun HintRow(icon: ImageVector, text: String) {
    Row(Modifier.padding(top = Dimens.SpaceS), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Dimens.SpaceS),
        )
    }
}

/** Pflichthinweis: Wellnessprodukt, kein Medizinprodukt. */
@Composable
private fun WellnessNotice() {
    ZocksCard(modifier = Modifier.testTag("wellness_notice")) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Icon(Icons.Outlined.Spa, null, tint = MaterialTheme.colorScheme.tertiary)
            Text(
                text = stringResource(R.string.wellness_title) + "\n" + stringResource(R.string.wellness_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Dimens.SpaceM),
            )
        }
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    ZocksTheme { SettingsScreen(onOpenDeveloperOptions = {}) }
}
