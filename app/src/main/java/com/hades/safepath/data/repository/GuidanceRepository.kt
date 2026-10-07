package com.hades.safepath.data.repository

import com.hades.safepath.data.dao.GuidanceStepDao
import com.hades.safepath.data.dao.PackItemDao
import com.hades.safepath.data.entity.GuidanceStep
import com.hades.safepath.data.entity.PackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GuidanceRepository(
    private val guidanceStepDao: GuidanceStepDao,
    private val packItemDao: PackItemDao
) {

    suspend fun getSteps(disasterType: String, severityLevel: String): List<GuidanceStep> =
        withContext(Dispatchers.IO) {
            guidanceStepDao.getSteps(disasterType, severityLevel)
        }

    /**
     * Alias used by DecisionEngine — returns steps ordered by stepOrder ascending.
     * Delegates to [getSteps].
     */
    suspend fun getStepsForEvent(disasterType: String, severity: String): List<GuidanceStep> =
        getSteps(disasterType, severity)

    suspend fun getAllSteps(): List<GuidanceStep> = withContext(Dispatchers.IO) {
        guidanceStepDao.getAllSteps()
    }

    suspend fun insertSteps(steps: List<GuidanceStep>) = withContext(Dispatchers.IO) {
        guidanceStepDao.insertAll(steps)
    }

    suspend fun getPackItems(disasterType: String): List<PackItem> = withContext(Dispatchers.IO) {
        packItemDao.getItemsByDisasterType(disasterType)
    }

    suspend fun insertPackItems(items: List<PackItem>) = withContext(Dispatchers.IO) {
        packItemDao.insertAll(items)
    }
}
