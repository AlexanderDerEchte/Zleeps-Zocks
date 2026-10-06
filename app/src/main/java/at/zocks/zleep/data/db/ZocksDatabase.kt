package at.zocks.zleep.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import at.zocks.zleep.domain.model.Tag

@Database(
    entities = [
        NightEntity::class,
        EpochMeasurementEntity::class,
        SleepStageEntity::class,
        NightEventEntity::class,
        ConnectionGapEntity::class,
        TagEntity::class,
        NightTagCrossRef::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ZocksDatabase : RoomDatabase() {
    abstract fun nightDao(): NightDao
    abstract fun tagDao(): TagDao

    /** Legt die eingebauten Tags (Koffein, Sport …) beim Erstellen der Datenbank an. */
    object SeedBuiltInTags : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            Tag.BuiltInKeys.forEach { key ->
                db.execSQL("INSERT OR IGNORE INTO tags (`key`, label) VALUES (?, NULL)", arrayOf(key))
            }
        }
    }

    companion object {
        const val NAME = "zocks.db"
    }
}
