package com.hades.safepath.di

import com.hades.safepath.BuildConfig
import com.hades.safepath.auth.AuthDeepLink
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import javax.inject.Singleton

import io.github.jan.supabase.realtime.Realtime

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient {
        return createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth) {
                scheme = AuthDeepLink.SCHEME
                host = AuthDeepLink.HOST
            }
            install(Postgrest)
            install(Realtime)
            install(io.github.jan.supabase.storage.Storage)
        }
    }
}
