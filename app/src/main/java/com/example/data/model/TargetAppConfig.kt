package com.example.data.model

data class TargetAppConfig(
    val appId: String,
    val displayName: String,
    val candidatePackages: List<String> = emptyList(),
    val labelKeywords: List<String> = emptyList(),
    val excludePackages: List<String> = emptyList(),
    val fareRegex: String = "",
    val distanceTimeRegex: String = "",
    val distanceRegex: String = "",
    val acceptTexts: List<String> = listOf("Accept"),
    val neverClickTexts: List<String> = emptyList(),
    val detectionHints: List<String> = emptyList()
)
