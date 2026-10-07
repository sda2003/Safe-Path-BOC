package com.hades.safepath.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton Retrofit client for the SafePath backend API.
 *
 * The base URL is a placeholder — the backend does not exist in the prototype
 * phase. [NetworkRepository] handles all connection failures gracefully and
 * falls back to local Room data (R8 criterion 6).
 */
@Suppress("SpellCheckingInspection")
object RetrofitClient {

    private const val BASE_URL = "https://api.safepath.lk/"
    private const val CONNECT_TIMEOUT_SECONDS = 5L
    private const val READ_TIMEOUT_SECONDS = 10L

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /** Returns the [SafePathApi] Retrofit service instance. */
    fun getApi(): SafePathApi = retrofit.create(SafePathApi::class.java)
}
