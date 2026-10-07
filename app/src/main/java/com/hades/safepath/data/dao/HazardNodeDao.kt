package com.hades.safepath.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hades.safepath.data.entity.HazardNodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HazardNodeDao {
    @Query("SELECT * FROM hazard_nodes")
    fun observeAllNodes(): Flow<List<HazardNodeEntity>>

    @Query("SELECT * FROM hazard_nodes")
    suspend fun getAllNodes(): List<HazardNodeEntity>

    @Query("SELECT * FROM hazard_nodes WHERE nodeId = :nodeId")
    suspend fun getNodeById(nodeId: String): HazardNodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: HazardNodeEntity)

    @Query("DELETE FROM hazard_nodes WHERE nodeId = :nodeId")
    suspend fun deleteNode(nodeId: String)
}
