package com.hades.safepath.data.model

data class DisasterEvent(
    val disasterType: DisasterType,
    val severity: SeverityLevel,
    val rawCode: Int,
    val receivedAt: Long = System.currentTimeMillis()
)
