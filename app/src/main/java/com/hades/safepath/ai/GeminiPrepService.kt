package com.hades.safepath.ai

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.hades.safepath.BuildConfig
import com.hades.safepath.ai.remote.GeminiApi
import com.hades.safepath.ai.remote.GeminiContent
import com.hades.safepath.ai.remote.GeminiParsedPack
import com.hades.safepath.ai.remote.GeminiPart
import com.hades.safepath.ai.remote.GeminiRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiPrepService @Inject constructor(
    private val geminiApi: GeminiApi,
    private val gson: Gson
) {

    suspend fun generatePersonalizedPack(profile: HouseholdProfile): Result<PersonalizedPackResult> {
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            return Result.failure(IllegalStateException("Gemini API key is not configured."))
        }

        return runCatching {
            val prompt = buildPrompt(profile)
            val response = geminiApi.generateContent(
                model = BuildConfig.GEMINI_MODEL,
                apiKey = BuildConfig.GEMINI_API_KEY,
                request = GeminiRequest(
                    contents = listOf(
                        GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                    )
                )
            )

            val rawText = response.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text
                ?: throw IllegalStateException("Empty response from Gemini.")

            val parsed = parseGeminiJson(rawText)
            PersonalizedPackResult(
                checklist = parsed.checklist.map {
                    ChecklistItemDto(item = it.item, priority = it.priority.coerceIn(1, 3))
                },
                tips = parsed.tips.take(5)
            )
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { error ->
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Gemini generation failed: ${error.message}")
                }
                Result.failure(error)
            }
        )
    }

    private fun buildPrompt(profile: HouseholdProfile): String = """
        You are an emergency preparedness assistant for households in Sri Lanka.
        Create a personalized emergency pack checklist and survival tips.

        Household profile:
        - Household size: ${profile.householdSize}
        - Children: ${if (profile.hasKids) "yes, ages ${profile.kidsAges}" else "no"}
        - Elderly members: ${if (profile.hasElderly) "yes" else "no"}
        - Pets: ${if (profile.hasPets) profile.petType else "no"}
        - Medical conditions: ${profile.medicalConditions.ifBlank { "none" }}

        Respond with ONLY valid JSON (no markdown, no code fences):
        {
          "checklist": [{"item": "item name", "priority": 1}],
          "tips": ["tip one", "tip two"]
        }

        Rules:
        - checklist: 8-12 practical items for floods/landslides/cyclones
        - priority: 1=critical, 2=important, 3=optional
        - tips: 3-5 short actionable survival tips tailored to this household
    """.trimIndent()

    private fun parseGeminiJson(raw: String): GeminiParsedPack {
        val json = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        return try {
            gson.fromJson(json, GeminiParsedPack::class.java)
                ?: throw JsonSyntaxException("Null parse result")
        } catch (e: JsonSyntaxException) {
            throw IllegalStateException("Could not parse AI response.", e)
        }
    }

    companion object {
        private const val TAG = "GeminiPrepService"
    }
}
