package com.hades.safepath.ai

data class HouseholdProfile(
    val householdSize: Int,
    val hasKids: Boolean,
    val kidsAges: String,
    val hasElderly: Boolean,
    val hasPets: Boolean,
    val petType: String,
    val medicalConditions: String
)

data class PersonalizedPackResult(
    val checklist: List<ChecklistItemDto>,
    val tips: List<String>
)

data class ChecklistItemDto(
    val item: String,
    val priority: Int
)
