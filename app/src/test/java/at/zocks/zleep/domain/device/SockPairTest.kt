package at.zocks.zleep.domain.device

import app.cash.turbine.test
import at.zocks.zleep.domain.model.ConnectionState
import at.zocks.zleep.domain.model.SockSide
import at.zocks.zleep.testing.FakeSockDevice
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class SockPairTest {

    private val left = FakeSockDevice(SockSide.LEFT)
    private val right = FakeSockDevice(SockSide.RIGHT)
    private val pair = SockPair(left, right)

    @Test
    fun `sides must not be swapped`() {
        assertThrows(IllegalArgumentException::class.java) { SockPair(right, left) }
    }

    @Test
    fun `both is the default target`() = runTest {
        pair.connect()
        assertThat(left.commands).containsExactly("connect")
        assertThat(right.commands).containsExactly("connect")
    }

    @Test
    fun `single side commands reach only that sock`() = runTest {
        pair.connect(SockSide.RIGHT)
        assertThat(left.commands).isEmpty()
        assertThat(right.commands).containsExactly("connect")
        assertThat(pair.devices(SockSide.LEFT)).containsExactly(left)
        assertThat(pair.devices(SockSide.BOTH)).containsExactly(left, right).inOrder()
    }

    @Test
    fun `status combines both socks`() = runTest {
        pair.status.test {
            val initial = awaitItem()
            assertThat(initial.anyConnected).isFalse()

            left.connectionState.value = ConnectionState.CONNECTED
            left.batteryPercent.value = 80
            val leftOnly = expectMostRecentItem()
            assertThat(leftOnly.anyConnected).isTrue()
            assertThat(leftOnly.bothConnected).isFalse()
            assertThat(leftOnly.of(SockSide.LEFT).batteryPercent).isEqualTo(80)

            right.connectionState.value = ConnectionState.CONNECTED
            right.heatState.value = right.heatState.value.copy(active = true, level = 2)
            val both = expectMostRecentItem()
            assertThat(both.bothConnected).isTrue()
            assertThat(both.anyHeating).isTrue()
        }
    }
}
