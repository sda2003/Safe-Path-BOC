package com.hades.safepath.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hades.safepath.data.entity.PendingSafeBroadcastEntity

@Dao
interface PendingSafeBroadcastDao {

    @Insert
    suspend fun insert(broadcast: PendingSafeBroadcastEntity): Long

    @Query("SELECT * FROM pending_safe_broadcasts ORDER BY createdAt ASC")
    suspend fun getAllPending(): List<PendingSafeBroadcastEntity>

    @Query("DELETE FROM pending_safe_broadcasts WHERE localId = :localId")
    suspend fun delete(localId: Long)

    @Query("UPDATE pending_safe_broadcasts SET attemptCount = attemptCount + 1 WHERE localId = :localId")
    suspend fun incrementAttempt(localId: Long)

    @Query("SELECT COUNT(*) FROM pending_safe_broadcasts")
    suspend fun countPending(): Int
}
