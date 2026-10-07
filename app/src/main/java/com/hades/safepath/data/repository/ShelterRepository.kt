package com.hades.safepath.data.repository

import com.hades.safepath.data.dao.ShelterDao
import com.hades.safepath.data.entity.Shelter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ShelterRepository @Inject constructor(private val shelterDao: ShelterDao) {

    suspend fun getAllShelters(): List<Shelter> = withContext(Dispatchers.IO) {
        shelterDao.getAllShelters()
    }

    suspend fun getAllActiveShelters(): List<Shelter> = withContext(Dispatchers.IO) {
        shelterDao.getAllActiveShelters()
    }

    suspend fun getSheltersByDistrict(district: String): List<Shelter> = withContext(Dispatchers.IO) {
        shelterDao.getSheltersByDistrict(district)
    }

    suspend fun getShelterById(id: Long): Shelter? = withContext(Dispatchers.IO) {
        shelterDao.getShelterById(id)
    }

    suspend fun insertShelter(shelter: Shelter) = withContext(Dispatchers.IO) {
        shelterDao.insertShelter(shelter)
    }

    suspend fun insertAll(shelters: List<Shelter>) = withContext(Dispatchers.IO) {
        shelterDao.insertAll(shelters)
    }
}
