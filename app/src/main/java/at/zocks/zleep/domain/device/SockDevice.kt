package at.zocks.zleep.domain.device

import at.zocks.zleep.domain.model.ConnectionState
import at.zocks.zleep.domain.model.DeviceCapabilities
import at.zocks.zleep.domain.model.HeatCommand
import at.zocks.zleep.domain.model.HeatState
import at.zocks.zleep.domain.model.MassageCommand
import at.zocks.zleep.domain.model.MassageState
import at.zocks.zleep.domain.model.SensorSample
import at.zocks.zleep.domain.model.SockSide
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Eine einzelne Socke (jede hat ihr eigenes Elektronikmodul). Umgesetzt vom
 * Simulator und – ab Phase 7 – von der echten BLE-Anbindung.
 *
 * Heizbefehle dürfen nur über den HeatSafetyGuard an ein Gerät gehen (Phase 3).
 */
interface SockDevice {
    /** [SockSide.LEFT] oder [SockSide.RIGHT]. */
    val side: SockSide
    val capabilities: DeviceCapabilities
    val connectionState: StateFlow<ConnectionState>

    /** Akkustand in Prozent, `null` solange unbekannt. */
    val batteryPercent: StateFlow<Int?>
    val heatState: StateFlow<HeatState>
    val massageState: StateFlow<MassageState>

    /** Messwerte, solange die Socke verbunden ist. Kalt: jeder Sammler bekommt den laufenden Strom. */
    val sensorData: Flow<SensorSample>

    suspend fun connect()
    suspend fun disconnect()
    suspend fun setHeat(command: HeatCommand)
    suspend fun stopHeat()
    suspend fun startMassage(command: MassageCommand)

    /** Beendet die Massage; mit [fadeOutMs] > 0 klingt sie sanft aus. */
    suspend fun stopMassage(fadeOutMs: Long = 0)
}
