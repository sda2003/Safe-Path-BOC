package com.hades.safepath.ui.map.offline

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.hades.safepath.R
import com.hades.safepath.databinding.FragmentOfflineMapBinding
import com.hades.safepath.location.LastLocationCache
import com.hades.safepath.map.offline.OfflineMapLayerRepository
import com.hades.safepath.map.offline.OfflineTileRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import javax.inject.Inject

/**
 * Dual-mode OsmDroid evacuation map — Online (OSM.de + OSRM) / Offline (MBTiles + Dijkstra).
 * Routes stay inside SafePath; never launches Google Maps / Organic Maps / browser.
 */
@AndroidEntryPoint
class OfflineMapFragment : Fragment() {

    private var _binding: FragmentOfflineMapBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OfflineMapViewModel by viewModels()

    @Inject lateinit var lastLocationCache: LastLocationCache
    @Inject lateinit var tileRepository: OfflineTileRepository

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var routePolyline: Polyline? = null
    private var routeHalo: Polyline? = null
    private val shelterMarkers = mutableListOf<Marker>()
    private val layerOverlays = mutableListOf<org.osmdroid.views.overlay.Overlay>()
    private var locationUpdatesActive = false
    private var lastGps: GeoPoint? = null
    private var offlineProviderReady = false
    private var currentEffective: com.hades.safepath.map.mode.EffectiveMapMode? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            onFreshLocation(loc)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            enableMyLocationOverlay()
            startLocationUpdates()
        } else {
            applyCachedOrFallbackLocation()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Point OsmDroid at internal cache + empty-URL Mapnik BEFORE MapView inflates,
        // otherwise the default provider hits osm.org and paints 403 "Access blocked" tiles.
        tileRepository.configureCaches()
        _binding = FragmentOfflineMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        setupOfflineMapView()
        observeViewModel()
        viewModel.initializeOfflineMap()
        requestUserLocation()

        binding.btnMapBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.btnEvacuate.setOnClickListener {
            viewModel.calculateEvacuationRoute()
        }
        binding.btnRouteHere.setOnClickListener {
            viewModel.selectedShelter.value?.shelter?.let { viewModel.routeToShelter(it) }
        }
        binding.btnMyLocation.setOnClickListener {
            centerOnUserLocation()
        }
        binding.btnMapMode.setOnClickListener {
            viewModel.cycleMapMode()
        }
        binding.tvMapAttribution.setOnClickListener {
            showAttributionDialog()
        }
        binding.cardShelterDetail.setOnLongClickListener {
            viewModel.clearShelterSelection()
            true
        }
    }

    // -------------------------------------------------------------------------
    // Offline MapView configuration
    // -------------------------------------------------------------------------

    private fun setupOfflineMapView() {
        val map = binding.mapView
        map.setMultiTouchControls(true)
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
        applyIslandViewport(map)
        // Until archives finish copying, never request online Mapnik tiles
        map.setTileSource(tileRepository.offlineOnlyTileSource())
        map.setUseDataConnection(false)
        map.overlayManager.tilesOverlay.isEnabled = false
        map.setBackgroundColor(Color.parseColor("#E8EEF4"))
    }

    private fun applyTileSetup(setup: OfflineTileRepository.TileSetup) {
        offlineProviderReady = setup.hasLocalArchive && setup.offlineProvider != null
        val mode = currentEffective
            ?: viewModel.effectiveMode.value
            ?: com.hades.safepath.map.mode.EffectiveMapMode.OFFLINE
        applyEffectiveMode(mode)
    }

    private fun applyEffectiveMode(mode: com.hades.safepath.map.mode.EffectiveMapMode) {
        val map = _binding?.mapView ?: return
        if (!isAdded) return
        val previous = currentEffective
        currentEffective = mode
        val cam = map.mapCenter
        val zoom = map.zoomLevelDouble

        try {
            when (mode) {
                com.hades.safepath.map.mode.EffectiveMapMode.ONLINE -> {
                    val source = com.hades.safepath.map.tiles.OnlineTileSources.esriWorldStreet()
                    // Must use SafePathRegisterReceiver — MapTileProviderBasic(Context)
                    // crashes on API 34+ (missing RECEIVER_NOT_EXPORTED).
                    val onlineProvider = com.hades.safepath.map.offline.SafePathOnlineTileProviders
                        .create(requireContext(), source)
                    map.setTileProvider(onlineProvider)
                    map.setTileSource(source)
                    map.setUseDataConnection(true)
                    map.overlayManager.tilesOverlay.isEnabled = true
                    map.minZoomLevel = 5.0
                    map.maxZoomLevel = 19.0
                    map.setScrollableAreaLimitDouble(
                        OfflineTileRepository.sriLankaBoundingBox()
                    )
                    binding.tvMapAttribution.text =
                        com.hades.safepath.map.tiles.OnlineTileSources.ATTRIBUTION_ONLINE
                    map.setBackgroundColor(Color.parseColor("#E8EEF4"))
                    android.util.Log.i("OfflineMapFragment", "[MAP] Mode = ONLINE")
                }
                com.hades.safepath.map.mode.EffectiveMapMode.OFFLINE -> {
                    val setup = viewModel.tileSetup.value
                    if (setup?.hasLocalArchive == true && setup.offlineProvider != null) {
                        val provider = setup.offlineProvider
                        provider.setTileSource(setup.tileSource)
                        provider.setUseDataConnection(false)
                        map.setTileProvider(provider)
                        map.setTileSource(setup.tileSource)
                        map.setUseDataConnection(false)
                        map.overlayManager.tilesOverlay.isEnabled = true
                        if (previous == com.hades.safepath.map.mode.EffectiveMapMode.ONLINE) {
                            runCatching { provider.clearTileCache() }
                        }
                    } else {
                        map.setTileSource(tileRepository.offlineOnlyTileSource())
                        map.setUseDataConnection(false)
                        map.overlayManager.tilesOverlay.isEnabled = false
                    }
                    map.minZoomLevel = OfflineTileRepository.ZOOM_MIN.toDouble()
                    map.maxZoomLevel = (OfflineTileRepository.ZOOM_MAX + 2).toDouble()
                    map.setScrollableAreaLimitDouble(OfflineTileRepository.sriLankaBoundingBox())
                    binding.tvMapAttribution.text =
                        com.hades.safepath.map.tiles.OnlineTileSources.ATTRIBUTION_OFFLINE
                    map.setBackgroundColor(Color.parseColor("#E8EEF4"))
                    android.util.Log.i("OfflineMapFragment", "[MAP] Mode = OFFLINE")
                }
            }
            if (cam != null) {
                val zMin = map.minZoomLevel
                val zMax = map.maxZoomLevel
                val safeZoom = when {
                    zMax < zMin -> zMin
                    else -> zoom.coerceIn(zMin, zMax)
                }
                map.controller.setCenter(cam)
                map.controller.setZoom(safeZoom)
            }
            map.invalidate()
        } catch (t: Throwable) {
            android.util.Log.e("OfflineMapFragment", "[MAP] applyEffectiveMode failed mode=$mode", t)
            runCatching {
                map.setUseDataConnection(false)
                map.overlayManager.tilesOverlay.isEnabled = false
                binding.tvMapStatus.text = "Map tiles error — showing overlays only"
            }
        }
    }

    private fun showAttributionDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Map data & attribution")
            .setMessage(
                "© OpenStreetMap contributors (ODbL)\n" +
                    "Online tiles: Esri World Street Map\n" +
                    "Online routing: OSRM (router.project-osrm.org)\n" +
                    "Offline basemap: SafePath Sri Lanka MBTiles (OSM)\n" +
                    "Offline routing: SafePath hazard-aware Dijkstra\n\n" +
                    "Routes are drawn inside SafePath — Google Maps / Organic Maps / browsers are not opened."
            )
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /** Full-island start view + pan clamp to Sri Lanka (offline). */
    private fun applyIslandViewport(map: org.osmdroid.views.MapView) {
        map.minZoomLevel = OfflineTileRepository.ZOOM_MIN.toDouble()
        map.maxZoomLevel = (OfflineTileRepository.ZOOM_MAX + 2).toDouble()
        map.setScrollableAreaLimitDouble(OfflineTileRepository.sriLankaBoundingBox())
        map.controller.setZoom(OfflineTileRepository.DEFAULT_ISLAND_ZOOM)
        map.controller.setCenter(
            GeoPoint(OfflineTileRepository.SL_CENTER_LAT, OfflineTileRepository.SL_CENTER_LON)
        )
    }

    // -------------------------------------------------------------------------
    // GPS — MyLocationNewOverlay (hardware GPS, works offline / airplane)
    // -------------------------------------------------------------------------

    private fun enableMyLocationOverlay() {
        if (myLocationOverlay != null) return
        val provider = GpsMyLocationProvider(requireContext())
        myLocationOverlay = MyLocationNewOverlay(provider, binding.mapView).also { overlay ->
            overlay.enableMyLocation()
            overlay.enableFollowLocation()
            overlay.isDrawAccuracyEnabled = true
            binding.mapView.overlays.add(overlay)
        }
        binding.mapView.invalidate()
    }

    private fun centerOnUserLocation() {
        val point = lastGps
            ?: myLocationOverlay?.myLocation
            ?: GeoPoint(OfflineMapViewModel.PILOT_LAT, OfflineMapViewModel.PILOT_LON)
        myLocationOverlay?.enableFollowLocation()
        binding.mapView.controller.animateTo(point, 14.0, 600L)
    }

    private fun requestUserLocation() {
        viewLifecycleOwner.lifecycleScope.launch {
            val (lat, lon) = lastLocationCache.getLastKnownCoordinates()
            viewModel.onUserLocation(lat, lon)
            lastGps = GeoPoint(lat, lon)
        }

        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            enableMyLocationOverlay()
            startLocationUpdates()
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (locationUpdatesActive) return
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) onFreshLocation(loc) else applyCachedOrFallbackLocation()
            }
            .addOnFailureListener { applyCachedOrFallbackLocation() }

        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 4_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .setWaitForAccurateLocation(false)
            .build()

        runCatching {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
            locationUpdatesActive = true
        }.onFailure { applyCachedOrFallbackLocation() }
    }

    private fun stopLocationUpdates() {
        if (!locationUpdatesActive) return
        runCatching { fusedLocationClient.removeLocationUpdates(locationCallback) }
        locationUpdatesActive = false
    }

    private fun onFreshLocation(loc: Location) {
        lastLocationCache.save(loc.latitude, loc.longitude)
        lastGps = GeoPoint(loc.latitude, loc.longitude)
        viewModel.onUserLocation(loc.latitude, loc.longitude)
    }

    private fun applyCachedOrFallbackLocation() {
        viewLifecycleOwner.lifecycleScope.launch {
            val (lat, lon) = lastLocationCache.getLastKnownCoordinates()
            lastGps = GeoPoint(lat, lon)
            viewModel.onUserLocation(lat, lon)
        }
    }

    // -------------------------------------------------------------------------
    // Route drawing — Dijkstra → green polyline
    // -------------------------------------------------------------------------

    /**
     * Thick SafePath-red evacuation polyline (halo + core). Fits camera to route.
     */
    fun drawEvacuationRoute(routePoints: List<GeoPoint>, isDirectFallback: Boolean = false) {
        val map = _binding?.mapView ?: return
        routeHalo?.let { map.overlays.remove(it) }
        routePolyline?.let { map.overlays.remove(it) }
        routeHalo = null
        routePolyline = null

        if (routePoints.size < 2) {
            binding.cardRouteInfo.isVisible = false
            map.invalidate()
            return
        }

        val coreColor = if (isDirectFallback) {
            Color.argb(200, 156, 163, 175) // muted gray = not a road route
        } else {
            Color.argb(230, 220, 38, 38) // SafePath red
        }

        val halo = Polyline(map).apply {
            outlinePaint.color = Color.argb(160, 255, 255, 255)
            outlinePaint.strokeWidth = 26f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
            outlinePaint.isAntiAlias = true
            setPoints(ArrayList(routePoints))
        }
        val line = Polyline(map).apply {
            outlinePaint.color = coreColor
            outlinePaint.strokeWidth = 14f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
            outlinePaint.isAntiAlias = true
            setPoints(ArrayList(routePoints))
        }
        routeHalo = halo
        routePolyline = line
        map.overlays.add(halo)
        map.overlays.add(line)

        runCatching {
            map.zoomToBoundingBox(BoundingBox.fromGeoPoints(routePoints), true, 120)
        }.onFailure {
            map.controller.animateTo(routePoints.first(), 15.0, 500L)
        }
        map.invalidate()
    }

    // -------------------------------------------------------------------------
    // Overlays (hazards / shelters) — still offline GeoJSON
    // -------------------------------------------------------------------------

    private fun observeViewModel() {
        viewModel.status.observe(viewLifecycleOwner) { binding.tvMapStatus.text = it }

        viewModel.tileSetup.observe(viewLifecycleOwner) { setup ->
            setup ?: return@observe
            applyTileSetup(setup)
        }

        viewModel.overlays.observe(viewLifecycleOwner) { pack ->
            pack ?: return@observe
            drawStaticLayers(pack)
        }

        viewModel.shelters.observe(viewLifecycleOwner) { items ->
            drawShelters(items)
        }

        viewModel.route.observe(viewLifecycleOwner) { result ->
            if (result == null) {
                drawEvacuationRoute(emptyList())
                binding.cardRouteInfo.isVisible = false
                return@observe
            }
            val pts = result.points.map { GeoPoint(it.lat, it.lon) }
            drawEvacuationRoute(pts, result.isDirectFallback)
            binding.cardRouteInfo.isVisible = true
            val km = result.distanceMeters / 1000.0
            val eta = result.durationSeconds?.let {
                " · ~${(it / 60).toInt().coerceAtLeast(1)} min"
            }.orEmpty()
            val note = result.message?.let { "\n$it" }.orEmpty()
            binding.tvRouteSummary.text =
                "${"%.1f".format(km)} km$eta · ${result.providerLabel} · ${result.nodeCount} pts$note"
        }

        viewModel.effectiveMode.observe(viewLifecycleOwner) { mode ->
            mode ?: return@observe
            applyEffectiveMode(mode)
        }

        viewModel.mapModePreference.observe(viewLifecycleOwner) { pref ->
            pref ?: return@observe
            binding.btnMapMode.text = when (pref) {
                com.hades.safepath.map.mode.MapMode.AUTO -> "● AUTO"
                com.hades.safepath.map.mode.MapMode.ONLINE -> "● ONLINE"
                com.hades.safepath.map.mode.MapMode.OFFLINE -> "● OFFLINE"
            }
        }

        viewModel.modeHint.observe(viewLifecycleOwner) { hint ->
            binding.tvModeHint.isVisible = !hint.isNullOrBlank()
            binding.tvModeHint.text = hint.orEmpty()
        }

        viewModel.selectedShelter.observe(viewLifecycleOwner) { item ->
            if (item == null) {
                binding.cardShelterDetail.isVisible = false
                return@observe
            }
            binding.cardShelterDetail.isVisible = true
            binding.tvShelterName.text = item.shelter.name
            val dist = item.distanceMeters?.let { d ->
                if (d >= 1000) String.format("%.1f km away", d / 1000.0)
                else String.format("%.0f m away", d)
            } ?: "Distance unknown"
            binding.tvShelterMeta.text = "${item.shelter.district} · $dist"
            val verified = if (item.shelter.isVerified) "Verified" else "Unverified"
            binding.tvShelterCapacity.text =
                "Capacity: ${item.shelter.capacityPersons} persons · $verified"
            binding.tvShelterContact.text = if (item.shelter.contactPhone.isNotBlank()) {
                "Contact: ${item.shelter.contactPhone}"
            } else {
                "Contact: unavailable offline"
            }
        }

        viewModel.focusPilotArea.observe(viewLifecycleOwner) { focus ->
            if (focus == true) zoomToPilotContent()
        }
    }

    private fun drawStaticLayers(pack: OfflineMapLayerRepository.OverlayPack) {
        val map = binding.mapView
        layerOverlays.forEach { map.overlays.remove(it) }
        layerOverlays.clear()

        pack.roads.forEach { road ->
            val line = Polyline(map).apply {
                outlinePaint.color = Color.parseColor("#559CA3AF")
                outlinePaint.strokeWidth = 3f
                setPoints(road.points)
            }
            layerOverlays += line
            map.overlays.add(line)
        }

        pack.floodZones.forEach { zone ->
            zone.rings.forEach { ring ->
                val poly = Polygon(map).apply {
                    fillPaint.color = Color.parseColor("#40007BFF")
                    outlinePaint.color = Color.parseColor("#003D82")
                    outlinePaint.strokeWidth = 3f
                    title = zone.name
                    points = ArrayList(ring)
                }
                layerOverlays += poly
                map.overlays.add(poly)
            }
        }

        pack.landslideZones.forEach { zone ->
            zone.rings.forEach { ring ->
                val poly = Polygon(map).apply {
                    fillPaint.color = Color.parseColor("#40DC3545")
                    outlinePaint.color = Color.parseColor("#DC3545")
                    outlinePaint.strokeWidth = 3f
                    title = zone.name
                    points = ArrayList(ring)
                }
                layerOverlays += poly
                map.overlays.add(poly)
            }
        }
        map.invalidate()
        zoomToPilotContent()
    }

    private fun drawShelters(items: List<ShelterMapItem>) {
        val map = binding.mapView
        shelterMarkers.forEach { map.overlays.remove(it) }
        shelterMarkers.clear()
        val greenIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_shelter_marker)

        items.forEach { item ->
            val marker = Marker(map).apply {
                position = GeoPoint(item.shelter.lat, item.shelter.lon)
                title = item.shelter.name
                snippet = "${item.shelter.district} · cap ${item.shelter.capacityPersons}"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = greenIcon
                relatedObject = item
                setOnMarkerClickListener { m, _ ->
                    (m.relatedObject as? ShelterMapItem)?.let { viewModel.selectShelter(it) }
                    true
                }
            }
            shelterMarkers += marker
            map.overlays.add(marker)
        }
        map.invalidate()
        if (items.isNotEmpty()) zoomToPilotContent()
    }

    private fun zoomToPilotContent() {
        val map = _binding?.mapView ?: return
        val points = mutableListOf<GeoPoint>()
        layerOverlays.forEach { overlay ->
            when (overlay) {
                is Polyline -> points += overlay.actualPoints
                is Polygon -> points += overlay.actualPoints
            }
        }
        shelterMarkers.forEach { points += it.position }
        if (points.isEmpty()) {
            applyIslandViewport(map)
            return
        }
        runCatching {
            map.zoomToBoundingBox(BoundingBox.fromGeoPoints(points), true, 72)
        }.onFailure {
            applyIslandViewport(map)
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onResume() {
        super.onResume()
        _binding?.mapView?.onResume()
        myLocationOverlay?.enableMyLocation()
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        stopLocationUpdates()
        myLocationOverlay?.disableMyLocation()
        _binding?.mapView?.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        stopLocationUpdates()
        myLocationOverlay?.disableMyLocation()
        myLocationOverlay = null
        _binding?.mapView?.onDetach()
        _binding = null
        super.onDestroyView()
    }
}
