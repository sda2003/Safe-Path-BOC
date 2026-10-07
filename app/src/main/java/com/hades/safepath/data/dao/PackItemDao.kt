package com.hades.safepath.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hades.safepath.data.entity.PackItem

@Dao
interface PackItemDao {

    @Query("SELECT * FROM pack_items")
    suspend fun getAllItems(): List<PackItem>

    @Query("SELECT * FROM pack_items WHERE disasterType = :disasterType ORDER BY priority ASC")
    suspend fun getItemsByDisasterType(disasterType: String): List<PackItem>

    @Query("DELETE FROM pack_items WHERE disasterType = :disasterType")
    suspend fun deleteByDisasterType(disasterType: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: PackItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PackItem>)

    @Delete
    suspend fun deleteItem(item: PackItem)
}
