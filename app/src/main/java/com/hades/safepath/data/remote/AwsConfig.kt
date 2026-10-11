package com.hades.safepath.data.remote

import com.hades.safepath.BuildConfig

object AwsConfig {
    fun isConfigured(): Boolean = BuildConfig.SAFEPATH_API_URL.startsWith("https://")
}
