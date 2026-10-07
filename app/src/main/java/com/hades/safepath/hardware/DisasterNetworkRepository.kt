package com.hades.safepath.hardware

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction layer for Long-Range (LoRa) disaster mesh networks.
 * Allows the Android device to route SOS messages and receive hazard telemetry
 * over a resilient LoRa P2P network if connected via Bluetooth to a LoRa bridge.
 */
interface DisasterNetworkRepository {
    
    /**
     * Checks if the Android device is successfully bridged to a LoRa module.
     */
    fun isLoRaBridgeConnected(): Flow<Boolean>

    /**
     * Broadcasts an SOS or intent message over the LoRa network.
     */
    suspend fun broadcastEmergencySignal(payload: ByteArray): Boolean
    
    /**
     * Observes incoming mesh network messages (e.g., from other survivor nodes or Hazard Nodes).
     */
    fun observeIncomingPackets(): Flow<ByteArray>
}
