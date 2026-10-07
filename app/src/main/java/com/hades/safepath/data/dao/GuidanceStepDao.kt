package com.hades.safepath.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hades.safepath.data.entity.GuidanceStep

@Dao
interface GuidanceStepDao {

    @Query("SELECT COUNT(*) FROM guidance_steps")
    suspend fun getCount(): Int

    @Query("SELECT * FROM guidance_steps")
    suspend fun getAllSteps(): List<GuidanceStep>

    @Query("""
        SELECT * FROM guidance_steps
        WHERE disasterType = :disasterType AND severityLevel = :severityLevel
        ORDER BY stepOrder ASC
    """)
    suspend fun getSteps(disasterType: String, severityLevel: String): List<GuidanceStep>

    @Query("DELETE FROM guidance_steps WHERE disasterType = :disasterType AND severityLevel = :severityLevel")
    suspend fun deleteByTypeAndSeverity(disasterType: String, severityLevel: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStep(step: GuidanceStep)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(steps: List<GuidanceStep>)

    @Delete
    suspend fun deleteStep(step: GuidanceStep)
}
