package com.hades.safepath.ai

import com.hades.safepath.BuildConfig
import com.hades.safepath.data.dao.AiQaCacheDao
import com.hades.safepath.data.entity.AiQaCacheEntity
import com.hades.safepath.util.NetworkMonitor
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

sealed class AiQaResult {
    data class Success(
        val question: String,
        val answer: String,
        val topic: String,
        val fromCache: Boolean
    ) : AiQaResult()

    data class Error(val message: String) : AiQaResult()
}

@Singleton
class AiQaRepository @Inject constructor(
    private val geminiQaService: GeminiQaService,
    private val cacheDao: AiQaCacheDao,
    private val networkMonitor: NetworkMonitor
) {

    fun observeHistory(): Flow<List<AiQaCacheEntity>> = cacheDao.observeRecent(40)

    val suggestedQuestions: List<String> = AiQaFaqCatalog.suggestedQuestions

    suspend fun ask(question: String, disasterHint: String? = null): AiQaResult {
        val trimmed = question.trim()
        if (trimmed.length < 3) {
            return AiQaResult.Error("Ask a short emergency question.")
        }

        val key = normalizeKey(trimmed)
        cacheDao.getByKey(key)?.let { cached ->
            return AiQaResult.Success(
                question = cached.question,
                answer = cached.answer,
                topic = cached.topic,
                fromCache = true
            )
        }

        // Built-in FAQ — works offline and without a Gemini key
        AiQaFaqCatalog.findAnswer(trimmed)?.let { faq ->
            cacheDao.upsert(
                AiQaCacheEntity(
                    questionKey = key,
                    question = trimmed,
                    answer = faq.answer,
                    topic = faq.topic,
                    fromNetwork = false
                )
            )
            return AiQaResult.Success(
                question = trimmed,
                answer = faq.answer,
                topic = faq.topic,
                fromCache = false
            )
        }

        val geminiReady = BuildConfig.GEMINI_API_KEY.isNotBlank() &&
            !BuildConfig.GEMINI_API_KEY.equals("placeholder", ignoreCase = true) &&
            !BuildConfig.GEMINI_API_KEY.contains("your_gemini", ignoreCase = true)

        if (!networkMonitor.isOnline() || !geminiReady) {
            return AiQaResult.Error(
                "No saved answer for that question yet. Try a suggested chip below, or add a Gemini API key and go online for custom questions."
            )
        }

        return geminiQaService.ask(trimmed, disasterHint).fold(
            onSuccess = { qa ->
                cacheDao.upsert(
                    AiQaCacheEntity(
                        questionKey = key,
                        question = trimmed,
                        answer = qa.answer,
                        topic = qa.topic,
                        fromNetwork = true
                    )
                )
                cacheDao.deleteOlderThan(System.currentTimeMillis() - CACHE_TTL_MS)
                AiQaResult.Success(
                    question = trimmed,
                    answer = qa.answer,
                    topic = qa.topic,
                    fromCache = false
                )
            },
            onFailure = { e ->
                // Last resort: fuzzy FAQ if Gemini fails
                AiQaFaqCatalog.findAnswer(trimmed)?.let { faq ->
                    return@fold AiQaResult.Success(
                        question = trimmed,
                        answer = faq.answer,
                        topic = faq.topic,
                        fromCache = false
                    )
                }
                AiQaResult.Error(e.message ?: "AI could not answer right now.")
            }
        )
    }

    private fun normalizeKey(question: String): String {
        val normalized = AiQaFaqCatalog.normalize(question)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    companion object {
        private const val CACHE_TTL_MS = 30L * 24L * 60L * 60L * 1000L
    }
}
