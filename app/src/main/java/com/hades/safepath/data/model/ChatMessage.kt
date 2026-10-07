package com.hades.safepath.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val id: String = "",
    @SerialName("room_id") val roomId: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("sender_name") val senderName: String = "Anonymous",
    @SerialName("is_anonymous") val isAnonymous: Boolean = false,
    val message: String = "",
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("created_at") val createdAt: String = ""
)
