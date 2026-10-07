package com.hades.safepath.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.hades.safepath.R
import com.hades.safepath.databinding.BottomSheetReportAlertBinding
import com.hades.safepath.location.LastLocationCache
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ReportAlertBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetReportAlertBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AlertViewModel by viewModels()
    private val mapViewModel: MapViewModel by activityViewModels()

    @Inject lateinit var lastLocationCache: LastLocationCache

    private var userLatitude: Double = 0.0
    private var userLongitude: Double = 0.0
    private var hasLocation: Boolean = false
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) getCurrentLocation() else showLocationFailed()
    }

    private data class DisasterType(val id: String, val labelRes: Int, val iconRes: Int)

    private data class SeverityOption(val id: String, val labelRes: Int, val colorRes: Int)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetReportAlertBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.let { sheet ->
            val bottom = sheet.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return
            BottomSheetBehavior.from(bottom).apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        setupTypeChips()
        setupSeverityChips()
        binding.btnRetryLocation.setOnClickListener { getCurrentLocation() }
        binding.btnSubmitAlert.setOnClickListener { submitAlert() }

        viewModel.reportResult.observe(viewLifecycleOwner) { result ->
            setSubmitting(false)
            result.onSuccess {
                val type = selectedTypeId()?.lowercase() ?: "general"
                requireContext()
                    .getSharedPreferences("safepath_prefs", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putString("last_alert_type", type)
                    .apply()
                mapViewModel.loadActiveAlerts()
                val msg = if (com.hades.safepath.data.remote.SupabaseConfig.isConfigured()) {
                    getString(R.string.report_success)
                } else {
                    "Alert saved on this device. Add real Supabase keys to sync online."
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                dismiss()
            }.onFailure { e ->
                Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        // Prefer cached coords immediately, then refresh GPS
        viewLifecycleOwner.lifecycleScope.launch {
            val (lat, lon) = lastLocationCache.getLastKnownCoordinates()
            if (!hasLocation) {
                userLatitude = lat
                userLongitude = lon
                hasLocation = true
                showLocationReady(lat, lon)
            }
        }
        getCurrentLocation()
    }

    private fun setupTypeChips() {
        val types = listOf(
            DisasterType("Flood", R.string.report_type_flood, R.drawable.ic_flood),
            DisasterType("Landslide", R.string.report_type_landslide, R.drawable.ic_disaster_landslide),
            DisasterType("Fire", R.string.report_type_fire, R.drawable.ic_disaster_fire),
            DisasterType("Tsunami", R.string.report_type_tsunami, R.drawable.ic_disaster_tsunami),
            DisasterType("Earthquake", R.string.report_type_earthquake, R.drawable.ic_disaster_earthquake),
            DisasterType("Other", R.string.report_type_other, R.drawable.ic_disaster_other)
        )
        val bg = ContextCompat.getColor(requireContext(), R.color.bg_card_elevated)
        val border = ContextCompat.getColor(requireContext(), R.color.border)
        val selected = ContextCompat.getColor(requireContext(), R.color.red_primary)
        val text = ContextCompat.getColor(requireContext(), R.color.text_primary)

        types.forEach { type ->
            val chip = Chip(requireContext()).apply {
                this.text = getString(type.labelRes)
                tag = type.id
                isCheckable = true
                isCheckedIconVisible = false
                chipIcon = runCatching {
                    ContextCompat.getDrawable(requireContext(), type.iconRes)
                }.getOrElse {
                    ContextCompat.getDrawable(requireContext(), R.drawable.ic_warning_triangle)
                }
                chipIconTint = ColorStateList.valueOf(text)
                chipBackgroundColor = ColorStateList.valueOf(bg)
                chipStrokeColor = ColorStateList.valueOf(border)
                chipStrokeWidth = resources.displayMetrics.density
                setTextColor(text)
                setOnCheckedChangeListener { button, checked ->
                    val c = button as Chip
                    if (checked) {
                        c.chipBackgroundColor = ColorStateList.valueOf(selected)
                        c.chipStrokeWidth = 0f
                    } else {
                        c.chipBackgroundColor = ColorStateList.valueOf(bg)
                        c.chipStrokeColor = ColorStateList.valueOf(border)
                        c.chipStrokeWidth = resources.displayMetrics.density
                    }
                }
            }
            binding.chipGroupType.addView(chip)
        }
    }

    private fun setupSeverityChips() {
        val options = listOf(
            SeverityOption("LOW", R.string.report_sev_low, R.color.green_light),
            SeverityOption("MODERATE", R.string.report_sev_moderate, R.color.amber_light),
            SeverityOption("HIGH", R.string.report_sev_high, R.color.red_primary),
            SeverityOption("CRITICAL", R.string.report_sev_critical, R.color.red_dark)
        )
        val idleBg = ContextCompat.getColor(requireContext(), R.color.bg_card_elevated)

        options.forEachIndexed { index, option ->
            val accent = ContextCompat.getColor(requireContext(), option.colorRes)
            val chip = Chip(requireContext()).apply {
                text = getString(option.labelRes)
                tag = option.id
                isCheckable = true
                isCheckedIconVisible = false
                chipBackgroundColor = ColorStateList.valueOf(idleBg)
                chipStrokeColor = ColorStateList.valueOf(accent)
                chipStrokeWidth = resources.displayMetrics.density * 1.5f
                setTextColor(accent)
                setOnCheckedChangeListener { button, checked ->
                    val c = button as Chip
                    if (checked) {
                        c.chipBackgroundColor = ColorStateList.valueOf(
                            Color.argb(55, Color.red(accent), Color.green(accent), Color.blue(accent))
                        )
                        c.chipStrokeWidth = resources.displayMetrics.density * 2f
                    } else {
                        c.chipBackgroundColor = ColorStateList.valueOf(idleBg)
                        c.chipStrokeWidth = resources.displayMetrics.density * 1.5f
                    }
                }
            }
            binding.chipGroupSeverity.addView(chip)
            if (index == 1) chip.isChecked = true // Moderate default
        }
    }

    private fun submitAlert() {
        val typeId = selectedTypeId()
        if (typeId == null) {
            Toast.makeText(context, R.string.report_select_type, Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasLocation) {
            Toast.makeText(context, R.string.report_need_location, Toast.LENGTH_SHORT).show()
            getCurrentLocation()
            return
        }

        val severity = selectedSeverityId() ?: "MODERATE"
        val description = binding.etDescription.text?.toString()?.trim().orEmpty()
        val district = binding.spinnerDistrict.selectedItem?.toString().orEmpty()

        setSubmitting(true)
        viewModel.reportAlert(
            typeId,
            severity,
            description,
            userLatitude,
            userLongitude,
            district
        )
    }

    private fun selectedTypeId(): String? =
        binding.chipGroupType.children
            .filterIsInstance<Chip>()
            .firstOrNull { it.isChecked }
            ?.tag as? String

    private fun selectedSeverityId(): String? =
        binding.chipGroupSeverity.children
            .filterIsInstance<Chip>()
            .firstOrNull { it.isChecked }
            ?.tag as? String

    private fun setSubmitting(loading: Boolean) {
        binding.btnSubmitAlert.isEnabled = !loading
        binding.btnSubmitAlert.text = if (loading) {
            getString(R.string.report_submitting)
        } else {
            getString(R.string.report_submit)
        }
        binding.progressSubmit.isVisible = loading
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        binding.tvLocationStatus.text = getString(R.string.report_location_getting)
        binding.tvLocationStatus.setTextColor(
            ContextCompat.getColor(requireContext(), R.color.amber_light)
        )
        binding.ivLocation.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), R.color.amber_light)
        )
        binding.btnRetryLocation.isVisible = false

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (_binding == null || !isAdded) return@addOnSuccessListener
                if (location != null) {
                    userLatitude = location.latitude
                    userLongitude = location.longitude
                    hasLocation = true
                    lastLocationCache.save(location.latitude, location.longitude)
                    showLocationReady(location.latitude, location.longitude)
                } else if (!hasLocation) {
                    showLocationFailed()
                } else {
                    showLocationReady(userLatitude, userLongitude)
                }
            }
            .addOnFailureListener {
                if (_binding == null || !isAdded) return@addOnFailureListener
                if (!hasLocation) showLocationFailed()
                else showLocationReady(userLatitude, userLongitude)
            }
    }

    private fun showLocationReady(lat: Double, lon: Double) {
        binding.tvLocationStatus.text = getString(R.string.report_location_ready, lat, lon)
        val green = ContextCompat.getColor(requireContext(), R.color.green_light)
        binding.tvLocationStatus.setTextColor(green)
        binding.ivLocation.imageTintList = ColorStateList.valueOf(green)
        binding.btnRetryLocation.isVisible = false
    }

    private fun showLocationFailed() {
        binding.tvLocationStatus.text = getString(R.string.report_location_failed)
        val red = ContextCompat.getColor(requireContext(), R.color.red_primary)
        binding.tvLocationStatus.setTextColor(red)
        binding.ivLocation.imageTintList = ColorStateList.valueOf(red)
        binding.btnRetryLocation.isVisible = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
