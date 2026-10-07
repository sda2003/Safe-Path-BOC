package com.hades.safepath.ai

import android.util.Log
import com.hades.safepath.BuildConfig
import com.hades.safepath.ai.remote.GeminiApi
import com.hades.safepath.ai.remote.GeminiContent
import com.hades.safepath.ai.remote.GeminiPart
import com.hades.safepath.ai.remote.GeminiRequest
import javax.inject.Inject
import javax.inject.Singleton

data class QaAnswer(
    val answer: String,
    val topic: String
)

@Singleton
class GeminiQaService @Inject constructor(
    private val geminiApi: GeminiApi
) {

    suspend fun ask(question: String, disasterHint: String? = null): Result<QaAnswer> {
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            return Result.failure(IllegalStateException("Gemini API key is not configured."))
        }
        val trimmed = question.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter a question."))
        }

        return runCatching {
            val prompt = buildPrompt(trimmed, disasterHint)
            val response = geminiApi.generateContent(
                model = BuildConfig.GEMINI_MODEL,
                apiKey = BuildConfig.GEMINI_API_KEY,
                request = GeminiRequest(
                    contents = listOf(
                        GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                    )
                )
            )
            val raw = response.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text
                ?.trim()
                ?: throw IllegalStateException("Empty response from Gemini.")

            val topic = extractTopic(raw) ?: guessTopic(trimmed)
            val answer = stripTopicLine(raw).ifBlank { raw }
            QaAnswer(answer = answer.take(MAX_ANSWER_CHARS), topic = topic)
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { error ->
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "QA failed: ${error.message}")
                }
                Result.failure(error)
            }
        )
    }

    private fun buildPrompt(question: String, disasterHint: String?): String = """
        You are SafePath, an emergency preparedness assistant for people in Sri Lanka.
        Answer clearly and calmly for someone who may be in danger or preparing for floods,
        landslides, cyclones, or tsunamis.

        ${if (!disasterHint.isNullOrBlank()) "Context: $disasterHint" else ""}

        Question: $question

        Rules:
        - Give practical steps first (short bullets or numbered list).
        - Keep answer under 180 words.
        - Do not invent official warnings; remind users to follow local authorities.
        - If medical emergency, say to call emergency services immediately.
        - First line MUST be exactly: TOPIC: <one of flood|landslide|cyclone|tsunami|pack|firstaid|general>
        - Then the answer on the following lines (no markdown fences).
    """.trimIndent()

    private fun extractTopic(raw: String): String? {
        val first = raw.lineSequence().firstOrNull()?.trim().orEmpty()
        val match = Regex("""(?i)^TOPIC:\s*(\w+)""").find(first) ?: return null
        return normalizeTopic(match.groupValues[1])
    }

    private fun stripTopicLine(raw: String): String {
        val lines = raw.lines()
        if (lines.isEmpty()) return raw
        return if (lines.first().trim().startsWith("TOPIC:", ignoreCase = true)) {
            lines.drop(1).joinToString("\n").trim()
        } else {
            raw.trim()
        }
    }

    private fun guessTopic(question: String): String {
        val q = question.lowercase()
        return when {
            "flood" in q || "water" in q -> "flood"
            "landslide" in q || "mud" in q -> "landslide"
            "cyclone" in q || "storm" in q || "wind" in q -> "cyclone"
            "tsunami" in q || "wave" in q -> "tsunami"
            "pack" in q || "kit" in q || "bag" in q -> "pack"
            "cpr" in q || "bleed" in q || "first aid" in q || "injury" in q -> "firstaid"
            else -> "general"
        }
    }

    private fun normalizeTopic(raw: String): String {
        val t = raw.lowercase()
        return when (t) {
            "flood", "landslide", "cyclone", "tsunami", "pack", "firstaid", "general" -> t
            "first_aid", "first-aid", "medical" -> "firstaid"
            else -> "general"
        }
    }

    companion object {
        private const val TAG = "GeminiQaService"
        private const val MAX_ANSWER_CHARS = 1200
    }
}
