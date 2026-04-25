package io.github.iamlooper.benchsuite.data.model

/** Metric unit for a benchmark result. Controls display formatting. */
enum class MetricUnit(
    val id: String,
    val label: String,
    val higherIsBetter: Boolean,
) {
    NS_PER_OP("ns_per_op",   "ns/op",      higherIsBetter = false),
    US_PER_OP("us_per_op",   "µs/op",      higherIsBetter = false),
    MB_PER_SEC("mb_per_sec", "MB/s",       higherIsBetter = true),
    GB_PER_SEC("gb_per_sec", "GB/s",       higherIsBetter = true),
    OPS_PER_SEC("ops_per_sec", "ops/s",    higherIsBetter = true);

    companion object {
        fun fromString(value: String): MetricUnit =
            entries.firstOrNull { it.id == value } ?: NS_PER_OP
    }
}
