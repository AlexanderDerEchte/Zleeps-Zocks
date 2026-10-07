package at.zocks.zleep.device.ble

import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Was eine GATT-Verbindung meldet, ohne dass danach gefragt wurde. */
sealed interface GattEvent {
    class Notification(val characteristic: UUID, val value: ByteArray) : GattEvent

    /** Verbindung beendet (vom Gerät, durch Funkabbruch oder durch [GattConnection.close]). */
    data class Disconnected(val status: Int) : GattEvent
}

/**
 * Eine Verbindung zu einer Socke. Alle Vorgänge laufen nacheinander (GATT erlaubt nur
 * einen gleichzeitig) und brechen nach einer Zeitspanne mit einem Fehlschlag ab.
 * Die Android-Umsetzung ist [AndroidGattConnection]; Tests nutzen eine Attrappe.
 */
interface GattConnection {
    /** Benachrichtigungen und Verbindungsende; gepuffert, damit nichts verloren geht. */
    val events: Flow<GattEvent>

    /** Verbindet und sucht die Dienste; `false` bei Fehler oder Zeitüberschreitung. */
    suspend fun connect(): Boolean

    val isBonded: Boolean

    /** Koppelt das Gerät (Bonding); `true`, wenn es danach gekoppelt ist. */
    suspend fun bond(): Boolean

    /** Fragt eine größere MTU an; liefert die ausgehandelte (mindestens 23). */
    suspend fun requestMtu(mtu: Int): Int

    suspend fun read(service: UUID, characteristic: UUID): ByteArray?
    suspend fun write(service: UUID, characteristic: UUID, value: ByteArray): Boolean
    suspend fun enableNotifications(service: UUID, characteristic: UUID): Boolean

    /** Trennt und gibt alle Ressourcen frei. */
    fun close()
}

/** Öffnet Verbindungen zu einer Bluetooth-Adresse. */
fun interface GattConnectionFactory {
    fun open(address: String): GattConnection
}

/** Ein Befehl kam nicht bei der Socke an. */
class SockCommandException(message: String) : Exception(message)
