package com.hades.safepath.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.hades.safepath.data.model.PackItem
import kotlinx.coroutines.flow.Flow

@Dao
interface PackItemDao {
    @Query("SELECT * FROM pack_items")
    fun getAllItems(): Flow<List<PackItem>>

    @Query("SELECT COUNT(*) FROM pack_items")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PackItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PackItem)

    @Update
    suspend fun updateItem(item: PackItem)

    @Query("DELETE FROM pack_items")
    suspend fun clearAll()
}
