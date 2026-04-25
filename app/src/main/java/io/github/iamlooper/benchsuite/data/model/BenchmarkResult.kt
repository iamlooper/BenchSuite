package io.github.iamlooper.benchsuite.data.model

/**
 * Per-benchmark result from a completed run.
 *
 * [score] is always null locally; it is populated by the server once
 * population anchors are available (Season ≥50 submissions).
 */
data class BenchmarkResult(
    val id: String,
    val displayName: String,
    val category: Category,
    val unit: MetricUnit,
    val metricP50: Double,
    val metricP99: Double,
    val metricBest: Double,
    val metricMean: Double,
    val throughput: Double,
    val variancePct: Double,
    val score: Double?,          // null until population scoring activates
)
