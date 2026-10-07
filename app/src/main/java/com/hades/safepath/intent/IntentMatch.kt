package com.hades.safepath.intent

import com.hades.safepath.data.model.DisasterType
import com.hades.safepath.data.model.SeverityLevel

data class IntentMatch(
    val type: DisasterType,
    val severity: SeverityLevel,
    val confidence: Float,
    val isEmergency: Boolean
)
