package com.hades.safepath.di

import android.content.Context
import com.hades.safepath.location.SafePathLocationManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocationModule {

    @Provides
    @Singleton
    fun provideSafePathLocationManager(
        @ApplicationContext context: Context
    ): SafePathLocationManager = SafePathLocationManager(context)
}
