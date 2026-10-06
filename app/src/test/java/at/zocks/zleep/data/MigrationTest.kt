package at.zocks.zleep.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.zocks.zleep.data.db.ZocksDatabase
import at.zocks.zleep.domain.model.NightSource
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Prüft, dass vorhandene Daten jede Schema-Migration überstehen. Die alte Datenbank wird
 * exakt aus dem versionierten Schema (`app/schemas/…/N.json`) erzeugt; beim Öffnen prüft
 * Room nach der Migration, dass das Ergebnis genau dem aktuellen Schema entspricht.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `migrate 1 to 2 keeps nights and adds massage programs`() = runTest {
        createDatabaseFromSchema(version = 1).use { db ->
            db.execSQL(
                "INSERT INTO nights (id, start_ms, end_ms, sleep_onset_ms, final_wake_ms, source, note) " +
                    "VALUES (1, 1000, 2000, 1100, 1900, 'DEVICE', 'alt')",
            )
            db.execSQL("INSERT INTO tags (id, `key`, label) VALUES (1, 'sport', NULL)")
            db.execSQL("INSERT INTO night_tags (night_id, tag_id) VALUES (1, 1)")
        }

        val database = Room.databaseBuilder(context, ZocksDatabase::class.java, DB)
            .addMigrations(*ZocksDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val night = database.nightDao().getNight(1)!!
            assertThat(night.night.note).isEqualTo("alt")
            assertThat(night.night.source).isEqualTo(NightSource.DEVICE)
            assertThat(night.tags.single().key).isEqualTo("sport")
            assertThat(database.massageProgramDao().observeAll().first()).isEmpty()
            assertThat(database.openHelper.readableDatabase.version).isEqualTo(2)
        } finally {
            database.close()
            context.deleteDatabase(DB)
        }
    }

    /** Legt eine leere Datenbank genau nach dem exportierten Schema der Version [version] an. */
    private fun createDatabaseFromSchema(version: Int): SupportSQLiteDatabase {
        val schema = Json.parseToJsonElement(File(SCHEMA_DIR, "$version.json").readText()).jsonObject.getValue("database").jsonObject
        val statements = schema.getValue("entities").jsonArray.flatMap { entity ->
            val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
            val create = entity.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)
            val indices = entity.jsonObject["indices"]?.jsonArray.orEmpty().map {
                it.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)
            }
            listOf(create) + indices
        } + schema.getValue("setupQueries").jsonArray.map { it.jsonPrimitive.content }

        context.deleteDatabase(DB)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) = statements.forEach(db::execSQL)
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        return helper.writableDatabase
    }

    private companion object {
        const val DB = "migration-test.db"
        val SCHEMA_DIR = File("schemas/at.zocks.zleep.data.db.ZocksDatabase")
    }
}
