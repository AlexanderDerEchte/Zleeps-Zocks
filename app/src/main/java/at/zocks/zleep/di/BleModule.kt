package at.zocks.zleep.di

import at.zocks.zleep.device.ble.AndroidGattConnectionFactory
import at.zocks.zleep.device.ble.BleSockScanner
import at.zocks.zleep.device.ble.GattConnectionFactory
import at.zocks.zleep.domain.device.SockScanner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Echte Bluetooth-Anbindung. UI-Tests ersetzen das Modul durch eine Fake-Firmware. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BleModule {
    @Binds
    abstract fun gattConnectionFactory(impl: AndroidGattConnectionFactory): GattConnectionFactory

    @Binds
    abstract fun sockScanner(impl: BleSockScanner): SockScanner
}
