package com.hades.safepath.di

import com.google.gson.Gson
import com.hades.safepath.ai.remote.GeminiApi
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
object GeminiModule {

  private const val BASE_URL = "https://generativelanguage.googleapis.com/"
  private const val TIMEOUT_SECONDS = 30L

  @Provides
  @Singleton
  fun provideGeminiOkHttpClient(): OkHttpClient =
      OkHttpClient.Builder()
          .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
          .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
          .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
          .build()

  @Provides
  @Singleton
  fun provideGeminiRetrofit(client: OkHttpClient): Retrofit =
      Retrofit.Builder()
          .baseUrl(BASE_URL)
          .client(client)
          .addConverterFactory(GsonConverterFactory.create())
          .build()

  @Provides
  @Singleton
  fun provideGson(): Gson = Gson()

  @Provides
  @Singleton
  fun provideGeminiApi(retrofit: Retrofit): GeminiApi = retrofit.create(GeminiApi::class.java)
}
