package com.hades.safepath.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hades.safepath.data.entity.HazardEvent

@Dao
interface HazardEventDao {
    @Query("SELECT * FROM hazard_events")
    suspend fun getAllHazards(): List<HazardEvent>

    @Query("SELECT * FROM hazard_events WHERE expirationTime > :currentTime")
    suspend fun getActiveHazards(currentTime: Long): List<HazardEvent>

    @Query("SELECT * FROM hazard_events WHERE expirationTime > :currentTime")
    fun observeActiveHazards(currentTime: Long): kotlinx.coroutines.flow.Flow<List<HazardEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHazard(hazard: HazardEvent)

    @Query("DELETE FROM hazard_events WHERE id = :id")
    suspend fun deleteHazard(id: String)
    
    @Query("DELETE FROM hazard_events WHERE expirationTime <= :currentTime")
    suspend fun deleteExpiredHazards(currentTime: Long)
}
