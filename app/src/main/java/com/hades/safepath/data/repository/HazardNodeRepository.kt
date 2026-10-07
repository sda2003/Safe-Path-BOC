package com.hades.safepath.data.repository

import com.hades.safepath.data.dao.HazardNodeDao
import com.hades.safepath.data.entity.HazardNodeEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface HazardNodeRepository {
    fun observeAllNodes(): Flow<List<HazardNodeEntity>>
    suspend fun getAllNodes(): List<HazardNodeEntity>
    suspend fun getNodeById(nodeId: String): HazardNodeEntity?
    suspend fun insertOrUpdateNode(node: HazardNodeEntity)
}

@Singleton
class LocalHazardNodeRepository @Inject constructor(
    private val hazardNodeDao: HazardNodeDao
) : HazardNodeRepository {
    override fun observeAllNodes() = hazardNodeDao.observeAllNodes()
    override suspend fun getAllNodes() = hazardNodeDao.getAllNodes()
    override suspend fun getNodeById(nodeId: String) = hazardNodeDao.getNodeById(nodeId)
    override suspend fun insertOrUpdateNode(node: HazardNodeEntity) = hazardNodeDao.insertNode(node)
}
