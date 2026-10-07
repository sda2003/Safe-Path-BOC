package com.hades.safepath.ai.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface GeminiApi {

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Header("X-goog-api-key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}
