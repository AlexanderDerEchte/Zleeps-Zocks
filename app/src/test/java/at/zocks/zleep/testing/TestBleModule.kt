package at.zocks.zleep.testing

import at.zocks.zleep.device.ble.GattConnection
import at.zocks.zleep.device.ble.GattConnectionFactory
import at.zocks.zleep.di.BleModule
import at.zocks.zleep.domain.device.BluetoothAvailability
import at.zocks.zleep.domain.device.DiscoveredSock
import at.zocks.zleep.domain.device.SockScanner
import at.zocks.zleep.domain.model.SockSide
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/** Zwei Socken in Reichweite, gesteuert von [FakeSockFirmware]. Pro Test neu (Hilt-Komponente). */
@Singleton
class FakeBleWorld @Inject constructor() : SockScanner, GattConnectionFactory {
    val left = FakeSockFirmware(LEFT_ADDRESS, SockSide.LEFT, battery = 81)
    val right = FakeSockFirmware(RIGHT_ADDRESS, SockSide.RIGHT, battery = 77)

    @Volatile
    var availability = BluetoothAvailability.READY

    override val requiredPermissions: List<String> = emptyList()

    override fun availability() = availability

    /** Findet beide Socken sofort; die linke hat das stärkere Signal. */
    override fun scan(): Flow<List<DiscoveredSock>> = flow {
        emit(
            listOf(
                DiscoveredSock(LEFT_ADDRESS, "Zocks L", SockSide.LEFT, left.battery, rssi = -52),
                DiscoveredSock(RIGHT_ADDRESS, "Zocks R", SockSide.RIGHT, right.battery, rssi = -64),
            ),
        )
    }

    override fun open(address: String): GattConnection = when (address) {
        LEFT_ADDRESS -> left.open()
        RIGHT_ADDRESS -> right.open()
        else -> error("Unbekannte Socke $address")
    }

    companion object {
        const val LEFT_ADDRESS = "AA:BB:CC:00:00:01"
        const val RIGHT_ADDRESS = "AA:BB:CC:00:00:02"
    }
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [BleModule::class])
abstract class TestBleModule {
    @Binds
    abstract fun scanner(world: FakeBleWorld): SockScanner

    @Binds
    abstract fun gattFactory(world: FakeBleWorld): GattConnectionFactory
}
