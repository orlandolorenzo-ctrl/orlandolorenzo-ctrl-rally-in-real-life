package com.rallyinreallife.app.pace

data class PaceNote(
    val index: Int,
    val severity: Int,
    val isLeft: Boolean,
    val isStraight: Boolean,
    val distanceMeters: Double,
    val modifiers: List<String>,
    val spokenText: String,
    val displayText: String,
    val maneuver: String?
)
