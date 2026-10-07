package com.hades.safepath.ai

/**
 * Built-in emergency Q&A used for suggestion chips and offline / no-API fallbacks.
 * Answers are practical Sri Lanka–focused guidance (not official warnings).
 */
object AiQaFaqCatalog {

    data class FaqEntry(
        val question: String,
        val topic: String,
        val answer: String
    )

    val entries: List<FaqEntry> = listOf(
        FaqEntry(
            question = "What should I do if flood water is rising near my house?",
            topic = "flood",
            answer = """
                1. Move people and pets to higher floors or higher ground immediately.
                2. Switch off electricity at the main if you can do so safely — never touch switches with wet hands.
                3. Take your go-bag, documents, medicines, phone, and drinking water.
                4. Avoid walking or driving through moving water; 15 cm can knock you down.
                5. Follow local Disaster Management Centre / police instructions and do not return until authorities say it is safe.
            """.trimIndent()
        ),
        FaqEntry(
            question = "How do I prepare a go-bag for a family with kids?",
            topic = "pack",
            answer = """
                Pack one bag you can carry in under 2 minutes:
                • Water (3 L per person for 1 day) + dry snacks
                • Child medicines, fever syrup, ORS, basic first-aid
                • Nappies/wipes, spare clothes, small blanket, comfort toy
                • Power bank, torch, whistle, copies of IDs & vaccination cards in a zip bag
                • Cash in small notes, face masks, hand sanitizer
                Label the bag and keep it by the exit. Check expiry dates every 3 months.
            """.trimIndent()
        ),
        FaqEntry(
            question = "What are the first steps after a landslide warning?",
            topic = "landslide",
            answer = """
                1. Leave slope-side homes immediately if officials issue an ALERT or IMMINENT warning.
                2. Move to designated safe centres (schools/temples on stable ground) — do not wait for daylight if told to go.
                3. Avoid roads cut into hillsides, stream beds, and areas with new cracks or tilting trees.
                4. Take only essentials; help elderly/children first.
                5. Stay away until authorities clear the area — secondary slides are common after rain.
            """.trimIndent()
        ),
        FaqEntry(
            question = "Where should I go during a tsunami alert in Sri Lanka?",
            topic = "tsunami",
            answer = """
                1. Move inland and uphill immediately — do not wait to see the wave.
                2. Follow official sirens / DMC / police guidance for your coastal district.
                3. Prefer solid high ground or upper floors of sturdy multi-storey buildings if you cannot reach inland fast.
                4. Stay away from beaches, lagoons, and river mouths for several hours after the first wave.
                5. Return only when officials announce the all-clear. Keep listening to radio updates.
            """.trimIndent()
        ),
        FaqEntry(
            question = "How can I purify drinking water after a disaster?",
            topic = "general",
            answer = """
                If bottled water is unavailable:
                1. Clear water: boil rolling 1 full minute (3 minutes at high elevation), then cool covered.
                2. Or use household bleach: 2 drops of unscented 5% bleach per 1 L clear water; wait 30 minutes.
                3. Cloudy water: filter through clean cloth first, then boil or treat.
                4. Do not drink flood water, well water near toilets, or water with chemical smell.
                5. Use ORS if someone has diarrhoea; seek medical help for infants and elderly.
            """.trimIndent()
        ),
        FaqEntry(
            question = "What should I do during a cyclone or severe storm?",
            topic = "cyclone",
            answer = """
                1. Shelter indoors — do NOT evacuate during peak winds unless ordered for storm surge.
                2. Stay in an interior room away from windows; close shutters/curtains.
                3. Unplug appliances; keep torch, radio, and charged power bank ready.
                4. Avoid coastal roads and flooded underpasses after the eye passes — winds return.
                5. After the storm, watch for downed power lines and damaged roofs before going outside.
            """.trimIndent()
        ),
        FaqEntry(
            question = "What first aid should I give for heavy bleeding?",
            topic = "firstaid",
            answer = """
                1. Call emergency services / get help immediately.
                2. Put firm direct pressure on the wound with a clean cloth — do not keep peeking.
                3. If blood soaks through, add another cloth on top; keep pressing.
                4. Raise the injured limb if no fracture is suspected.
                5. Use a tourniquet only for life-threatening limb bleeding when pressure fails, and note the time.
                Do not remove embedded objects — pad around them and press.
            """.trimIndent()
        ),
        FaqEntry(
            question = "How do I stay safe in an earthquake?",
            topic = "general",
            answer = """
                If indoors: DROP, COVER, HOLD ON under a sturdy table; stay away from windows and tall furniture.
                If outdoors: move to open space away from buildings, trees, and power lines.
                After shaking stops: check for injuries and gas smells; evacuate if the building is damaged.
                Expect aftershocks. Keep shoes and a torch by the bed. Follow official updates before re-entering damaged homes.
            """.trimIndent()
        ),
        FaqEntry(
            question = "What should I pack for elderly family members?",
            topic = "pack",
            answer = """
                Add to the household go-bag:
                • 7 days of prescription medicines + list of doses and doctor contacts
                • Spare glasses/hearing-aid batteries, walking aid if used
                • Easy-to-eat food, water, adult continence supplies if needed
                • Warm layer, whistle, phone with large-font ICE contacts
                • Copies of clinic cards and allergy notes
                Practice the exit route with them before monsoon season.
            """.trimIndent()
        ),
        FaqEntry(
            question = "How do I know if my area is at landslide risk?",
            topic = "landslide",
            answer = """
                Higher risk signs in hill country (e.g. Ratnapura, Kegalle, Nuwara Eliya):
                • New cracks in soil, roads, or house walls
                • Tilting trees/poles, sudden muddy water in streams
                • Springs appearing on slopes after heavy rain
                If you see these during prolonged rain, leave early for a safe centre.
                Always treat official landslide warnings as urgent — do not wait for visible movement.
            """.trimIndent()
        ),
        FaqEntry(
            question = "What should I do if someone is in flood water and drowning?",
            topic = "flood",
            answer = """
                Reach or throw — don't go if you are not a trained rescuer.
                1. Call for help loudly; alert emergency services.
                2. Throw a rope, branch, or floating object; pull from stable ground.
                3. Avoid entering fast water — rescuers often become victims.
                4. Once out: check breathing; start CPR if trained and needed; keep them warm.
                5. Seek medical care even if they seem fine (water inhalation risk).
            """.trimIndent()
        ),
        FaqEntry(
            question = "How long should emergency food and water last?",
            topic = "pack",
            answer = """
                Aim for at least 72 hours per person:
                • Water: about 3 litres per person per day (drinking + basic hygiene)
                • Food: ready-to-eat items that need no cooking (biscuits, canned fish, energy bars)
                • Special needs: baby formula, elderly soft foods, pet food
                Rotate stock every 6 months. Store in a waterproof container above flood level.
            """.trimIndent()
        ),
        FaqEntry(
            question = "What should I do if there is a house fire?",
            topic = "general",
            answer = """
                1. Alert everyone; get out fast — do not stop for belongings.
                2. Stay low under smoke; feel doors with the back of your hand before opening.
                3. Close doors behind you to slow the fire.
                4. Meet at a fixed outdoor point; call the fire service.
                5. Never go back inside. If clothes catch fire: stop, drop, roll.
            """.trimIndent()
        ),
        FaqEntry(
            question = "How can I contact family when the network is down?",
            topic = "general",
            answer = """
                1. Use SafePath I'm Safe when you have any signal — it queues offline and sends later.
                2. Try SMS (often works when calls fail); keep messages short.
                3. Agree on an out-of-town contact everyone can reach.
                4. Use Mesh Notes nearby if Bluetooth/Nearby peers are available.
                5. Go to an official safe centre — staff often help reconnect families.
            """.trimIndent()
        ),
        FaqEntry(
            question = "What documents should I keep waterproof?",
            topic = "pack",
            answer = """
                Keep sealed copies (zip bag / waterproof pouch) of:
                • National ID, birth certificates, passports
                • Property deeds / rental papers, insurance
                • Bank and clinic cards, vaccination records
                • Emergency contact list written on paper
                Store one set in the go-bag and another with a trusted relative if possible.
            """.trimIndent()
        ),
        FaqEntry(
            question = "What should pregnant women do in an evacuation?",
            topic = "general",
            answer = """
                1. Leave early — do not wait until roads flood.
                2. Pack antenatal card, medicines, sanitary supplies, and a spare set of clothes.
                3. Sit where you can rest; avoid wading deep water.
                4. Tell shelter staff you are pregnant so they can prioritise care.
                5. Seek clinic help for pain, bleeding, reduced baby movements, or contractions.
            """.trimIndent()
        )
    )

    val suggestedQuestions: List<String> = entries.map { it.question }

    fun findAnswer(question: String): FaqEntry? {
        val key = normalize(question)
        if (key.isEmpty()) return null

        entries.firstOrNull { normalize(it.question) == key }?.let { return it }

        // Loose match: user typed a shorter related question
        val scored = entries.map { entry ->
            val eq = normalize(entry.question)
            val overlap = key.split(" ").count { token -> token.length > 3 && token in eq }
            entry to overlap
        }.filter { it.second >= 2 }
            .maxByOrNull { it.second }

        return scored?.first
    }

    fun normalize(question: String): String =
        question.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
