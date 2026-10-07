package com.hades.safepath.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hades.safepath.data.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {

    @Query("SELECT * FROM user_profiles WHERE id = :userId LIMIT 1")
    suspend fun getById(userId: String): UserProfileEntity?

    @Query("SELECT * FROM user_profiles WHERE id = :userId LIMIT 1")
    fun observeById(userId: String): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles LIMIT 1")
    suspend fun getCurrent(): UserProfileEntity?

    @Query("SELECT * FROM user_profiles LIMIT 1")
    fun observeCurrent(): Flow<UserProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfileEntity)
}
