package com.hades.safepath.data.repository

import com.hades.safepath.data.local.PackItemDao
import com.hades.safepath.data.model.PackItem
import com.hades.safepath.utils.Constants
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class PackRepository @Inject constructor(
    private val packDao: PackItemDao
) {
    fun getAllItems(): Flow<List<PackItem>> = packDao.getAllItems()

    suspend fun seedPackItems() {
        val existing = packDao.getCount()
        if (existing == 0) {
            packDao.insertAll(Constants.DEFAULT_PACK_ITEMS)
        }
    }

    suspend fun replaceAll(items: List<PackItem>) {
        packDao.clearAll()
        packDao.insertAll(items)
    }

    suspend fun updateItem(item: PackItem) {
        // REPLACE so a missed row never silently no-ops the checkbox.
        packDao.upsert(item)
    }
}
