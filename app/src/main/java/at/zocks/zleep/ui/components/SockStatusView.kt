package at.zocks.zleep.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BluetoothConnected
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import at.zocks.zleep.R
import at.zocks.zleep.domain.device.SockStatus
import at.zocks.zleep.domain.model.ConnectionState
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.ui.theme.Dimens
import at.zocks.zleep.ui.theme.ZocksThemeExt

/** Status einer Socke: Seite, Verbindung, Akku, ggf. „heizt“. */
@Composable
fun SockStatusView(status: SockStatus?, side: SockSide, modifier: Modifier = Modifier) {
    val sideLabel = stringResource(if (side == SockSide.RIGHT) R.string.sock_right else R.string.sock_left)
    val connection = status?.connection ?: ConnectionState.DISCONNECTED
    val connectionLabel = stringResource(
        when (connection) {
            ConnectionState.CONNECTED -> R.string.connection_connected
            ConnectionState.CONNECTING -> R.string.connection_connecting
            ConnectionState.RECONNECTING -> R.string.connection_reconnecting
            ConnectionState.DISCONNECTED -> R.string.sock_disconnected
        },
    )
    val details = buildList {
        add(connectionLabel)
        if (connection == ConnectionState.CONNECTED) {
            status?.batteryPercent?.let { add(stringResource(R.string.battery_level, it)) }
            if (status?.heating == true) add(stringResource(R.string.sock_heating))
        }
    }.joinToString(" · ")
    val description = stringResource(R.string.sock_status_description, sideLabel, details)

    val (icon, tint) = when (connection) {
        ConnectionState.CONNECTED ->
            Icons.Outlined.BluetoothConnected to if (status?.heating == true) ZocksThemeExt.colors.heat else MaterialTheme.colorScheme.primary
        ConnectionState.CONNECTING, ConnectionState.RECONNECTING ->
            Icons.AutoMirrored.Outlined.BluetoothSearching to MaterialTheme.colorScheme.onSurfaceVariant
        ConnectionState.DISCONNECTED -> Icons.Outlined.BluetoothDisabled to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
        Column(Modifier.padding(start = Dimens.SpaceS)) {
            Text(sideLabel, style = MaterialTheme.typography.titleMedium)
            Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
