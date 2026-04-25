package io.github.iamlooper.benchsuite.data.model

/** Complete result of a benchmark run (all categories). */
data class RunResult(
    val id: String,                   // UUID string, matches local Room id
    val deviceBrand: String,
    val deviceModel: String,
    val deviceSoc: String,
    val appVersion: String,
    val overallScore: Double?,        // locally estimated from benchmark throughput
    val stabilityRating: StabilityRating?,
    val categories: List<CategoryScore>,
    val isUploaded: Boolean,
    val startedAt: Long,              // epoch millis
    val completedAt: Long,            // epoch millis
)
