package com.hades.safepath.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.hades.safepath.data.entity.Shelter
import com.hades.safepath.data.model.Alert
import com.hades.safepath.databinding.FragmentEvacuationMapBinding
import com.hades.safepath.location.LastLocationCache
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.Polyline
import android.graphics.Color
import javax.inject.Inject

@AndroidEntryPoint
class EvacuationMapFragment : Fragment() {

    private var _binding: FragmentEvacuationMapBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MapViewModel by activityViewModels()

    @Inject lateinit var lastLocationCache: LastLocationCache

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var currentRoutePolyline: Polyline? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            setupMyLocation()
            fetchGpsLocation()
        } else {
            applyCachedOrFallbackLocation()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Configuration.getInstance().load(requireContext(), android.preference.PreferenceManager.getDefaultSharedPreferences(requireContext()))
        Configuration.getInstance().userAgentValue = requireContext().packageName
        _binding = FragmentEvacuationMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        
        setupMapView()
        requestUserLocation()

        binding.btnMapBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        viewModel.activeAlerts.observe(viewLifecycleOwner) { alerts ->
            val count = alerts.size
            binding.tvActiveAlertCount.text = "$count active alert${if (count == 1) "" else "s"}"
            redrawMarkers(alerts, viewModel.shelters.value.orEmpty())
        }

        viewModel.shelters.observe(viewLifecycleOwner) { shelters ->
            redrawMarkers(viewModel.activeAlerts.value.orEmpty(), shelters)
        }

        viewModel.route.observe(viewLifecycleOwner) { routeNodes ->
            currentRoutePolyline?.let { binding.mapView.overlays.remove(it) }
            if (routeNodes.isNotEmpty()) {
                val polyline = Polyline()
                polyline.color = Color.parseColor("#4285F4") // Google Maps blue
                polyline.width = 12f
                val geoPoints = routeNodes.map { GeoPoint(it.lat, it.lon) }
                polyline.setPoints(geoPoints)
                binding.mapView.overlays.add(0, polyline) // Add below markers
                currentRoutePolyline = polyline
                binding.mapView.invalidate()
            }
        }
        
        viewModel.loadActiveAlerts()
        
        // Setup directions button listener from the bottom sheet
        binding.btnGetDirections.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val (lat, lon) = lastLocationCache.getLastKnownCoordinates()
                if (lat != 0.0 && lon != 0.0) {
                    viewModel.calculateRouteToNearestShelter(lat, lon)
                    binding.cardAlertDetail.visibility = View.GONE
                }
            }
        }
    }

    private fun setupMapView() {
        // Hard offline — EvacuationMapFragment must never hit osm.org (403)
        binding.mapView.setTileSource(
            object : org.osmdroid.tileprovider.tilesource.XYTileSource(
                "Mapnik",
                com.hades.safepath.map.offline.OfflineTileRepository.ZOOM_MIN,
                com.hades.safepath.map.offline.OfflineTileRepository.ZOOM_MAX,
                256,
                ".png",
                emptyArray()
            ) {}
        )
        binding.mapView.setUseDataConnection(false)
        binding.mapView.overlayManager.tilesOverlay.isEnabled = false
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        binding.mapView.controller.setZoom(12.0)
        // Default center to Sri Lanka
        binding.mapView.controller.setCenter(GeoPoint(7.8731, 80.7718))
    }

    private fun setupMyLocation() {
        if (myLocationOverlay == null) {
            myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), binding.mapView)
            myLocationOverlay?.enableMyLocation()
            binding.mapView.overlays.add(myLocationOverlay)
        }
    }

    private fun requestUserLocation() {
        viewLifecycleOwner.lifecycleScope.launch {
            val (lat, lon) = lastLocationCache.getLastKnownCoordinates()
            if (lat != 0.0 && lon != 0.0) {
                binding.mapView.controller.setCenter(GeoPoint(lat, lon))
                binding.mapView.controller.setZoom(15.0)
            }
        }

        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            setupMyLocation()
            fetchGpsLocation()
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchGpsLocation() {
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    lastLocationCache.save(loc.latitude, loc.longitude)
                    binding.mapView.controller.animateTo(GeoPoint(loc.latitude, loc.longitude), 15.0, 1000L)
                } else {
                    applyCachedOrFallbackLocation()
                }
            }
            .addOnFailureListener { applyCachedOrFallbackLocation() }
    }

    private fun applyCachedOrFallbackLocation() {
        viewLifecycleOwner.lifecycleScope.launch {
            val (lat, lon) = lastLocationCache.getLastKnownCoordinates()
            if (lat != 0.0 && lon != 0.0) {
                binding.mapView.controller.animateTo(GeoPoint(lat, lon), 15.0, 1000L)
            }
        }
    }

    private fun redrawMarkers(alerts: List<Alert>, shelters: List<Shelter>) {
        binding.mapView.overlays.removeAll { it is Marker }
        
        // Ensure my location overlay is kept
        myLocationOverlay?.let { binding.mapView.overlays.add(it) }
        
        shelters.forEach { shelter ->
            val marker = Marker(binding.mapView)
            marker.position = GeoPoint(shelter.lat, shelter.lon)
            marker.title = shelter.name
            binding.mapView.overlays.add(marker)
        }

        alerts.forEach { alert ->
            if (alert.latitude == 0.0 && alert.longitude == 0.0) return@forEach
            val marker = Marker(binding.mapView)
            marker.position = GeoPoint(alert.latitude, alert.longitude)
            marker.title = "${alert.type}: ${alert.description}"
            binding.mapView.overlays.add(marker)
        }
        
        binding.mapView.invalidate()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.mapView.onDetach()
        _binding = null
    }
}
