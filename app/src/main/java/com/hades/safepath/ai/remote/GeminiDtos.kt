package com.hades.safepath.ai.remote

import com.google.gson.annotations.SerializedName

data class GeminiRequest(
    val contents: List<GeminiContent>
)

data class GeminiContent(
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

data class GeminiCandidate(
    val content: GeminiContent?
)

data class GeminiParsedPack(
    val checklist: List<GeminiChecklistItem> = emptyList(),
    val tips: List<String> = emptyList()
)

data class GeminiChecklistItem(
    val item: String,
    val priority: Int = 2
)
