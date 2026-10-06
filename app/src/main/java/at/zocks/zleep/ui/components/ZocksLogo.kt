package at.zocks.zleep.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.zocks.zleep.R

/** Zocks-Logo (Socke mit „zzz“). Dekorativ, daher ohne Content Description. */
@Composable
fun ZocksLogo(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Icon(
        painter = painterResource(R.drawable.ic_zocks_logo),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier.size(size),
    )
}
