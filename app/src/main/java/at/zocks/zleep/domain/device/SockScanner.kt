package at.zocks.zleep.domain.device

import at.zocks.zleep.domain.model.SockSide
import kotlinx.coroutines.flow.Flow

/** Ob nach Socken gesucht werden kann. */
enum class BluetoothAvailability {
    READY,

    /** Bluetooth ist ausgeschaltet. */
    OFF,

    /** „Geräte in der Nähe“ (bzw. bis Android 11 Standort) ist nicht erlaubt. */
    NO_PERMISSION,

    /** Das Handy hat kein Bluetooth Low Energy. */
    UNSUPPORTED,
}

/** Eine gefundene Socke. [side] und [batteryPercent] stehen in ihrer Werbung, sofern sie es mitteilt. */
data class DiscoveredSock(
    val address: String,
    val name: String?,
    val side: SockSide?,
    val batteryPercent: Int?,
    val rssi: Int,
)

/** Sucht Socken in der Nähe. */
interface SockScanner {
    /** Laufzeit-Berechtigungen (Android-Namen), ohne die weder gesucht noch verbunden werden kann. */
    val requiredPermissions: List<String>

    fun availability(): BluetoothAvailability

    /** Alle bisher gefundenen Socken, stärkstes Signal zuerst. Sucht, solange gesammelt wird. */
    fun scan(): Flow<List<DiscoveredSock>>
}
