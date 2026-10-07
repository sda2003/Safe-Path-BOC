package com.hades.safepath.ui.home

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hades.safepath.R
import com.hades.safepath.data.model.Alert
import com.hades.safepath.databinding.FragmentHomeBinding
import com.hades.safepath.family.ImSafeResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private var pulseAnimator: ObjectAnimator? = null
    private var mapReady = false
    private var pendingMapColor: String? = null
    private var pendingLat: Double? = null
    private var pendingLng: Double? = null
    private var pendingSheltersJson: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startPulseAnimation()
        binding.wvSriLankaMap.post { setupSilhouetteWebView() }

        viewModel.shelters.observe(viewLifecycleOwner) { shelters ->
            pendingSheltersJson = sheltersToJson(shelters)
            if (mapReady) pushSafePlaces(pendingSheltersJson!!)
        }

        viewModel.currentAlert.observe(viewLifecycleOwner) { alert ->
            if (alert != null && alert.severity == "IMMINENT") {
                if (findNavController().currentDestination?.id == R.id.homeFragment) {
                    findNavController().navigate(
                        R.id.action_home_to_imminentAlert,
                        bundleOf("alertId" to alert.id)
                    )
                }
            }
            updateAlertCard(alert)
        }

        viewModel.lastAlert.observe(viewLifecycleOwner) { alert ->
            if (alert != null) {
                binding.cardLastAlert.visibility = View.VISIBLE
                binding.tvLastAlertTitle.text = "${alert.type} Warning"
            } else {
                binding.cardLastAlert.visibility = View.GONE
            }
        }

        binding.btnViewMap.setOnClickListener {
            findNavController().navigate(R.id.evacuationMapFragment)
        }

        binding.cardAiQa.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_aiQa)
        }

        binding.cardNearbyNodes.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_nearbyNodes)
        }

        binding.btnFamilyCircleAction.setOnClickListener {
            lifecycleScope.launch {
                val serverVerified = viewModel.isVerifiedOnServer()
                when {
                    serverVerified -> findNavController().navigate(R.id.action_home_to_family_circle)
                    viewModel.isFamilyCircleVerified.value == true -> {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.family_circle_offline_cached),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    else -> findNavController().navigate(R.id.action_home_to_phone_verification)
                }
            }
        }

        binding.btnImSafe.setOnClickListener {
            viewModel.sendImSafe { result ->
                val message = when (result) {
                    is ImSafeResult.Sent -> getString(R.string.im_safe_sent)
                    is ImSafeResult.Queued -> getString(R.string.im_safe_queued)
                    is ImSafeResult.Error -> result.message
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.isFamilyCircleVerified.observe(viewLifecycleOwner) { cachedVerified ->
            lifecycleScope.launch {
                val serverVerified = viewModel.isVerifiedOnServer()
                updateFamilyCircleCard(serverVerified, cachedVerified)
            }
        }

        binding.btnMenu.setOnClickListener { anchor ->
            val popup = PopupMenu(requireContext(), anchor)
            popup.inflate(R.menu.menu_home)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_settings -> {
                        findNavController().navigate(R.id.action_global_settingsFragment)
                        true
                    }
                    R.id.menu_map -> {
                        findNavController().navigate(R.id.evacuationMapFragment)
                        true
                    }
                    R.id.menu_node_dashboard -> {
                        findNavController().navigate(R.id.action_home_to_nodeDashboard)
                        true
                    }
                    R.id.menu_signout -> {
                        showSignOutDialog()
                        true
                    }
                    R.id.menu_about -> {
                        showAboutDialog()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun updateFamilyCircleCard(serverVerified: Boolean, cachedVerified: Boolean) {
        val canBroadcast = serverVerified || cachedVerified
        when {
            serverVerified -> {
                binding.tvFamilyCircleStatus.text = getString(R.string.family_circle_unlocked)
                binding.btnFamilyCircleAction.text = getString(R.string.manage_family_circle)
            }
            cachedVerified -> {
                binding.tvFamilyCircleStatus.text = getString(R.string.family_circle_offline_cached)
                binding.btnFamilyCircleAction.text = getString(R.string.manage_family_circle)
            }
            else -> {
                binding.tvFamilyCircleStatus.text = getString(R.string.family_circle_locked)
                binding.btnFamilyCircleAction.text = getString(R.string.verify_now)
            }
        }
        binding.btnImSafe.isVisible = canBroadcast
    }

    override fun onResume() {
        super.onResume()
        pulseAnimator?.resume()
        _binding?.wvSriLankaMap?.onResume()
        lifecycleScope.launch {
            val serverVerified = viewModel.isVerifiedOnServer()
            val cachedVerified = viewModel.isFamilyCircleVerified.value == true
            updateFamilyCircleCard(serverVerified, cachedVerified)
        }
    }

    override fun onPause() {
        _binding?.wvSriLankaMap?.onPause()
        pulseAnimator?.pause()
        super.onPause()
    }

    private fun showSignOutDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sign Out")
            .setMessage("Are you sure you want to sign out?")
            .setPositiveButton("Sign Out") { _, _ ->
                viewModel.signOut()
                val prefs = requireContext().getSharedPreferences("safepath_prefs", Context.MODE_PRIVATE)
                prefs.edit().clear().apply()
                findNavController().navigate(R.id.action_global_authFragment)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("SafePath")
            .setMessage(
                "Version 1.0\nStay Aware. Stay Safe.\n\nBuilt for Sri Lanka.\nEmergency: 119\n\n" +
                    "Offline basemap tiles © OpenStreetMap contributors.\n" +
                    "Optional navigation via Organic Maps — https://organicmaps.app"
            )
            .setPositiveButton("Close", null)
            .show()
    }

    private fun startPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            binding.viewStatusDot,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.35f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.35f, 1f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.45f, 1f)
        ).apply {
            duration = 2200
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupSilhouetteWebView() {
        val webView = _binding?.wvSriLankaMap ?: return
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            cacheMode = WebSettings.LOAD_NO_CACHE
            setSupportZoom(false)
            displayZoomControls = false
            builtInZoomControls = false
            mediaPlaybackRequiresUserGesture = true
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                mapReady = true
                val color = pendingMapColor ?: "#FFFFFF"
                pushMapColor(color, pendingLat, pendingLng)
                pendingSheltersJson?.let { pushSafePlaces(it) }
                    ?: viewModel.shelters.value?.let { pushSafePlaces(sheltersToJson(it)) }
            }
        }
        // Cache-bust so asset updates (detailed silhouette) always load
        webView.loadUrl("file:///android_asset/map_silhouette.html?v=4")
    }

    private fun sheltersToJson(shelters: List<com.hades.safepath.data.entity.Shelter>): String {
        val body = shelters.joinToString(",") { s ->
            val name = s.name.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "")
            """{"lat":${s.lat},"lon":${s.lon},"name":"$name"}"""
        }
        return "[$body]"
    }

    private fun pushSafePlaces(jsonArray: String) {
        val webView = _binding?.wvSriLankaMap ?: return
        webView.evaluateJavascript("setSafePlaces($jsonArray);", null)
    }

    private fun updateAlertCard(alert: Alert?) {
        if (alert != null) {
            binding.tvAlertTitle.text = getString(R.string.home_active_alert)
            binding.tvAlertTitle.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.red_primary)
            )
            binding.tvAlertSubtitle.text = getString(R.string.home_affected_area, alert.district)
            val colorHex = when (alert.severity) {
                "CRITICAL" -> "#D72B2B"
                "HIGH" -> "#EF4444"
                "MODERATE" -> "#F0A500"
                else -> "#22C55E"
            }
            pendingMapColor = colorHex
            pendingLat = alert.latitude
            pendingLng = alert.longitude
            if (mapReady) pushMapColor(colorHex, alert.latitude, alert.longitude)
        } else {
            binding.tvAlertTitle.text = getString(R.string.home_no_active_alerts)
            binding.tvAlertTitle.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.text_primary)
            )
            binding.tvAlertSubtitle.text = getString(R.string.home_area_safe_with_shelters)
            pendingMapColor = "#FFFFFF"
            pendingLat = null
            pendingLng = null
            if (mapReady) pushMapColor("#FFFFFF", null, null)
        }
    }

    private fun pushMapColor(colorHex: String, lat: Double?, lng: Double?) {
        val webView = _binding?.wvSriLankaMap ?: return
        val latArg = lat?.toString() ?: "null"
        val lngArg = lng?.toString() ?: "null"
        webView.evaluateJavascript("updateColor('$colorHex', $latArg, $lngArg);", null)
    }

    override fun onDestroyView() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        mapReady = false
        _binding?.wvSriLankaMap?.apply {
            stopLoading()
            loadUrl("about:blank")
            destroy()
        }
        super.onDestroyView()
        _binding = null
    }
}
