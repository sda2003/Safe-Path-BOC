package com.hades.safepath.hardware

import com.hades.safepath.data.entity.HazardNodeEntity
import com.hades.safepath.data.repository.HazardNodeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class MockHazardNodeSimulator @Inject constructor(
    private val hazardNodeRepository: HazardNodeRepository
) {
    private var simulatorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    
    // Fake nodes placed around Ratnapura area
    private val mockNodes = listOf(
        HazardNodeEntity(
            nodeId = "MOCK_NODE_001",
            latitude = 6.6828,
            longitude = 80.3992,
            temperature = 28.5,
            humidity = 75.0,
            pressure = 1012.0,
            rainfall = 12.0,
            waterLevel = 0.5,
            soilMoisture = 40.0,
            battery = 98,
            riskLevel = "LOW",
            timestamp = System.currentTimeMillis(),
            cameraAvailable = false
        ),
        HazardNodeEntity(
            nodeId = "MOCK_NODE_002",
            latitude = 6.6900,
            longitude = 80.4010,
            temperature = 26.0,
            humidity = 90.0,
            pressure = 1005.0,
            rainfall = 45.0,
            waterLevel = 2.1,
            soilMoisture = 85.0,
            battery = 85,
            riskLevel = "HIGH",
            timestamp = System.currentTimeMillis(),
            cameraAvailable = true
        ),
        HazardNodeEntity(
            nodeId = "MOCK_NODE_003",
            latitude = 6.6850,
            longitude = 80.3900,
            temperature = 27.2,
            humidity = 88.0,
            pressure = 1002.0,
            rainfall = 80.0,
            waterLevel = 0.1,
            soilMoisture = 95.0,
            battery = 45,
            riskLevel = "CRITICAL",
            timestamp = System.currentTimeMillis(),
            cameraAvailable = false
        )
    )

    fun startSimulation() {
        if (simulatorJob?.isActive == true) return

        simulatorJob = scope.launch {
            while (true) {
                for (node in mockNodes) {
                    val updatedNode = node.copy(
                        battery = (node.battery - 1).coerceAtLeast(0),
                        waterLevel = node.waterLevel + (Random.nextDouble() * 0.2 - 0.1),
                        soilMoisture = (node.soilMoisture + (Random.nextDouble() * 2.0 - 1.0)).coerceIn(0.0, 100.0),
                        timestamp = System.currentTimeMillis()
                    )
                    hazardNodeRepository.insertOrUpdateNode(updatedNode)
                }
                delay(5000) // Update every 5 seconds
            }
        }
    }

    fun stopSimulation() {
        simulatorJob?.cancel()
        simulatorJob = null
    }
}
