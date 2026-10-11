package com.hades.safepath.di

import com.hades.safepath.BuildConfig
import com.hades.safepath.network.SafePathApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SafePathApiModule {
    @Provides
    @Singleton
    fun provideSafePathApi(): SafePathApi {
        val base = BuildConfig.SAFEPATH_API_URL.ifBlank { "https://api.safepath.lk/" }
            .let { if (it.endsWith("/")) it else "$it/" }
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SafePathApi::class.java)
    }
}
