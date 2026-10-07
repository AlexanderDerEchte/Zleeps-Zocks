package at.zocks.zleep.testing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import at.zocks.zleep.ui.components.LOADING_TAG

/** Großzügig gewählt: CI-Runner sind deutlich langsamer als ein Entwicklerrechner. */
const val UI_TIMEOUT_MS = 15_000L

/** Länger als eine kurze Snackbar (4 s) samt Ausblenden. */
const val SNACKBAR_GONE_MS = 6_000L

/** Wartet, bis ein Knoten sichtbar ist – z. B. nach einer Übergangsanimation. */
fun ComposeTestRule.waitUntilDisplayed(tag: String, timeoutMillis: Long = UI_TIMEOUT_MS) {
    waitUntil(timeoutMillis) { runCatching { onNodeWithTag(tag).assertIsDisplayed() }.isSuccess }
}

/**
 * Wartet, bis kein Ladezustand mehr sichtbar ist. Vorher zu tippen ist unzuverlässig: Wenn die
 * Daten eintreffen, verschiebt sich das Layout und ein Klick kann danebengehen.
 */
fun ComposeTestRule.waitUntilLoaded(timeoutMillis: Long = UI_TIMEOUT_MS) {
    waitUntil(timeoutMillis) { onAllNodes(hasTestTag(LOADING_TAG)).fetchSemanticsNodes().isEmpty() }
    waitForIdle()
}
