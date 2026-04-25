package io.github.iamlooper.benchsuite.data.model

/** A single entry in the global leaderboard. */
data class LeaderboardEntry(
    val runId: String,
    val displayName: String,
    val appVersion: String,
    val brand: String,
    val model: String,
    val soc: String,
    val cpuCores: Int,
    val androidApi: Int,
    val overallScore: Double,
    val stabilityRating: String?,
    val rank: Int,                   // computed client-side from response ordering
    val abi: String,
    val ramBytes: Long,
    val batteryLevel: Int = -1,      // 0–100 at run start; -1 if unavailable
    val isCharging: Boolean = false,
    val startedAt: String?,          // ISO 8601 timestamp from server
    val completedAt: String?,        // ISO 8601 timestamp from server
    val categories: List<CategoryScore> = emptyList(),
)
