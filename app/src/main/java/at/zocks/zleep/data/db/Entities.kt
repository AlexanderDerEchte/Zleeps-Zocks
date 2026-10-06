package at.zocks.zleep.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import at.zocks.zleep.domain.massage.MassageProgramType
import at.zocks.zleep.domain.massage.MassageTempo
import at.zocks.zleep.domain.model.NightEventType
import at.zocks.zleep.domain.model.NightSource
import at.zocks.zleep.domain.model.SleepStage
import at.zocks.zleep.domain.model.SockSide

// Zeitpunkte werden als Epoch-Millisekunden (UTC) gespeichert.

@Entity(tableName = "nights", indices = [Index("start_ms"), Index("source")])
data class NightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "start_ms") val startMs: Long,
    @ColumnInfo(name = "end_ms") val endMs: Long?,
    @ColumnInfo(name = "sleep_onset_ms") val sleepOnsetMs: Long?,
    @ColumnInfo(name = "final_wake_ms") val finalWakeMs: Long?,
    val source: NightSource,
    val note: String?,
    @ColumnInfo(name = "sleep_window_manual", defaultValue = "0") val sleepWindowManual: Boolean = false,
)

/** Messwerte einer Socke, verdichtet auf eine 30-s-Epoche. */
@Entity(
    tableName = "epoch_measurements",
    primaryKeys = ["night_id", "side", "start_ms"],
    foreignKeys = [
        ForeignKey(entity = NightEntity::class, parentColumns = ["id"], childColumns = ["night_id"], onDelete = ForeignKey.CASCADE),
    ],
)
data class EpochMeasurementEntity(
    @ColumnInfo(name = "night_id") val nightId: Long,
    val side: SockSide,
    @ColumnInfo(name = "start_ms") val startMs: Long,
    @ColumnInfo(name = "heart_rate") val heartRate: Float?,
    @ColumnInfo(name = "hrv_rmssd") val hrvRmssd: Float?,
    val spo2: Float?,
    @ColumnInfo(name = "skin_temperature") val skinTemperature: Float?,
    val motion: Float?,
    @ColumnInfo(name = "sample_count") val sampleCount: Int,
)

@Entity(
    tableName = "sleep_stages",
    primaryKeys = ["night_id", "start_ms"],
    foreignKeys = [
        ForeignKey(entity = NightEntity::class, parentColumns = ["id"], childColumns = ["night_id"], onDelete = ForeignKey.CASCADE),
    ],
)
data class SleepStageEntity(
    @ColumnInfo(name = "night_id") val nightId: Long,
    @ColumnInfo(name = "start_ms") val startMs: Long,
    val stage: SleepStage,
)

@Entity(
    tableName = "night_events",
    indices = [Index("night_id")],
    foreignKeys = [
        ForeignKey(entity = NightEntity::class, parentColumns = ["id"], childColumns = ["night_id"], onDelete = ForeignKey.CASCADE),
    ],
)
data class NightEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "night_id") val nightId: Long,
    val type: NightEventType,
    val side: SockSide,
    @ColumnInfo(name = "start_ms") val startMs: Long,
    @ColumnInfo(name = "end_ms") val endMs: Long?,
    val detail: String?,
)

@Entity(
    tableName = "connection_gaps",
    indices = [Index("night_id")],
    foreignKeys = [
        ForeignKey(entity = NightEntity::class, parentColumns = ["id"], childColumns = ["night_id"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ConnectionGapEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "night_id") val nightId: Long,
    val side: SockSide,
    @ColumnInfo(name = "start_ms") val startMs: Long,
    @ColumnInfo(name = "end_ms") val endMs: Long?,
)

/** Eingebaute Tags haben einen [key], eigene ein [label]. */
@Entity(tableName = "tags", indices = [Index(value = ["key"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String?,
    val label: String?,
)

@Entity(
    tableName = "night_tags",
    primaryKeys = ["night_id", "tag_id"],
    indices = [Index("tag_id")],
    foreignKeys = [
        ForeignKey(entity = NightEntity::class, parentColumns = ["id"], childColumns = ["night_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tag_id"], onDelete = ForeignKey.CASCADE),
    ],
)
data class NightTagCrossRef(
    @ColumnInfo(name = "night_id") val nightId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
)

data class NightWithTags(
    @Embedded val night: NightEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(NightTagCrossRef::class, parentColumn = "night_id", entityColumn = "tag_id"),
    )
    val tags: List<TagEntity>,
)

/** Eigenes Massageprogramm (seit Schema-Version 2). */
@Entity(tableName = "massage_programs")
data class MassageProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: MassageProgramType,
    val intensity: Int,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int,
    val tempo: MassageTempo,
    /** Kommagetrennte Zonen, z. B. „HEEL,BALL“. */
    val zones: String,
)
