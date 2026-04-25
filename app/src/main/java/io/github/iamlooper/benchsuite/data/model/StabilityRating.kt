package io.github.iamlooper.benchsuite.data.model

/** Stability rating derived from coefficient of variation across rounds. */
enum class StabilityRating(val id: String, val label: String) {
    EXCELLENT("excellent", "Excellent"),
    GOOD("good",           "Good"),
    FAIR("fair",           "Fair"),
    UNSTABLE("unstable",   "Unstable");

    companion object {
        fun fromString(value: String): StabilityRating =
            entries.firstOrNull { it.id == value } ?: UNSTABLE
    }
}
