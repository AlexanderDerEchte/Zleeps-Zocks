package at.zocks.zleep.device.ble

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import at.zocks.zleep.domain.device.BluetoothAvailability
import at.zocks.zleep.domain.device.DiscoveredSock
import at.zocks.zleep.domain.device.SockScanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sucht Socken über die Android-Bluetooth-API. Gefiltert wird auf den Dienst
 * [SockBleProtocol.SERVICE]; Seite und Akku stehen (sofern gesendet) in den Herstellerdaten.
 */
@Singleton
class BleSockScanner @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SockScanner {

    override val requiredPermissions: List<String> get() = requiredPermissions().toList()

    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    override fun availability(): BluetoothAvailability {
        val adapter = adapter
        return when {
            adapter == null || !context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) ->
                BluetoothAvailability.UNSUPPORTED
            !hasPermissions(context) -> BluetoothAvailability.NO_PERMISSION
            !adapter.isEnabled -> BluetoothAvailability.OFF
            else -> BluetoothAvailability.READY
        }
    }

    override fun scan(): Flow<List<DiscoveredSock>> = callbackFlow {
        val scanner = adapter?.takeIf { availability() == BluetoothAvailability.READY }?.bluetoothLeScanner
        if (scanner == null) {
            close(ScanFailedException(ScanCallback.SCAN_FAILED_INTERNAL_ERROR))
            return@callbackFlow
        }
        val found = linkedMapOf<String, DiscoveredSock>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                add(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach(::add)
            }

            override fun onScanFailed(errorCode: Int) {
                close(ScanFailedException(errorCode))
            }

            private fun add(result: ScanResult) {
                val record = result.scanRecord
                val advertisement = SockBleProtocol.parseAdvertisement(
                    record?.getManufacturerSpecificData(SockBleProtocol.MANUFACTURER_ID),
                )
                val sock = DiscoveredSock(
                    address = result.device.address,
                    name = record?.deviceName,
                    side = advertisement?.side,
                    batteryPercent = advertisement?.batteryPercent,
                    rssi = result.rssi,
                )
                val list = synchronized(found) {
                    found[sock.address] = sock
                    found.values.sortedByDescending { it.rssi }
                }
                trySend(list)
            }
        }
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SockBleProtocol.SERVICE)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            scanner.startScan(filters, settings, callback)
        } catch (e: SecurityException) {
            close(e)
            return@callbackFlow
        }
        trySend(emptyList())
        awaitClose {
            try {
                scanner.stopScan(callback)
            } catch (_: SecurityException) {
                // Berechtigung inzwischen entzogen: die Suche endet ohnehin.
            } catch (_: IllegalStateException) {
                // Bluetooth inzwischen ausgeschaltet.
            }
        }
    }.conflate()

    companion object {
        /** Was zum Suchen und Verbinden nötig ist: ab Android 12 „Geräte in der Nähe“, davor Standort. */
        fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        fun hasPermissions(context: Context): Boolean = requiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}

/** Die Suche ist fehlgeschlagen (Fehlercode aus [ScanCallback]). */
class ScanFailedException(val errorCode: Int) : Exception("BLE-Suche fehlgeschlagen: $errorCode")
