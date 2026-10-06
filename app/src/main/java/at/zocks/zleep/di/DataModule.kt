package at.zocks.zleep.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import at.zocks.zleep.data.db.MassageProgramDao
import at.zocks.zleep.data.db.NightDao
import at.zocks.zleep.data.db.TagDao
import at.zocks.zleep.data.db.ZocksDatabase
import at.zocks.zleep.data.repository.RoomMassageProgramRepository
import at.zocks.zleep.data.repository.RoomNightRepository
import at.zocks.zleep.data.schedule.AlarmPreheatScheduler
import at.zocks.zleep.data.repository.RoomTagRepository
import at.zocks.zleep.data.settings.DataStoreSettingsRepository
import at.zocks.zleep.domain.heat.PreheatScheduler
import at.zocks.zleep.domain.repository.MassageProgramRepository
import at.zocks.zleep.domain.repository.NightRepository
import at.zocks.zleep.domain.repository.SettingsRepository
import at.zocks.zleep.domain.repository.TagRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): ZocksDatabase =
        Room.databaseBuilder(context, ZocksDatabase::class.java, ZocksDatabase.NAME)
            .addCallback(ZocksDatabase.SeedBuiltInTags)
            .addMigrations(*ZocksDatabase.ALL_MIGRATIONS)
            .build()

    @Provides
    fun nightDao(database: ZocksDatabase): NightDao = database.nightDao()

    @Provides
    fun tagDao(database: ZocksDatabase): TagDao = database.tagDao()

    @Provides
    fun massageProgramDao(database: ZocksDatabase): MassageProgramDao = database.massageProgramDao()

    @Provides
    @Singleton
    fun preferences(
        @ApplicationContext context: Context,
        @IoDispatcher io: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(SupervisorJob() + io),
        produceFile = { context.preferencesDataStoreFile("settings") },
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun nightRepository(impl: RoomNightRepository): NightRepository

    @Binds
    abstract fun tagRepository(impl: RoomTagRepository): TagRepository

    @Binds
    abstract fun settingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    abstract fun massageProgramRepository(impl: RoomMassageProgramRepository): MassageProgramRepository

    @Binds
    abstract fun preheatScheduler(impl: AlarmPreheatScheduler): PreheatScheduler
}
