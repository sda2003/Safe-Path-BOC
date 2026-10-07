package com.hades.safepath.data.seeding

import android.util.Log
import com.hades.safepath.data.SafePathDatabase
import com.hades.safepath.data.entity.GuidanceStep
import com.hades.safepath.data.entity.PackItem
import com.hades.safepath.data.entity.Shelter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Seeds the SafePath Room database with all required guidance steps,
 * pack items, and shelter records.
 *
 * Safe to call on every launch — each table is filled independently if empty.
 * Fixes upgrades where Room onCreate raced before INSTANCE was set (empty shelters).
 *
 * Content review notes:
 * - FLOOD / LANDSLIDE: evacuation guidance — route away from hazard zone.
 * - CYCLONE: shelter-in-place ONLY — no evacuation route instructions.
 *   This distinction is safety-critical (R6 criterion 7).
 */
object DatabaseSeeder {

    private const val TAG = "DatabaseSeeder"

    /**
     * Ensures shelters / guidance / pack rows exist. Idempotent per table.
     */
    suspend fun ensureSeeded(db: SafePathDatabase) = withContext(Dispatchers.IO) {
        seedShelters(db)
        seedGuidanceSteps(db)
        seedPackItems(db)
    }

    fun seed(db: SafePathDatabase) {
        runBlocking(Dispatchers.IO) {
            ensureSeeded(db)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shelters
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun seedShelters(db: SafePathDatabase) {
        val dao = db.shelterDao()
        if (dao.getAllShelters().isNotEmpty()) {
            Log.d(TAG, "Shelters already seeded — skipping")
            return
        }

        val shelters = listOf(
            // Ratnapura district
            Shelter(1L, "Ratnapura Central School", "Ratnapura", 6.6831, 80.3988, 500, true, "045-222-2100"),
            Shelter(2L, "Ratnapura District Hospital", "Ratnapura", 6.6824, 80.3972, 300, true, "045-222-2261"),
            Shelter(3L, "Ratnapura Divisional Secretariat", "Ratnapura", 6.6841, 80.3994, 200, true, "045-222-2275"),
            // Kegalle district
            Shelter(4L, "Kegalle Central College", "Kegalle", 7.2513, 80.3464, 450, true, "035-222-2201"),
            Shelter(5L, "Kegalle District Hospital", "Kegalle", 7.2530, 80.3450, 350, true, "035-222-2261"),
            Shelter(6L, "Kegalle Divisional Secretariat", "Kegalle", 7.2500, 80.3480, 200, true, "035-222-2275"),
            // Kalutara district
            Shelter(7L, "Kalutara North School", "Kalutara", 6.5854, 79.9607, 400, true, "034-222-2100"),
            Shelter(8L, "Kalutara District Hospital", "Kalutara", 6.5840, 79.9590, 300, true, "034-222-2261"),
            Shelter(9L, "Kalutara Divisional Secretariat", "Kalutara", 6.5870, 79.9620, 200, true, "034-222-2275")
        )

        dao.insertAll(shelters)
        Log.d(TAG, "Seeded ${shelters.size} shelters")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Guidance steps
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun seedGuidanceSteps(db: SafePathDatabase) {
        val dao = db.guidanceStepDao()
        if (dao.getAllSteps().isNotEmpty()) {
            Log.d(TAG, "Guidance steps already seeded — skipping")
            return
        }

        val steps = mutableListOf<GuidanceStep>()

        // ── FLOOD / IMMINENT ─────────────────────────────────────────────────
        // Evacuation — leave immediately
        steps += guidanceStep("FLOOD", "IMMINENT", 1,
            "Secure all important documents in a waterproof bag (NIC, passport, deeds, medical records).")
        steps += guidanceStep("FLOOD", "IMMINENT", 2,
            "Turn off all electrical switches at the mains. Do not touch electrical items if water is present.")
        steps += guidanceStep("FLOOD", "IMMINENT", 3,
            "Leave immediately using the route shown on your screen. Do not delay.")
        steps += guidanceStep("FLOOD", "IMMINENT", 4,
            "Avoid low-lying roads, underpasses, and any area near rivers or streams.")
        steps += guidanceStep("FLOOD", "IMMINENT", 5,
            "Call family members now to confirm your location before losing signal.")

        // ── FLOOD / ALERT ────────────────────────────────────────────────────
        // Prepare to evacuate
        steps += guidanceStep("FLOOD", "ALERT", 1,
            "Prepare your emergency bag — documents, medications, phone charger, 3 days of food and water.")
        steps += guidanceStep("FLOOD", "ALERT", 2,
            "Identify your nearest shelter using the map on this screen.")
        steps += guidanceStep("FLOOD", "ALERT", 3,
            "Monitor official updates. Be ready to leave immediately if the alert escalates.")

        // ── FLOOD / ADVISORY ─────────────────────────────────────────────────
        // Awareness and preparation
        steps += guidanceStep("FLOOD", "ADVISORY", 1,
            "Check your emergency pack is complete using the checklist on this screen.")
        steps += guidanceStep("FLOOD", "ADVISORY", 2,
            "Confirm your family's evacuation meeting point.")
        steps += guidanceStep("FLOOD", "ADVISORY", 3,
            "Keep your phone charged and SafePath notifications enabled.")

        // ── LANDSLIDE / IMMINENT ─────────────────────────────────────────────
        // Evacuation — move away from slopes immediately
        steps += guidanceStep("LANDSLIDE", "IMMINENT", 1,
            "Move away from hillsides, slopes, and valley floors immediately.")
        steps += guidanceStep("LANDSLIDE", "IMMINENT", 2,
            "Follow the route shown on your screen — avoid roads through cuttings or embankments.")
        steps += guidanceStep("LANDSLIDE", "IMMINENT", 3,
            "Do not shelter under trees or near utility poles on slopes.")
        steps += guidanceStep("LANDSLIDE", "IMMINENT", 4,
            "If you hear rumbling or see the ground cracking — run uphill immediately, do not wait.")

        // ── LANDSLIDE / ALERT ────────────────────────────────────────────────
        // Monitor terrain, prepare to move
        steps += guidanceStep("LANDSLIDE", "ALERT", 1,
            "Monitor the terrain around your home — watch for new cracks, bulges, or unusual water seepage on slopes.")
        steps += guidanceStep("LANDSLIDE", "ALERT", 2,
            "Prepare to evacuate. Pack essential documents, medications, and emergency supplies.")
        steps += guidanceStep("LANDSLIDE", "ALERT", 3,
            "Avoid roads that pass through hillside cuttings or embankments until the alert is lifted.")

        // ── LANDSLIDE / ADVISORY ─────────────────────────────────────────────
        // Awareness — check surroundings
        steps += guidanceStep("LANDSLIDE", "ADVISORY", 1,
            "Check the slopes and drainage channels around your property for signs of instability or blockage.")
        steps += guidanceStep("LANDSLIDE", "ADVISORY", 2,
            "Clear blocked drains and gutters — water accumulation on slopes increases landslide risk.")
        steps += guidanceStep("LANDSLIDE", "ADVISORY", 3,
            "Inform your neighbours of the advisory and confirm your community's evacuation plan.")

        // ── CYCLONE / IMMINENT ───────────────────────────────────────────────
        // SHELTER-IN-PLACE — do NOT evacuate during a cyclone
        // This is intentionally different from flood/landslide guidance.
        steps += guidanceStep("CYCLONE", "IMMINENT", 1,
            "Stay indoors. Do NOT attempt to travel or evacuate during a cyclone — flying debris is lethal.")
        steps += guidanceStep("CYCLONE", "IMMINENT", 2,
            "Move to the most interior room on the lowest floor, away from all windows and glass doors.")
        steps += guidanceStep("CYCLONE", "IMMINENT", 3,
            "Secure or bring inside all loose outdoor items — furniture, tools, and containers become projectiles in high winds.")
        steps += guidanceStep("CYCLONE", "IMMINENT", 4,
            "Fill bathtubs and containers with clean water now — water supply may be disrupted after the storm.")
        steps += guidanceStep("CYCLONE", "IMMINENT", 5,
            "Keep your battery radio on for official updates. Do not go outside until authorities confirm the all-clear.")

        // ── CYCLONE / ALERT ──────────────────────────────────────────────────
        // Prepare shelter-in-place
        steps += guidanceStep("CYCLONE", "ALERT", 1,
            "Identify the safest room in your home — interior room, lowest floor, no windows.")
        steps += guidanceStep("CYCLONE", "ALERT", 2,
            "Secure loose items outside your home and reinforce doors and shutters if possible.")
        steps += guidanceStep("CYCLONE", "ALERT", 3,
            "Charge all devices, stock water and food for at least 3 days, and keep your emergency bag accessible.")

        // ── CYCLONE / ADVISORY ───────────────────────────────────────────────
        // Awareness — monitor and prepare
        steps += guidanceStep("CYCLONE", "ADVISORY", 1,
            "Monitor official weather updates from the Department of Meteorology via radio or SafePath.")
        steps += guidanceStep("CYCLONE", "ADVISORY", 2,
            "Review your household cyclone plan — identify your safe room and confirm your emergency contacts.")
        steps += guidanceStep("CYCLONE", "ADVISORY", 3,
            "Check your emergency supplies: water, food, medications, flashlight, and battery radio.")

        dao.insertAll(steps)
        Log.d(TAG, "Seeded ${steps.size} guidance steps across 9 disaster/severity combinations")
    }

    private fun guidanceStep(
        disasterType: String,
        severity: String,
        order: Int,
        textEn: String
    ) = GuidanceStep(
        disasterType = disasterType,
        severityLevel = severity,
        stepOrder = order,
        stepTextEn = textEn,
        stepTextSi = "...",   // Placeholder — replace with Sinhala translation
        stepTextTa = "..."    // Placeholder — replace with Tamil translation
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Pack items
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun seedPackItems(db: SafePathDatabase) {
        val dao = db.packItemDao()
        if (dao.getAllItems().isNotEmpty()) {
            Log.d(TAG, "Pack items already seeded — skipping")
            return
        }

        val items = mutableListOf<PackItem>()

        // ── FLOOD pack items ─────────────────────────────────────────────────
        // Priority 1 — Critical
        items += packItem("FLOOD", "NIC / Passport", 1)
        items += packItem("FLOOD", "Prescription medications (minimum 7-day supply)", 1)
        items += packItem("FLOOD", "Mobile phone and charger / power bank", 1)
        // Priority 2 — Important
        items += packItem("FLOOD", "3 litres of drinking water per person", 2)
        items += packItem("FLOOD", "Food for 3 days (non-perishable)", 2)
        items += packItem("FLOOD", "Flashlight and spare batteries", 2)
        // Priority 3 — Optional
        items += packItem("FLOOD", "Change of clothes and sturdy footwear", 3)
        items += packItem("FLOOD", "Basic first aid kit", 3)
        items += packItem("FLOOD", "Cash (small denominations)", 3)

        // ── LANDSLIDE pack items ─────────────────────────────────────────────
        // Priority 1 — Critical (sturdy footwear replaces water at top priority)
        items += packItem("LANDSLIDE", "Sturdy closed-toe footwear (essential for debris terrain)", 1)
        items += packItem("LANDSLIDE", "NIC / Passport", 1)
        items += packItem("LANDSLIDE", "Prescription medications (minimum 7-day supply)", 1)
        items += packItem("LANDSLIDE", "Mobile phone and charger / power bank", 1)
        // Priority 2 — Important
        items += packItem("LANDSLIDE", "3 litres of drinking water per person", 2)
        items += packItem("LANDSLIDE", "Food for 3 days (non-perishable)", 2)
        items += packItem("LANDSLIDE", "Flashlight and spare batteries", 2)
        // Priority 3 — Optional
        items += packItem("LANDSLIDE", "Change of clothes", 3)
        items += packItem("LANDSLIDE", "Basic first aid kit", 3)
        items += packItem("LANDSLIDE", "Cash (small denominations)", 3)

        // ── CYCLONE pack items ───────────────────────────────────────────────
        // Priority 1 — Critical (battery radio added for cyclone)
        items += packItem("CYCLONE", "Battery-powered radio (for official updates during power outage)", 1)
        items += packItem("CYCLONE", "NIC / Passport", 1)
        items += packItem("CYCLONE", "Prescription medications (minimum 7-day supply)", 1)
        items += packItem("CYCLONE", "Mobile phone and charger / power bank", 1)
        // Priority 2 — Important (duct tape for sealing windows/doors)
        items += packItem("CYCLONE", "Duct tape (for sealing windows and door gaps)", 2)
        items += packItem("CYCLONE", "3 litres of drinking water per person", 2)
        items += packItem("CYCLONE", "Food for 3 days (non-perishable)", 2)
        items += packItem("CYCLONE", "Flashlight and spare batteries", 2)
        // Priority 3 — Optional
        items += packItem("CYCLONE", "Change of clothes", 3)
        items += packItem("CYCLONE", "Basic first aid kit", 3)
        items += packItem("CYCLONE", "Cash (small denominations)", 3)

        dao.insertAll(items)
        Log.d(TAG, "Seeded ${items.size} pack items for FLOOD, LANDSLIDE, CYCLONE")
    }

    private fun packItem(disasterType: String, nameEn: String, priority: Int) =
        PackItem(disasterType = disasterType, itemNameEn = nameEn, priority = priority)
}
