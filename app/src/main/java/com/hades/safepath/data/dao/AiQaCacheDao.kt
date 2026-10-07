package com.hades.safepath.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hades.safepath.data.entity.AiQaCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiQaCacheDao {

    @Query("SELECT * FROM ai_qa_cache ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<AiQaCacheEntity>>

    @Query("SELECT * FROM ai_qa_cache WHERE questionKey = :key LIMIT 1")
    suspend fun getByKey(key: String): AiQaCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AiQaCacheEntity): Long

    @Query("DELETE FROM ai_qa_cache WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long)
}
