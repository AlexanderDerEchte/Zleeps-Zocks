package at.zocks.zleep.testing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag

/** Wartet, bis ein Knoten sichtbar ist – z. B. nach einer Übergangsanimation. */
fun ComposeTestRule.waitUntilDisplayed(tag: String, timeoutMillis: Long = 5_000) {
    waitUntil(timeoutMillis) { runCatching { onNodeWithTag(tag).assertIsDisplayed() }.isSuccess }
}
