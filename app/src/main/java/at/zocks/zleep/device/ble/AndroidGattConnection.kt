package at.zocks.zleep.device.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject

/**
 * GATT-Verbindung über die Android-Bluetooth-API. Android erlaubt nur einen GATT-Vorgang
 * gleichzeitig: jeder Vorgang wartet auf den vorigen ([queue]) und auf seinen Rückruf,
 * höchstens [OPERATION_TIMEOUT_MS]. Ohne Berechtigung „Geräte in der Nähe“ schlagen die
 * Vorgänge fehl (SecurityException wird abgefangen), statt die App zu beenden.
 */
class AndroidGattConnection(
    private val context: Context,
    private val address: String,
) : GattConnection {

    private val channel = Channel<GattEvent>(Channel.UNLIMITED)
    override val events: Flow<GattEvent> = channel.receiveAsFlow()

    private val queue = Mutex()

    @Volatile
    private var gatt: BluetoothGatt? = null

    /** Wartet gerade auf einen Rückruf: Ergebnis des laufenden Vorgangs. */
    @Volatile
    private var pending: CompletableDeferred<Result>? = null

    private class Result(val ok: Boolean, val value: ByteArray? = null, val mtu: Int = 0)

    private val device: BluetoothDevice? by lazy {
        runCatching { context.getSystemService(BluetoothManager::class.java)?.adapter?.getRemoteDevice(address) }.getOrNull()
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                complete(Result(true))
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                complete(Result(false))
                channel.trySend(GattEvent.Disconnected(status))
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) = complete(Result(status == BluetoothGatt.GATT_SUCCESS))

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) =
            complete(Result(status == BluetoothGatt.GATT_SUCCESS, mtu = mtu))

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) =
            complete(Result(status == BluetoothGatt.GATT_SUCCESS, value.copyOf()))

        @Deprecated("Bis Android 12")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) =
            complete(Result(status == BluetoothGatt.GATT_SUCCESS, characteristic.value?.copyOf()))

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) =
            complete(Result(status == BluetoothGatt.GATT_SUCCESS))

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) =
            complete(Result(status == BluetoothGatt.GATT_SUCCESS))

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            channel.trySend(GattEvent.Notification(characteristic.uuid, value.copyOf()))
        }

        @Deprecated("Bis Android 12")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val value = characteristic.value ?: return
            channel.trySend(GattEvent.Notification(characteristic.uuid, value.copyOf()))
        }
    }

    private fun complete(result: Result) {
        pending?.complete(result)
    }

    /** Führt einen Vorgang aus und wartet auf seinen Rückruf; `null` bei Fehler oder Zeitüberschreitung. */
    private suspend fun operation(timeoutMs: Long = OPERATION_TIMEOUT_MS, start: (BluetoothGatt?) -> Boolean): Result? = queue.withLock {
        val deferred = CompletableDeferred<Result>()
        pending = deferred
        try {
            // Jeder Vorgang fängt eine fehlende Berechtigung (SecurityException) selbst ab.
            if (!start(gatt)) return@withLock null
            withTimeoutOrNull(timeoutMs) { deferred.await() }?.takeIf { it.ok }
        } finally {
            pending = null
        }
    }

    override suspend fun connect(): Boolean {
        val target = device ?: return false
        val connected = operation(CONNECT_TIMEOUT_MS) {
            val opened = try {
                target.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            } catch (_: SecurityException) {
                null
            }
            gatt = opened
            opened != null
        } != null
        if (!connected) return false
        return operation { gatt ->
            try {
                gatt?.discoverServices() == true
            } catch (_: SecurityException) {
                false
            }
        } != null
    }

    override val isBonded: Boolean
        get() = try {
            device?.bondState == BluetoothDevice.BOND_BONDED
        } catch (_: SecurityException) {
            false
        }

    override suspend fun bond(): Boolean {
        val target = device ?: return false
        if (isBonded) return true
        val bonded = CompletableDeferred<Boolean>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val changed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                if (changed?.address != address) return
                when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                    BluetoothDevice.BOND_BONDED -> bonded.complete(true)
                    BluetoothDevice.BOND_NONE -> bonded.complete(false)
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        return try {
            val started = try {
                target.createBond()
            } catch (_: SecurityException) {
                false
            }
            started && withTimeoutOrNull(BOND_TIMEOUT_MS) { bonded.await() } == true
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    override suspend fun requestMtu(mtu: Int): Int =
        operation { gatt ->
            try {
                gatt?.requestMtu(mtu) == true
            } catch (_: SecurityException) {
                false
            }
        }?.mtu ?: BleSockDevice.DEFAULT_MTU

    override suspend fun read(service: UUID, characteristic: UUID): ByteArray? = operation { gatt ->
        val target = gatt?.getService(service)?.getCharacteristic(characteristic) ?: return@operation false
        try {
            gatt.readCharacteristic(target)
        } catch (_: SecurityException) {
            false
        }
    }?.value

    override suspend fun write(service: UUID, characteristic: UUID, value: ByteArray): Boolean = operation { gatt ->
        val target = gatt?.getService(service)?.getCharacteristic(characteristic) ?: return@operation false
        val type = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(target, value, type) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                target.writeType = type
                @Suppress("DEPRECATION")
                target.value = value
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(target)
            }
        } catch (_: SecurityException) {
            false
        }
    } != null

    override suspend fun enableNotifications(service: UUID, characteristic: UUID): Boolean = operation { gatt ->
        val target = gatt?.getService(service)?.getCharacteristic(characteristic) ?: return@operation false
        val descriptor = target.getDescriptor(SockBleProtocol.CCCD) ?: return@operation false
        val enable = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        try {
            if (!gatt.setCharacteristicNotification(target, true)) {
                false
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, enable) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = enable
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        } catch (_: SecurityException) {
            false
        }
    } != null

    override fun close() {
        val current = gatt ?: return
        gatt = null
        try {
            current.disconnect()
            current.close()
        } catch (_: SecurityException) {
            // Ohne Berechtigung lässt sich nichts mehr trennen – die Verbindung ist dann ohnehin weg.
        }
        pending?.complete(Result(false))
        channel.close()
    }

    companion object {
        const val OPERATION_TIMEOUT_MS = 10_000L
        const val CONNECT_TIMEOUT_MS = 20_000L
        const val BOND_TIMEOUT_MS = 30_000L
    }
}

/** Öffnet echte GATT-Verbindungen. */
class AndroidGattConnectionFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : GattConnectionFactory {
    override fun open(address: String): GattConnection = AndroidGattConnection(context, address)
}
