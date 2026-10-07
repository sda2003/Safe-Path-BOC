package com.hades.safepath.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hades.safepath.data.entity.PersonalizedPackMetaEntity

@Dao
interface PersonalizedPackMetaDao {

    @Query("SELECT * FROM personalized_pack_meta WHERE id = 1 LIMIT 1")
    suspend fun get(): PersonalizedPackMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: PersonalizedPackMetaEntity)
}
