package at.zocks.zleep.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.zocks.zleep.MainActivity
import at.zocks.zleep.domain.device.BluetoothAvailability
import at.zocks.zleep.domain.model.DeviceMode
import at.zocks.zleep.domain.model.PairedSocks
import at.zocks.zleep.domain.repository.SettingsRepository
import at.zocks.zleep.testing.FakeBleWorld
import at.zocks.zleep.testing.UI_TIMEOUT_MS
import at.zocks.zleep.testing.waitUntilDisplayed
import at.zocks.zleep.testing.waitUntilLoaded
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import javax.inject.Inject

/** Ablauf: echte Socken (Fake-Firmware) suchen, links/rechts zuordnen, verbinden. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
class PairingFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var ble: FakeBleWorld

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking { settings.update { it.copy(deviceMode = DeviceMode.BLE) } }
        composeRule.waitUntilLoaded()
    }

    private fun waitForTag(tag: String) = composeRule.waitUntil(UI_TIMEOUT_MS) {
        composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun pairBothSocksFromHomeAndConnect() {
        // Ohne gekoppelte Socke führt „Socken koppeln“ zur Suche.
        composeRule.onNodeWithTag("action_connect_socks").performScrollTo().performClick()
        composeRule.waitUntilDisplayed("screen_pairing")
        waitForTag("found_sock_1")

        composeRule.onNodeWithTag("assign_left_0").performScrollTo().performClick()
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            runCatching { composeRule.onNodeWithTag("assign_left_0").assertIsSelected() }.isSuccess
        }
        composeRule.onNodeWithTag("assign_right_1").performScrollTo().performClick()
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            runCatching { composeRule.onNodeWithTag("assign_right_1").assertIsSelected() }.isSuccess
        }

        composeRule.onNodeWithTag("pairing_connect").performScrollTo().performClick()
        waitForTag("pairing_done")
        assertThat(ble.left.current?.connected).isTrue()
        assertThat(ble.right.current?.connected).isTrue()

        // Gespeichert erst prüfen, nachdem die Oberfläche es gezeigt hat.
        val stored = runBlocking { settings.settings.first().pairedSocks }
        assertThat(stored).isEqualTo(PairedSocks(FakeBleWorld.LEFT_ADDRESS, FakeBleWorld.RIGHT_ADDRESS))

        composeRule.onNodeWithTag("action_back").performClick()
        composeRule.waitUntilDisplayed("screen_home")
        waitForTag("action_disconnect_socks")
    }

    @Test
    fun unpairingASockDisconnectsIt() {
        runBlocking {
            settings.update { it.copy(pairedSocks = PairedSocks(FakeBleWorld.LEFT_ADDRESS, FakeBleWorld.RIGHT_ADDRESS)) }
        }
        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.waitUntilDisplayed("screen_settings")
        composeRule.onNodeWithTag("settings_pair").performScrollTo().performClick()
        composeRule.waitUntilDisplayed("screen_pairing")

        composeRule.onNodeWithTag("pairing_connect").performScrollTo().performClick()
        waitForTag("pairing_done")

        composeRule.onNodeWithTag("unpair_right").performScrollTo().performClick()
        // Die rechte Seite ist wieder frei, die Verbindung zur Socke wird getrennt.
        waitForTag("pairing_connect")
        composeRule.waitUntil(UI_TIMEOUT_MS) { ble.right.current?.connected == false }
        assertThat(ble.left.current?.connected).isTrue()
    }

    @Test
    fun missingPermissionIsExplained() {
        ble.availability = BluetoothAvailability.NO_PERMISSION
        composeRule.onNodeWithTag("action_connect_socks").performScrollTo().performClick()
        composeRule.waitUntilDisplayed("screen_pairing")
        composeRule.waitUntilDisplayed("pairing_permission")

        // Nach der Freigabe (Rückkehr zur App) startet die Suche.
        ble.availability = BluetoothAvailability.READY
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        waitForTag("found_sock_0")
        composeRule.onNodeWithTag("pairing_nearby").assertIsDisplayed()
    }
}
