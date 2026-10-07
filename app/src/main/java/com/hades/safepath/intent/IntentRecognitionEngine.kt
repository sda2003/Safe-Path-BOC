package com.hades.safepath.intent

import com.hades.safepath.data.model.DisasterType
import com.hades.safepath.data.model.SeverityLevel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntentRecognitionEngine @Inject constructor() {

    private val floodKeywords = listOf("flood", "water", "river", "overflow", "drowning", "rising", "ගංවතුර", "வெள்ளம்")
    private val landslideKeywords = listOf("landslide", "mud", "slip", "rock", "dirt", "bury", "නාය", "நிலச்சரிவு")
    private val fireKeywords = listOf("fire", "burn", "smoke", "blaze", "flame", "ගින්න", "தீ")
    private val cycloneKeywords = listOf("wind", "cyclone", "storm", "hurricane", "blow", "சுழல்காற்று")
    private val medicalKeywords = listOf("medical", "doctor", "hurt", "injury", "blood", "pain", "hospital", "sick", "වෛද්‍ය", "மருத்துவ")
    private val collapseKeywords = listOf("collapse", "trapped", "stuck", "building", "rubble", "earthquake", "quake", "සිරවී", "சிக்கி")
    
    private val criticalKeywords = listOf("help", "die", "trapped", "fast", "urgent", "sos", "save", "critical", "உதவி")

    fun analyzeText(input: String): IntentMatch {
        val lowerInput = input.lowercase()
        
        var maxConfidence = 0f
        var detectedType = DisasterType.UNKNOWN
        
        val floodScore = scoreKeywords(lowerInput, floodKeywords)
        if (floodScore > maxConfidence) {
            maxConfidence = floodScore
            detectedType = DisasterType.FLOOD
        }
        
        val landslideScore = scoreKeywords(lowerInput, landslideKeywords)
        if (landslideScore > maxConfidence) {
            maxConfidence = landslideScore
            detectedType = DisasterType.LANDSLIDE
        }
        
        val fireScore = scoreKeywords(lowerInput, fireKeywords)
        if (fireScore > maxConfidence) {
            maxConfidence = fireScore
            detectedType = DisasterType.FIRE
        }
        
        val medicalScore = scoreKeywords(lowerInput, medicalKeywords)
        if (medicalScore > maxConfidence) {
            maxConfidence = medicalScore
            detectedType = DisasterType.MEDICAL
        }
        
        val collapseScore = scoreKeywords(lowerInput, collapseKeywords)
        if (collapseScore > maxConfidence) {
            maxConfidence = collapseScore
            detectedType = DisasterType.STRUCTURAL_COLLAPSE
        }
        
        // Severity analysis
        val criticalScore = scoreKeywords(lowerInput, criticalKeywords)
        var severity = SeverityLevel.ADVISORY
        var isEmergency = false
        
        if (criticalScore > 0 || maxConfidence > 1.5f) {
            severity = SeverityLevel.IMMINENT
            isEmergency = true
        } else if (maxConfidence > 0.5f) {
            severity = SeverityLevel.ALERT
        }
        
        if (maxConfidence == 0f) {
            detectedType = DisasterType.UNKNOWN
            severity = SeverityLevel.UNKNOWN
        }
        
        // Cap confidence at 1.0f
        val finalConfidence = if (maxConfidence > 1.0f) 1.0f else maxConfidence

        return IntentMatch(detectedType, severity, finalConfidence, isEmergency)
    }
    
    private fun scoreKeywords(input: String, keywords: List<String>): Float {
        var score = 0f
        for (word in keywords) {
            if (input.contains(word)) {
                score += 0.4f // Each matching keyword increases confidence
            }
        }
        return score
    }
}
