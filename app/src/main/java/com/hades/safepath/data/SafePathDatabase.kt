package com.hades.safepath.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hades.safepath.data.dao.AlertLogDao
import com.hades.safepath.data.dao.GuidanceStepDao
import com.hades.safepath.data.dao.PackItemDao
import com.hades.safepath.data.dao.ShelterDao
import com.hades.safepath.data.entity.AlertLog
import com.hades.safepath.data.entity.GuidanceStep
import com.hades.safepath.data.entity.PackItem
import com.hades.safepath.data.entity.Shelter
import com.hades.safepath.data.seeding.DatabaseSeeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Room database for SafePath.
 *
 * [DatabaseSeeder.ensureSeeded] runs on every [onOpen] so shelters are never
 * missing after upgrades / destructive migrations (fixes empty-shelter routing).
 */
@Database(
    entities = [
        Shelter::class,
        GuidanceStep::class,
        PackItem::class,
        AlertLog::class,
        com.hades.safepath.data.entity.UserProfileEntity::class,
        com.hades.safepath.data.entity.FamilyContactEntity::class,
        com.hades.safepath.data.entity.PendingSafeBroadcastEntity::class,
        com.hades.safepath.data.entity.PersonalizedPackMetaEntity::class,
        com.hades.safepath.data.entity.MeshNoteEntity::class,
        com.hades.safepath.data.entity.AiQaCacheEntity::class,
        com.hades.safepath.data.entity.HazardEvent::class,
        com.hades.safepath.data.entity.HazardNodeEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class SafePathDatabase : RoomDatabase() {

    abstract fun shelterDao(): ShelterDao
    abstract fun guidanceStepDao(): GuidanceStepDao
    abstract fun packItemDao(): PackItemDao
    abstract fun alertLogDao(): AlertLogDao
    abstract fun userProfileDao(): com.hades.safepath.data.dao.UserProfileDao
    abstract fun familyContactDao(): com.hades.safepath.data.dao.FamilyContactDao
    abstract fun pendingSafeBroadcastDao(): com.hades.safepath.data.dao.PendingSafeBroadcastDao
    abstract fun personalizedPackMetaDao(): com.hades.safepath.data.dao.PersonalizedPackMetaDao
    abstract fun meshNoteDao(): com.hades.safepath.data.dao.MeshNoteDao
    abstract fun aiQaCacheDao(): com.hades.safepath.data.dao.AiQaCacheDao
    abstract fun hazardEventDao(): com.hades.safepath.data.dao.HazardEventDao
    abstract fun hazardNodeDao(): com.hades.safepath.data.dao.HazardNodeDao

    companion object {
        @Volatile
        private var INSTANCE: SafePathDatabase? = null

        fun getInstance(context: Context): SafePathDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = buildDatabase(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        private fun buildDatabase(context: Context): SafePathDatabase {
            // Capture Room instance for callbacks (INSTANCE may still be null during onCreate).
            lateinit var database: SafePathDatabase
            val seedCallback = object : Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    CoroutineScope(Dispatchers.IO).launch {
                        runCatching { DatabaseSeeder.ensureSeeded(database) }
                            .onFailure { android.util.Log.e("SafePathDatabase", "Seed failed", it) }
                    }
                }
            }

            database = Room.databaseBuilder(
                context,
                SafePathDatabase::class.java,
                "safepath_db"
            )
                .fallbackToDestructiveMigration()
                .addCallback(seedCallback)
                .build()
            return database
        }
    }
}
