package com.hades.safepath.data.remote

import com.hades.safepath.BuildConfig

/**
 * Detects whether real Supabase credentials are present.
 * Placeholder values in local.properties (example.supabase.co / placeholder)
 * cause HTTP failures that look like "localhost" / unable to connect.
 */
object SupabaseConfig {
    fun isConfigured(): Boolean {
        val url = BuildConfig.SUPABASE_URL.trim()
        val key = BuildConfig.SUPABASE_ANON_KEY.trim()
        if (url.isEmpty() || key.isEmpty()) return false
        val badUrl = url.contains("example.supabase", ignoreCase = true) ||
            url.contains("your-project", ignoreCase = true) ||
            url.contains("localhost", ignoreCase = true) ||
            url.contains("127.0.0.1")
        val badKey = key.equals("placeholder", ignoreCase = true) ||
            key.contains("your_supabase", ignoreCase = true) ||
            key.contains("your-anon", ignoreCase = true) ||
            key.contains("your_real_anon", ignoreCase = true)
        return !badUrl && !badKey
    }

    fun notConfiguredMessage(): String =
        "Supabase is not configured. Add a real SUPABASE_URL and SUPABASE_ANON_KEY to local.properties, then rebuild. Alerts are saved on this device for now."
}
