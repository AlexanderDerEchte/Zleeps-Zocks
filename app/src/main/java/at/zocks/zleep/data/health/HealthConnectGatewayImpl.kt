package at.zocks.zleep.data.health

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import at.zocks.zleep.domain.health.HealthAvailability
import at.zocks.zleep.domain.health.HealthConnectGateway
import at.zocks.zleep.domain.health.HealthSleepSession
import at.zocks.zleep.domain.model.SleepStage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schreibt Nächte nach Health Connect: eine Schlafsession mit Phasen und Pulswerte in
 * Stundenstücken. Die eigene Kennung (`clientRecordId`) sorgt dafür, dass erneutes
 * Übertragen dieselben Einträge ersetzt statt sie zu verdoppeln.
 */
@Singleton
class HealthConnectGatewayImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock,
) : HealthConnectGateway {

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    override val requiredPermissions: Set<String> = PERMISSIONS

    override fun availability(): HealthAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.UPDATE_REQUIRED
        // Health Connect gibt es ab Android 9.
        else -> if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) HealthAvailability.NOT_SUPPORTED else HealthAvailability.NOT_INSTALLED
    }

    override suspend fun hasPermissions(): Boolean =
        client.permissionController.getGrantedPermissions().containsAll(PERMISSIONS)

    override suspend fun write(session: HealthSleepSession) {
        // Höhere Version ersetzt einen früher übertragenen Eintrag mit gleicher Kennung.
        val version = clock.millis()
        val records = mutableListOf<Record>()
        records += SleepSessionRecord(
            startTime = session.start,
            startZoneOffset = offset(session.start),
            endTime = session.end,
            endZoneOffset = offset(session.end),
            metadata = Metadata.autoRecorded(DEVICE, session.id, version),
            title = null,
            notes = session.notes,
            stages = session.stages.map { SleepSessionRecord.Stage(it.start, it.end, stageType(it.stage)) },
        )
        session.heartRate
            .groupBy { it.time.epochSecond / HOUR_SECONDS }
            .values
            .forEachIndexed { index, chunk ->
                val start = chunk.first().time
                val end = chunk.last().time.plusSeconds(1)
                records += HeartRateRecord(
                    startTime = start,
                    startZoneOffset = offset(start),
                    endTime = end,
                    endZoneOffset = offset(end),
                    samples = chunk.map { HeartRateRecord.Sample(it.time, it.bpm) },
                    metadata = Metadata.autoRecorded(DEVICE, "${session.id}-hr-$index", version),
                )
            }
        client.insertRecords(records)
    }

    private fun offset(instant: Instant) = ZoneId.systemDefault().rules.getOffset(instant)

    private fun stageType(stage: SleepStage): Int = when (stage) {
        SleepStage.AWAKE -> SleepSessionRecord.STAGE_TYPE_AWAKE
        SleepStage.LIGHT -> SleepSessionRecord.STAGE_TYPE_LIGHT
        SleepStage.DEEP -> SleepSessionRecord.STAGE_TYPE_DEEP
        SleepStage.REM -> SleepSessionRecord.STAGE_TYPE_REM
    }

    companion object {
        val PERMISSIONS: Set<String> = setOf(
            HealthPermission.getWritePermission(SleepSessionRecord::class),
            HealthPermission.getWritePermission(HeartRateRecord::class),
        )
        private const val HOUR_SECONDS = 3_600L
        private val DEVICE = Device(type = Device.TYPE_UNKNOWN, manufacturer = "Zocks", model = "Zleep")
    }
}
