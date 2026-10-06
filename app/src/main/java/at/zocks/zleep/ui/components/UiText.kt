package at.zocks.zleep.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Text aus Ressourcen, der erst in der Oberfläche aufgelöst wird. Argumente dürfen selbst
 * [UiText] sein (z. B. „Links“ als Teil einer Meldung).
 */
sealed interface UiText {
    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    companion object {
        fun of(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())
    }
}

@Composable
fun UiText.resolve(): String = when (this) {
    is UiText.Res -> {
        val resolved = args.map { if (it is UiText) it.resolve() else it }
        stringResource(id, *resolved.toTypedArray())
    }
}
