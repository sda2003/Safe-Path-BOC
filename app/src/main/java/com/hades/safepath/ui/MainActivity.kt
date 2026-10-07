package com.hades.safepath.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.hades.safepath.R
import com.hades.safepath.auth.PendingSignupStore
import com.hades.safepath.auth.SessionSecureStorage
import com.hades.safepath.databinding.ActivityMainBinding
import com.hades.safepath.ui.auth.UserProfile
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.handleDeeplinks
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var supabaseClient: SupabaseClient
    @Inject lateinit var sessionSecureStorage: SessionSecureStorage
    @Inject lateinit var pendingSignupStore: PendingSignupStore

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private val tabNavOptions: NavOptions by lazy {
        NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setRestoreState(true)
            .setPopUpTo(R.id.homeFragment, inclusive = false, saveState = true)
            .setEnterAnim(android.R.anim.fade_in)
            .setExitAnim(android.R.anim.fade_out)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val sharedPref = getSharedPreferences("safepath_prefs", MODE_PRIVATE)
        val isDark = sharedPref.getBoolean("dark_mode", true)
        val mode = if (isDark) {
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
        } else {
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        }
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handleAuthDeepLink(intent)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.navHome.setOnClickListener { navigateTab(R.id.homeFragment, 0) }
        binding.navPack.setOnClickListener { navigateTab(R.id.emergencyPackFragment, 1) }
        binding.navSurvival.setOnClickListener { navigateTab(R.id.survivalLibraryFragment, 2) }
        binding.navCommunity.setOnClickListener { navigateTab(R.id.communityFragment, 3) }

        navController.addOnDestinationChangedListener { _, dest, _ ->
            val hideOn = setOf(
                R.id.splashFragment,
                R.id.authFragment,
                R.id.imminentAlertFragment,
                R.id.checkEmailFragment,
                R.id.phoneVerificationFragment,
                R.id.otpVerificationFragment,
                R.id.verificationSuccessFragment
            )
            val visibility = if (dest.id in hideOn) View.GONE else View.VISIBLE
            binding.bottomNavContainer.visibility = visibility
            binding.fabReportAlert.visibility = visibility

            when (dest.id) {
                R.id.homeFragment -> updateNavSelection(0)
                R.id.emergencyPackFragment -> updateNavSelection(1)
                R.id.survivalLibraryFragment -> updateNavSelection(2)
                R.id.communityFragment -> updateNavSelection(3)
            }
        }

        binding.fabReportAlert.setOnClickListener {
            // Avoid stacking multiple sheets if tapped quickly
            if (supportFragmentManager.findFragmentByTag(TAG_REPORT) != null) return@setOnClickListener
            com.hades.safepath.ui.map.ReportAlertBottomSheet()
                .show(supportFragmentManager, TAG_REPORT)
        }
    }

    private fun navigateTab(destinationId: Int, selected: Int) {
        if (navController.currentDestination?.id == destinationId) {
            updateNavSelection(selected)
            return
        }
        runCatching {
            navController.navigate(destinationId, null, tabNavOptions)
        }
        updateNavSelection(selected)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthDeepLink(intent)
    }

    private fun handleAuthDeepLink(intent: Intent?) {
        if (intent == null) return
        runCatching {
            supabaseClient.handleDeeplinks(intent) { session ->
                Log.d(TAG, "Auth deep link session for ${session.user?.email}")
                lifecycleScope.launch {
                    sessionSecureStorage.saveSession(session)
                    val user = session.user ?: return@launch
                    val pending = pendingSignupStore.take()
                    val email = user.email.orEmpty().ifBlank { pending?.email.orEmpty() }
                    runCatching {
                        supabaseClient.postgrest["users"].upsert(
                            UserProfile(
                                id = user.id,
                                name = pending?.name ?: "SafePath User",
                                email = email,
                                district = pending?.district.orEmpty(),
                                verified = true,
                                tier = "free"
                            )
                        )
                    }
                    if (::navController.isInitialized) {
                        navController.navigate(R.id.action_global_homeFragment)
                    }
                }
            }
        }.onFailure { Log.w(TAG, "handleDeeplinks: ${it.message}") }
    }

    private fun updateNavSelection(selected: Int) {
        val icons = listOf(
            binding.iconHome,
            binding.iconPack,
            binding.iconSurvival,
            binding.iconCommunity
        )
        val labels = listOf(
            binding.labelHome,
            binding.labelPack,
            binding.labelSurvival,
            binding.labelCommunity
        )
        icons.forEachIndexed { index, view ->
            val active = index == selected
            val color = if (active) R.color.red_primary else R.color.text_secondary
            view.setColorFilter(getColor(color))
            labels[index].setTextColor(getColor(color))
        }
    }

    private companion object {
        const val TAG = "MainActivity"
        const val TAG_REPORT = "ReportAlertBottomSheet"
    }
}
