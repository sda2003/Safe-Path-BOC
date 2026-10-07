package com.hades.safepath.auth

/**
 * Deep link used for Supabase Auth magic-link / email confirmation callbacks.
 * Add this exact URL in Supabase → Authentication → URL Configuration → Redirect URLs:
 *   com.hades.safepath://login-callback
 */
object AuthDeepLink {
    const val SCHEME = "com.hades.safepath"
    const val HOST = "login-callback"
    const val REDIRECT_URL = "$SCHEME://$HOST"
}
