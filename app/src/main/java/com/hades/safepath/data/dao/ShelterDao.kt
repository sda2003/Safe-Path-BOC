package com.hades.safepath.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hades.safepath.data.entity.Shelter

@Dao
interface ShelterDao {

    @Query("SELECT * FROM shelters")
    suspend fun getAllShelters(): List<Shelter>

    @Query("SELECT * FROM shelters WHERE isVerified = 1")
    suspend fun getAllActiveShelters(): List<Shelter>

    @Query("SELECT * FROM shelters WHERE district = :district")
    suspend fun getSheltersByDistrict(district: String): List<Shelter>

    @Query("SELECT * FROM shelters WHERE id = :id")
    suspend fun getShelterById(id: Long): Shelter?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShelter(shelter: Shelter)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(shelters: List<Shelter>)

    @Delete
    suspend fun deleteShelter(shelter: Shelter)
}
