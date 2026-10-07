package com.hades.safepath.guidance

import android.util.Log
import com.hades.safepath.data.model.DisasterEvent
import com.hades.safepath.data.model.DisasterType
import com.hades.safepath.data.model.SafePathDecision
import com.hades.safepath.data.model.SeverityLevel
import com.hades.safepath.data.repository.AlertRepository
import com.hades.safepath.data.repository.GuidanceRepository
import com.hades.safepath.location.SafePathLocationManager
import com.hades.safepath.routing.RoutingEngine

/**
 * Orchestrates the full SafePathDecision pipeline for a given [DisasterEvent].
 *
 * Produces a complete [SafePathDecision] — route, shelter, guidance steps,
 * and pack items — using exclusively on-device resources (R12 offline-first).
 *
 * @param guidanceRepo    Repository for guidance steps and pack items.
 * @param routingEngine   Offline routing engine (must be initialised before use).
 * @param locationManager GPS location provider.
 * @param alertRepository Repository for persisting alert log entries.
 */
class DecisionEngine(
    private val guidanceRepo: GuidanceRepository,
    private val routingEngine: RoutingEngine,
    private val locationManager: SafePathLocationManager,
    private val alertRepository: AlertRepository
) {

    private val TAG = "DecisionEngine"

    // Default fallback — NE Ratnapura road-grid node (not shelter centre).
    // Shelter coords (6.6831, 80.3988) make Dijkstra start==end → 1-node path.
    private val FALLBACK_LAT = 6.6950
    private val FALLBACK_LON = 80.4050

    /**
     * Processes a [DisasterEvent] and returns a complete [SafePathDecision].
     *
     * Steps:
     * 1. Resolve user location (GPS or Ratnapura fallback)
     * 2. Compute evacuation route (skipped for CYCLONE — shelter-in-place)
     * 3. Find nearest shelter
     * 4. Load guidance steps for the event type + severity
     * 5. Load pack items (ADVISORY severity only)
     * 6. Persist the alert to the alert log
     * 7. Return the assembled SafePathDecision
     *
     * Safety note (R6 criterion 7): CYCLONE events use shelter-in-place
     * guidance. The route list is intentionally empty for CYCLONE alerts.
     */
    suspend fun processEvent(event: DisasterEvent): SafePathDecision {
        Log.d(TAG, "Processing event: ${event.disasterType} / ${event.severity} (code ${event.rawCode})")

        // ── 1. Resolve user location ──────────────────────────────────────────
        val location = locationManager.getLastKnownLocation()
        val (userLat, userLon) = if (location != null) {
            Pair(location.latitude, location.longitude)
        } else {
            Log.w(TAG, "GPS unavailable — using Ratnapura fallback (${FALLBACK_LAT}, ${FALLBACK_LON})")
            Pair(FALLBACK_LAT, FALLBACK_LON)
        }

        // ── 2. Find nearest shelter ───────────────────────────────────────────
        val shelter = routingEngine.findNearestShelter(userLat, userLon)

        // ── 3. Compute evacuation route ───────────────────────────────────────
        // SAFETY CRITICAL (R6 criterion 7): CYCLONE = shelter-in-place.
        // Do NOT compute an evacuation route for cyclone events.
        val route = if (event.disasterType == DisasterType.CYCLONE) {
            Log.d(TAG, "CYCLONE event — shelter-in-place mode, no evacuation route computed")
            emptyList()
        } else {
            routingEngine.calculateEvacuationRoute(userLat, userLon, event.disasterType)
        }

        // ── 4. Load guidance steps ────────────────────────────────────────────
        val steps = guidanceRepo.getStepsForEvent(
            event.disasterType.name,
            event.severity.name
        )
        Log.d(TAG, "Loaded ${steps.size} guidance steps for ${event.disasterType}/${event.severity}")

        // ── 5. Load pack items (ADVISORY only) ────────────────────────────────
        val packItems = if (event.severity == SeverityLevel.ADVISORY) {
            guidanceRepo.getPackItems(event.disasterType.name).also {
                Log.d(TAG, "Loaded ${it.size} pack items for ADVISORY severity")
            }
        } else {
            emptyList()
        }

        // ── 6. Persist alert log ──────────────────────────────────────────────
        val alert = com.hades.safepath.data.model.Alert(
            id = event.rawCode.toString(),
            type = event.disasterType.name,
            severity = event.severity.name,
            district = "$userLat, $userLon",
            latitude = userLat,
            longitude = userLon,
            createdAt = event.receivedAt.toString(),
            description = ""
        )
        alertRepository.saveAlert(alert)
        Log.d(TAG, "Alert logged: code=${event.rawCode} at ($userLat, $userLon)")

        // ── 7. Assemble and return decision ───────────────────────────────────
        return SafePathDecision(
            event = event,
            route = route,
            shelter = shelter,
            guidanceSteps = steps,
            packItems = packItems
        ).also {
            Log.d(
                TAG,
                "SafePathDecision ready: shelter=${shelter.name}, " +
                    "route=${route.size} nodes, steps=${steps.size}, packs=${packItems.size}"
            )
        }
    }
}
