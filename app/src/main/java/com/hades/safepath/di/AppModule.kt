package com.hades.safepath.di

import android.content.Context
import androidx.room.Room
import com.hades.safepath.data.local.AlertDao
import com.hades.safepath.data.local.AppDatabase
import com.hades.safepath.data.local.PackItemDao
import com.hades.safepath.data.SafePathDatabase
import com.hades.safepath.data.dao.ShelterDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "safepath_cache_db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideAlertDao(database: AppDatabase): AlertDao {
        return database.alertDao()
    }

    @Provides
    fun providePackItemDao(database: AppDatabase): PackItemDao {
        return database.packItemDao()
    }

    @Provides
    @Singleton
    fun provideSafePathDatabase(@ApplicationContext context: Context): SafePathDatabase {
        return SafePathDatabase.getInstance(context)
    }

    @Provides
    fun provideShelterDao(database: SafePathDatabase): ShelterDao {
        return database.shelterDao()
    }

    @Provides
    fun provideUserProfileDao(database: SafePathDatabase): com.hades.safepath.data.dao.UserProfileDao {
        return database.userProfileDao()
    }

    @Provides
    fun provideFamilyContactDao(database: SafePathDatabase): com.hades.safepath.data.dao.FamilyContactDao {
        return database.familyContactDao()
    }

    @Provides
    fun providePendingSafeBroadcastDao(database: SafePathDatabase): com.hades.safepath.data.dao.PendingSafeBroadcastDao {
        return database.pendingSafeBroadcastDao()
    }

    @Provides
    fun providePersonalizedPackMetaDao(database: SafePathDatabase): com.hades.safepath.data.dao.PersonalizedPackMetaDao {
        return database.personalizedPackMetaDao()
    }

    @Provides
    fun provideMeshNoteDao(database: SafePathDatabase): com.hades.safepath.data.dao.MeshNoteDao {
        return database.meshNoteDao()
    }

    @Provides
    fun provideAiQaCacheDao(database: SafePathDatabase): com.hades.safepath.data.dao.AiQaCacheDao {
        return database.aiQaCacheDao()
    }

    @Provides
    fun provideSafePathPackItemDao(database: SafePathDatabase): com.hades.safepath.data.dao.PackItemDao {
        return database.packItemDao()
    }

    @Provides
    fun provideSafePathGuidanceStepDao(database: SafePathDatabase): com.hades.safepath.data.dao.GuidanceStepDao {
        return database.guidanceStepDao()
    }

    @Provides
    @Singleton
    fun provideRoutingEngine(
        @ApplicationContext context: Context,
        database: SafePathDatabase
    ): com.hades.safepath.routing.RoutingEngine {
        return com.hades.safepath.routing.RoutingEngine(context, database)
    }

    @Provides
    fun provideHazardEventDao(database: SafePathDatabase): com.hades.safepath.data.dao.HazardEventDao {
        return database.hazardEventDao()
    }

    @Provides
    fun provideHazardNodeDao(database: SafePathDatabase): com.hades.safepath.data.dao.HazardNodeDao {
        return database.hazardNodeDao()
    }

    @Provides
    @Singleton
    fun provideHazardNodeRepository(dao: com.hades.safepath.data.dao.HazardNodeDao): com.hades.safepath.data.repository.HazardNodeRepository {
        return com.hades.safepath.data.repository.LocalHazardNodeRepository(dao)
    }
}
