package com.hades.safepath.di

import android.content.Context
import com.hades.safepath.auth.SessionSecureStorage
import com.hades.safepath.auth.SupabaseVerificationService
import com.hades.safepath.auth.VerificationService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VerificationModule {

    @Binds
    @Singleton
    abstract fun bindVerificationService(
        impl: SupabaseVerificationService
    ): VerificationService

    companion object {
        @Provides
        @Singleton
        fun provideSessionSecureStorage(
            @ApplicationContext context: Context
        ): SessionSecureStorage = SessionSecureStorage(context)
    }
}
