package com.hades.safepath.network

import com.hades.safepath.network.dto.AlertUpdateDto
import com.hades.safepath.network.dto.ShelterStatusDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit service interface for the SafePath backend API.
 *
 * NOTE: The base URL (https://api.safepath.lk/) is a placeholder endpoint
 * that does not exist in the prototype phase. All calls must be wrapped in
 * try-catch and failures handled gracefully (R8 criterion 6).
 */
interface SafePathApi {

    /**
     * Fetches live shelter status for a given district.
     * Endpoint: GET v1/shelters/{district}/live
     */
    @GET("v1/shelters/{district}/live")
    suspend fun getLiveShelterStatus(
        @Path("district") district: String
    ): Response<List<ShelterStatusDto>>

    /**
     * Fetches the latest alerts for a given district.
     * Endpoint: GET v1/alerts/latest?district={district}
     */
    @GET("v1/alerts/latest")
    suspend fun getLatestAlerts(
        @Query("district") district: String
    ): Response<List<AlertUpdateDto>>
}
