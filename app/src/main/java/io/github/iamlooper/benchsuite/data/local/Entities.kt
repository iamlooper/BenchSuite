package io.github.iamlooper.benchsuite.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "local_runs")
data class LocalRunEntity(
    @PrimaryKey val id: String,           // UUID string
    val deviceBrand: String,
    val deviceModel: String,
    val deviceSoc: String,
    val appVersion: String,
    val overallScore: Double?,             // locally estimated from benchmark throughput
    val stabilityRating: String?,          // "excellent", "good", "fair", "unstable"
    val isUploaded: Boolean = false,
    val startedAt: Long,                   // epoch millis
    val completedAt: Long,                 // epoch millis
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "local_results",
    primaryKeys = ["runId", "benchmarkId"],
    foreignKeys = [
        ForeignKey(
            entity = LocalRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("runId")],
)
data class LocalResultEntity(
    val runId: String,
    val benchmarkId: String,               // e.g. "cpu.clock_gettime_libc"
    val category: String,
    val displayName: String,
    val metricP50: Double?,
    val metricP99: Double?,
    val metricBest: Double?,
    val metricMean: Double?,
    val throughput: Double?,
    val score: Double?,                    // null until the server assigns normalized scores
    val variancePct: Double?,
    val unit: String,                      // "ns_per_op", "mb_per_sec", etc.
)

@Entity(
    tableName = "local_category_scores",
    primaryKeys = ["runId", "category"],
    foreignKeys = [
        ForeignKey(
            entity = LocalRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("runId")],
)
data class LocalCategoryScoreEntity(
    val runId: String,
    val category: String,
    val score: Double?,
)
