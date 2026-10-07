package com.hades.safepath.data.model

data class SurvivalArticle(
    val title: String,
    val body: String,           // markdown-formatted
    val steps: List<String>     // numbered step-by-step actions
)

data class SurvivalTopic(
    val id: String,
    val category: String,
    val title: String,
    val iconRes: Int,
    val colorHex: String,
    val summary: String,
    val steps: List<String>
)
