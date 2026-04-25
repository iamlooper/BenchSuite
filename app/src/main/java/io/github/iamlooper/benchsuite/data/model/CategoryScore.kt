package io.github.iamlooper.benchsuite.data.model

/** Aggregated score for one category, plus the list of per-benchmark results. */
data class CategoryScore(
    val category: Category,
    val score: Double?,              // locally estimated from benchmark throughput
    val benchmarks: List<BenchmarkResult>,
)
