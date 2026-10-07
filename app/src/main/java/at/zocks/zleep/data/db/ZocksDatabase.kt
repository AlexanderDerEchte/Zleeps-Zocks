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
        NightSummaryEntity::class,
    ],
    version = ZocksDatabase.VERSION,
    exportSchema = true,
)
abstract class ZocksDatabase : RoomDatabase() {
    abstract fun nightDao(): NightDao
    abstract fun tagDao(): TagDao
    abstract fun massageProgramDao(): MassageProgramDao
    abstract fun nightSummaryDao(): NightSummaryDao

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
        const val VERSION = 4

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

        /**
         * Version 4: zwischengespeicherte Kennzahlen je Nacht. Die Tabelle startet leer und wird
         * beim nächsten App-Start aus den vorhandenen Nächten befüllt (`NightSummaryUpdater`).
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `night_summaries` (`night_id` INTEGER NOT NULL, " +
                        "`night_date` INTEGER NOT NULL, `start_ms` INTEGER NOT NULL, `end_ms` INTEGER, " +
                        "`sleep_onset_ms` INTEGER, `final_wake_ms` INTEGER, `time_in_bed_s` INTEGER NOT NULL, " +
                        "`total_sleep_s` INTEGER, `sleep_latency_s` INTEGER, `efficiency` REAL, " +
                        "`wake_after_onset_s` INTEGER, `awakenings` INTEGER, `awake_minutes` INTEGER NOT NULL, " +
                        "`light_minutes` INTEGER NOT NULL, `deep_minutes` INTEGER NOT NULL, " +
                        "`rem_minutes` INTEGER NOT NULL, `resting_heart_rate` REAL, `avg_heart_rate` REAL, " +
                        "`avg_hrv_rmssd` REAL, `avg_spo2` REAL, `avg_skin_temperature` REAL, " +
                        "`heat_used` INTEGER NOT NULL, `massage_used` INTEGER NOT NULL, `gap_s` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`night_id`), FOREIGN KEY(`night_id`) REFERENCES `nights`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_night_summaries_night_date` ON `night_summaries` (`night_date`)",
                )
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    }
}
