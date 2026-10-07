package com.hades.safepath.data.repository

import com.hades.safepath.data.dao.HazardEventDao
import com.hades.safepath.data.entity.HazardEvent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HazardRepository @Inject constructor(
    private val hazardEventDao: HazardEventDao
) {
    suspend fun getActiveHazards(currentTime: Long): List<HazardEvent> {
        return hazardEventDao.getActiveHazards(currentTime)
    }

    fun observeActiveHazards(currentTime: Long): kotlinx.coroutines.flow.Flow<List<HazardEvent>> {
        return hazardEventDao.observeActiveHazards(currentTime)
    }

    suspend fun insertHazard(hazard: HazardEvent) {
        hazardEventDao.insertHazard(hazard)
    }

    suspend fun clearExpiredHazards(currentTime: Long) {
        hazardEventDao.deleteExpiredHazards(currentTime)
    }
}
