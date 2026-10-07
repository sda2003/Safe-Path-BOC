package com.hades.safepath.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hades.safepath.data.entity.AlertLog

@Dao
interface AlertLogDao {

    @Query("SELECT * FROM alert_log ORDER BY receivedAt DESC")
    suspend fun getAllAlerts(): List<AlertLog>

    @Query("SELECT * FROM alert_log ORDER BY receivedAt DESC LIMIT :limit")
    suspend fun getRecentAlerts(limit: Int): List<AlertLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: AlertLog): Long

    @Delete
    suspend fun deleteAlert(alert: AlertLog)
}
