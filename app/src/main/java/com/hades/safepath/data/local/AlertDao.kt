package com.hades.safepath.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hades.safepath.data.model.Alert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts ORDER BY createdAt DESC LIMIT 1")
    fun getCurrentAlert(): Flow<Alert?>

    @Query("SELECT * FROM alerts ORDER BY createdAt DESC LIMIT 1")
    fun getLastAlert(): Flow<Alert?>

    @Query("SELECT * FROM alerts WHERE isActive = 1 ORDER BY createdAt DESC LIMIT 50")
    suspend fun getActiveAlertsList(): List<Alert>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: Alert)
}
