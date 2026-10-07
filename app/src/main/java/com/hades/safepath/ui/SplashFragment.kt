package com.hades.safepath.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.hades.safepath.R
import com.hades.safepath.auth.VerificationRepository
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SplashFragment : Fragment() {

    @Inject
    lateinit var supabaseClient: SupabaseClient

    @Inject
    lateinit var verificationRepository: VerificationRepository

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_splash, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        lifecycleScope.launch {
            delay(2000)
            verificationRepository.restoreSessionIfNeeded()
            // Guard: only navigate if we're still on the splash screen
            if (!isAdded) return@launch
            try {
                val navController = findNavController()
                if (navController.currentDestination?.id != R.id.splashFragment) return@launch
                
                val session = try { supabaseClient.auth.currentSessionOrNull() } catch (_: Exception) { null }
                val action = if (session != null) R.id.action_global_homeFragment else R.id.action_global_authFragment
                navController.navigate(action)
            } catch (_: Exception) {
                // Fragment was detached or NavController unavailable — ignore
            }
        }
    }
}
