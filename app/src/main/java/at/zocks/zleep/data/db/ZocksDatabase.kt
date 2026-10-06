package at.zocks.zleep.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
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
        MassageProgramEntity::class,
    ],
    version = ZocksDatabase.VERSION,
    exportSchema = true,
)
abstract class ZocksDatabase : RoomDatabase() {
    abstract fun nightDao(): NightDao
    abstract fun tagDao(): TagDao
    abstract fun massageProgramDao(): MassageProgramDao

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
        const val VERSION = 3

        /** Version 2: Tabelle für eigene Massageprogramme. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `massage_programs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, `intensity` INTEGER NOT NULL, `duration_minutes` INTEGER NOT NULL, " +
                        "`tempo` TEXT NOT NULL, `zones` TEXT NOT NULL)",
                )
            }
        }

        /** Version 3: Merker, ob das Schlaffenster von Hand korrigiert wurde. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `nights` ADD COLUMN `sleep_window_manual` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
    }
}
