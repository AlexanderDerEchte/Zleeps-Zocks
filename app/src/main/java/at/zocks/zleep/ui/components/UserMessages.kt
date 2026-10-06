package at.zocks.zleep.ui.components

import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource

/** Snackbar der App, bereitgestellt von ZocksApp. */
val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("Kein SnackbarHostState bereitgestellt")
}

/**
 * Zeigt eine einmalige Meldung aus dem UiState als Snackbar und meldet sie danach als
 * gezeigt, damit sie nicht erneut erscheint.
 */
@Composable
fun UserMessageEffect(@StringRes message: Int?, onShown: () -> Unit) {
    val snackbar = LocalSnackbarHostState.current
    val text = message?.let { stringResource(it) }
    val currentOnShown = rememberUpdatedState(onShown)
    LaunchedEffect(message) {
        if (text != null) {
            snackbar.showSnackbar(text)
            currentOnShown.value()
        }
    }
}
