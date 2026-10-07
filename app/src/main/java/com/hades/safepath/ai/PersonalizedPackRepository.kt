package com.hades.safepath.ai

import com.hades.safepath.data.dao.GuidanceStepDao
import com.hades.safepath.data.dao.PackItemDao
import com.hades.safepath.data.dao.PersonalizedPackMetaDao
import com.hades.safepath.data.entity.GuidanceStep
import com.hades.safepath.data.entity.PackItem
import com.hades.safepath.data.entity.PersonalizedPackConstants
import com.hades.safepath.data.entity.PersonalizedPackMetaEntity
import com.hades.safepath.utils.PackData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class GeneratePackOutcome {
    data class Success(val usedDefaultFallback: Boolean) : GeneratePackOutcome()
    data object AlreadyExists : GeneratePackOutcome()
    data class Error(val message: String) : GeneratePackOutcome()
}

@Singleton
class PersonalizedPackRepository @Inject constructor(
    private val geminiPrepService: GeminiPrepService,
    private val packItemDao: PackItemDao,
    private val guidanceStepDao: GuidanceStepDao,
    private val metaDao: PersonalizedPackMetaDao
) {

    suspend fun hasPersonalizedPack(): Boolean = metaDao.get() != null

    suspend fun getMeta(): PersonalizedPackMetaEntity? = metaDao.get()

    suspend fun getPackItems(): List<PackItem> = withContext(Dispatchers.IO) {
        packItemDao.getItemsByDisasterType(PersonalizedPackConstants.DISASTER_TYPE)
    }

    suspend fun getTips(): List<GuidanceStep> = withContext(Dispatchers.IO) {
        guidanceStepDao.getSteps(
            PersonalizedPackConstants.DISASTER_TYPE,
            PersonalizedPackConstants.TIPS_SEVERITY
        )
    }

    suspend fun generateAndCache(profile: HouseholdProfile): GeneratePackOutcome {
        if (metaDao.get() != null) {
            return GeneratePackOutcome.AlreadyExists
        }

        val geminiResult = geminiPrepService.generatePersonalizedPack(profile)

        return if (geminiResult.isSuccess) {
            val data = geminiResult.getOrThrow()
            saveToRoom(profile, data, usedDefaultFallback = false)
            GeneratePackOutcome.Success(usedDefaultFallback = false)
        } else {
            applyDefaultFallback(profile)
            GeneratePackOutcome.Success(usedDefaultFallback = true)
        }
    }

    private suspend fun applyDefaultFallback(profile: HouseholdProfile) {
        val defaultItems = PackData.generalPack.map { item ->
            PackItem(
                disasterType = PersonalizedPackConstants.DISASTER_TYPE,
                itemNameEn = item.label,
                priority = when (item.section) {
                    "critical" -> 1
                    "important" -> 2
                    else -> 3
                }
            )
        }
        val defaultTips = listOf(
            "Keep your emergency pack where everyone in the household can reach it quickly.",
            "Store drinking water for at least 3 days — 1 litre per person per day.",
            "Keep phones charged and save offline maps before connectivity drops.",
            "Agree on a family meeting point outside flood-prone areas.",
            "Review your pack every 6 months and replace expired items."
        )
        saveToRoom(
            profile,
            PersonalizedPackResult(
                checklist = defaultItems.map { ChecklistItemDto(it.itemNameEn, it.priority) },
                tips = defaultTips
            ),
            usedDefaultFallback = true
        )
    }

    private suspend fun saveToRoom(
        profile: HouseholdProfile,
        result: PersonalizedPackResult,
        usedDefaultFallback: Boolean
    ) = withContext(Dispatchers.IO) {
        packItemDao.deleteByDisasterType(PersonalizedPackConstants.DISASTER_TYPE)
        guidanceStepDao.deleteByTypeAndSeverity(
            PersonalizedPackConstants.DISASTER_TYPE,
            PersonalizedPackConstants.TIPS_SEVERITY
        )

        val packEntities = result.checklist.map { dto ->
            PackItem(
                disasterType = PersonalizedPackConstants.DISASTER_TYPE,
                itemNameEn = dto.item,
                priority = dto.priority.coerceIn(1, 3)
            )
        }
        if (packEntities.isNotEmpty()) {
            packItemDao.insertAll(packEntities)
        }

        val tipSteps = result.tips.mapIndexed { index, tip ->
            GuidanceStep(
                disasterType = PersonalizedPackConstants.DISASTER_TYPE,
                severityLevel = PersonalizedPackConstants.TIPS_SEVERITY,
                stepOrder = index + 1,
                stepTextEn = tip,
                stepTextSi = tip,
                stepTextTa = tip
            )
        }
        if (tipSteps.isNotEmpty()) {
            guidanceStepDao.insertAll(tipSteps)
        }

        metaDao.upsert(
            PersonalizedPackMetaEntity(
                householdSize = profile.householdSize,
                hasKids = profile.hasKids,
                kidsAges = profile.kidsAges,
                hasElderly = profile.hasElderly,
                hasPets = profile.hasPets,
                petType = profile.petType,
                medicalConditions = profile.medicalConditions,
                generatedAt = System.currentTimeMillis(),
                usedDefaultFallback = usedDefaultFallback
            )
        )
    }
}
