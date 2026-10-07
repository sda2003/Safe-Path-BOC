package com.hades.safepath.network

import android.util.Log
import com.hades.safepath.data.repository.ShelterRepository
import com.hades.safepath.network.dto.ShelterStatusDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles remote data fetching and local cache updates.
 *
 * All API calls are wrapped in try-catch — this class never throws to the caller.
 * On failure, [Result.failure] is returned and the app continues with local data.
 *
 * The backend API (https://api.safepath.lk/) is a placeholder in the prototype
 * phase. Failures are expected and handled gracefully (R8 criterion 6).
 *
 * @param api         Retrofit [SafePathApi] instance.
 * @param shelterRepo Local [ShelterRepository] for cache updates.
 */
class NetworkRepository(
    private val api: SafePathApi,
    private val shelterRepo: ShelterRepository
) {

    private val TAG = "NetworkRepository"

    /**
     * Fetches live shelter status for [district] from the remote API and
     * updates [ShelterRepository] with the latest capacity values.
     *
     * @return [Result.success] with the DTO list on success.
     *         [Result.failure] if the network is unavailable or the API call fails.
     */
    suspend fun fetchAndCacheLiveShelters(district: String): Result<List<ShelterStatusDto>> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getLiveShelterStatus(district)

                if (!response.isSuccessful) {
                    Log.w(TAG, "getLiveShelterStatus failed: HTTP ${response.code()} for district=$district")
                    return@withContext Result.failure(
                        Exception("API error ${response.code()} for district=$district")
                    )
                }

                val dtos = response.body() ?: run {
                    Log.w(TAG, "getLiveShelterStatus returned empty body for district=$district")
                    return@withContext Result.failure(Exception("Empty response body"))
                }

                // Update local Room cache with latest capacity values
                updateShelterCache(dtos)

                Log.d(TAG, "Fetched and cached ${dtos.size} shelter statuses for $district")
                Result.success(dtos)

            } catch (e: Exception) {
                // Network unavailable, timeout, or placeholder URL — expected in prototype
                Log.w(TAG, "fetchAndCacheLiveShelters failed (expected for placeholder URL): ${e.message}")
                Result.failure(e)
            }
        }

    /**
     * Fetches the latest alerts for [district] from the remote API.
     *
     * @return [Result.success] with the DTO list on success.
     *         [Result.failure] if the network is unavailable or the API call fails.
     */
    suspend fun fetchLatestAlerts(district: String): Result<List<com.hades.safepath.network.dto.AlertUpdateDto>> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getLatestAlerts(district)

                if (!response.isSuccessful) {
                    Log.w(TAG, "getLatestAlerts failed: HTTP ${response.code()} for district=$district")
                    return@withContext Result.failure(
                        Exception("API error ${response.code()} for district=$district")
                    )
                }

                val dtos = response.body() ?: run {
                    Log.w(TAG, "getLatestAlerts returned empty body for district=$district")
                    return@withContext Result.failure(Exception("Empty response body"))
                }

                Log.d(TAG, "Fetched ${dtos.size} alert updates for $district")
                Result.success(dtos)

            } catch (e: Exception) {
                Log.w(TAG, "fetchLatestAlerts failed (expected for placeholder URL): ${e.message}")
                Result.failure(e)
            }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Cache helpers
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun updateShelterCache(dtos: List<ShelterStatusDto>) {
        val allShelters = shelterRepo.getAllShelters()
        for (dto in dtos) {
            val match = allShelters.firstOrNull {
                it.name.equals(dto.shelterName, ignoreCase = true)
            } ?: continue

            val updated = match.copy(capacityPersons = dto.availableCapacity)
            shelterRepo.insertShelter(updated)  // REPLACE strategy updates in-place
            Log.d(TAG, "Updated shelter '${dto.shelterName}' capacity → ${dto.availableCapacity}")
        }
    }
}
