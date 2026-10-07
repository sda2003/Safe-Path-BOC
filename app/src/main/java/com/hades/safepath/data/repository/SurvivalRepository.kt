package com.hades.safepath.data.repository

import com.hades.safepath.data.model.SurvivalTopic
import com.hades.safepath.utils.Constants
import javax.inject.Inject

class SurvivalRepository @Inject constructor() {
    fun getTopics(): List<SurvivalTopic> = listOf(

        // ─── FLOOD ───────────────────────────────────────────────
        SurvivalTopic(
            id = "flood_signal",
            category = "flood",
            title = "How to Signal for Rescue",
            iconRes = com.hades.safepath.R.drawable.how_to_signal_for_rescue,
            colorHex = "#3B82F6",
            summary = "When trapped by floodwater, signaling correctly can save your life.",
            steps = listOf(
                "Move to the highest point of your location — roof, top floor, or elevated ground.",
                "Use a mirror or any reflective object to flash sunlight toward helicopters or boats.",
                "Blow a whistle in groups of 3 blasts (international distress signal). Pause. Repeat.",
                "Wave a brightly colored cloth — red, orange, or yellow — in a wide arc.",
                "At night, use a flashlight and flash SOS: 3 short, 3 long, 3 short blinks.",
                "If you have a phone, call 119 (Sri Lanka DMC). Even with no signal, dial 112.",
                "Spell HELP in large letters using rocks, clothes, or anything visible from above.",
                "Stay in place once you signal — rescuers return to the last known position."
            )
        ),

        SurvivalTopic(
            id = "flood_raft",
            category = "flood",
            title = "Building a Temporary Raft",
            iconRes = com.hades.safepath.R.drawable.building_a_temporary_raft,
            colorHex = "#3B82F6",
            summary = "If you must cross floodwater, a makeshift raft provides critical buoyancy.",
            steps = listOf(
                "Collect 6–10 dry logs or thick bamboo poles of similar length (2–3 meters).",
                "Lay them side by side and lash them tightly with rope, belts, or torn clothing strips.",
                "Tie at least 3 cross-pieces perpendicular to the main logs to hold the structure together.",
                "Test buoyancy in shallow water before putting full weight on it.",
                "Use a long stick or plank as a paddle or pole to push off the ground.",
                "Tie yourself loosely to the raft with a rope in case you fall off.",
                "Never enter fast-moving floodwater — use a raft only in slow or still water.",
                "Steer toward the bank by paddling, not fighting the current directly."
            )
        ),

        SurvivalTopic(
            id = "flood_water",
            category = "flood",
            title = "Finding Clean Water",
            iconRes = com.hades.safepath.R.drawable.finding_clean_water,
            colorHex = "#3B82F6",
            summary = "Floodwater is contaminated. Here is how to find or purify safe drinking water.",
            steps = listOf(
                "Never drink floodwater directly — it contains sewage, chemicals, and bacteria.",
                "Collect rainwater in any clean container — it is safe to drink without treatment.",
                "Boil any found water for at least 1 minute (3 minutes at high altitude) before drinking.",
                "Solar disinfection (SODIS): fill a clear plastic bottle, leave in direct sunlight for 6 hours.",
                "Make a basic filter: layer cloth, then charcoal (from burnt wood), then sand, then gravel.",
                "Look for water seeping from rocks or hillsides — it is often cleaner than surface water.",
                "Dig 1 meter away from the riverbank — let water seep in and settle before drinking.",
                "Green plants indicate nearby water underground — dig at the base of vegetation."
            )
        ),

        // ─── FIRE ────────────────────────────────────────────────
        SurvivalTopic(
            id = "fire_start",
            category = "fire",
            title = "Starting a Fire Without Matches",
            iconRes = com.hades.safepath.R.drawable.starting_a_fire_without_matches,
            colorHex = "#F97316",
            summary = "Fire provides warmth, signals rescuers, purifies water, and prevents hypothermia.",
            steps = listOf(
                "BOW DRILL METHOD: Carve a flat baseboard from dry softwood. Make a small depression.",
                "Cut a notch next to the depression — this is where the ember forms.",
                "Wrap a string or shoelace around a straight spindle stick and pull the bow back and forth.",
                "Apply downward pressure with your hand block. Spin fast until you see smoke and ember.",
                "Transfer the ember into a ball of dry grass or coconut fiber and blow gently until it flames.",
                "BATTERY + STEEL WOOL: Pull steel wool slightly apart. Touch both battery terminals to it.",
                "FLINT STRIKE: Strike flint sharply against the back of a steel knife blade downward.",
                "Always build your fire in stages: tinder → kindling → fuel logs. Never rush the tinder stage."
            )
        ),

        SurvivalTopic(
            id = "fire_signal",
            category = "fire",
            title = "Using Fire to Signal Rescue",
            iconRes = com.hades.safepath.R.drawable.using_fire_to_signal_rescue,
            colorHex = "#F97316",
            summary = "Three fires in a triangle is the universal distress signal from air and ground.",
            steps = listOf(
                "Build 3 fires in a triangle shape, each 25–30 meters apart — visible from the air.",
                "Add green leaves, rubber, or plastic to create thick BLACK smoke (visible in daytime).",
                "Add dry grass and birch bark to create WHITE smoke (visible against dark forest/mountain).",
                "Keep fires going continuously — a fire that goes out is not a signal.",
                "If rescue aircraft approaches, add as much smoke material as possible immediately.",
                "At night, let the flames be as high as possible — smoke matters less in darkness.",
                "A signal mirror reflects sunlight up to 16 km — combine with fire for dual signaling."
            )
        ),

        // ─── EARTHQUAKE ──────────────────────────────────────────
        SurvivalTopic(
            id = "quake_during",
            category = "earthquake",
            title = "During an Earthquake",
            iconRes = com.hades.safepath.R.drawable.during_an_earthquake,
            colorHex = "#8B5CF6",
            summary = "The right actions in the first 30 seconds save lives.",
            steps = listOf(
                "DROP to your hands and knees immediately — this prevents being knocked over.",
                "COVER your head and neck with your arms. Get under a sturdy desk or table if nearby.",
                "HOLD ON to shelter and be ready to move with it until shaking stops.",
                "Stay away from windows, outside walls, and anything that can fall.",
                "If in bed, stay there. Hold a pillow over your head.",
                "If outdoors, move away from buildings, trees, and power lines. Drop to the ground.",
                "If in a car, pull over away from bridges and overpasses. Stay inside with seatbelt on.",
                "Do NOT run outside during shaking — most injuries happen from falling debris near exits."
            )
        ),

        SurvivalTopic(
            id = "quake_after",
            category = "earthquake",
            title = "After an Earthquake — Trapped Survival",
            iconRes = com.hades.safepath.R.drawable.after_an_earthquake_trapped_survival,
            colorHex = "#8B5CF6",
            summary = "If trapped under debris, your actions determine survival.",
            steps = listOf(
                "Do NOT light a match or lighter — gas leaks may be present.",
                "Cover your nose and mouth with clothing to filter dust.",
                "Tap on a pipe or wall rhythmically so rescuers can hear you — voices exhaust you faster.",
                "Conserve energy — do not shout continuously. Call out only when you hear rescuers.",
                "If possible, move debris away from your face to create breathing space.",
                "Avoid moving large debris that may be supporting the structure above you.",
                "Check yourself and others for injuries — apply pressure to bleeding wounds.",
                "If you get free, check for gas smell before turning on any switches or flames."
            )
        ),

        // ─── SHELTER ─────────────────────────────────────────────
        SurvivalTopic(
            id = "shelter_build",
            category = "general",
            title = "Building an Emergency Shelter",
            iconRes = com.hades.safepath.R.drawable.building_an_emergency_shelter,
            colorHex = "#22C55E",
            summary = "Exposure kills faster than hunger or thirst. Build shelter first.",
            steps = listOf(
                "Choose a site on elevated, dry ground away from flood zones and dead trees.",
                "LEAN-TO: Prop a 2m branch between two trees at shoulder height. Lean branches against it at 45°.",
                "Layer leaves, ferns, and bark over the angled branches — at least 30cm thick to repel rain.",
                "DEBRIS HUT: Make a ridge pole from a long branch. Pile leaves and debris 1m thick on both sides.",
                "Insulate the ground with at least 15cm of dry leaves or grass — ground steals body heat fast.",
                "Make the shelter just big enough for your body — smaller space retains heat better.",
                "Face the entrance away from prevailing wind direction.",
                "Test for rain by pouring water on the roof — adjust coverage before nightfall."
            )
        ),

        // ─── FIRST AID ────────────────────────────────────────────
        SurvivalTopic(
            id = "firstaid_cpr",
            category = "firstaid",
            title = "CPR — Cardiopulmonary Resuscitation",
            iconRes = com.hades.safepath.R.drawable.cpr_cardiopulmonary_resuscitation,
            colorHex = "#EF4444",
            summary = "CPR can double survival chances for cardiac arrest. Learn this now.",
            steps = listOf(
                "Check the scene is safe. Tap the person's shoulder and shout 'Are you okay?'",
                "Call 110 (ambulance Sri Lanka) immediately or ask someone nearby to call.",
                "Lay them on their back on a firm surface.",
                "Heel of your hand on center of chest (lower half of breastbone). Second hand on top.",
                "Push DOWN hard and fast — at least 5cm deep, 100–120 compressions per minute.",
                "Allow chest to fully rise between compressions — do not lean on the chest.",
                "After 30 compressions: tilt head back, lift chin, give 2 rescue breaths (1 second each).",
                "Continue 30:2 cycle until ambulance arrives, the person recovers, or you are too exhausted."
            )
        )
    )
}
