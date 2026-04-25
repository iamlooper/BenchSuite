package io.github.iamlooper.benchsuite.data.repository

import io.github.iamlooper.benchsuite.data.local.AppDatabase
import io.github.iamlooper.benchsuite.data.local.LocalCategoryScoreEntity
import io.github.iamlooper.benchsuite.data.local.LocalResultEntity
import io.github.iamlooper.benchsuite.data.local.LocalRunEntity
import io.github.iamlooper.benchsuite.data.model.BenchmarkResult
import io.github.iamlooper.benchsuite.data.model.Category
import io.github.iamlooper.benchsuite.data.model.CategoryScore
import io.github.iamlooper.benchsuite.data.model.MetricUnit
import io.github.iamlooper.benchsuite.data.model.RunResult
import io.github.iamlooper.benchsuite.data.model.StabilityRating
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local source of truth for all benchmark runs.
 *
 * Every run is persisted immediately after completion (regardless of whether it is
 * uploaded). Supabase upload is optional, and [RunResult.isUploaded] tracks the status.
 */
@Singleton
class RunRepository @Inject constructor(private val db: AppDatabase) {

    /** Emits the full list of local runs ordered by completedAt DESC. */
    fun getAllRuns(): Flow<List<LocalRunEntity>> = db.runDao().getAllRuns()

    /**
     * Fetches a single run with all its benchmark results and category scores,
     * mapped to the [RunResult] domain model.
     *
     * @return null if [runId] does not exist in the local database.
     */
    suspend fun getRunById(runId: String): RunResult? {
        return db.withTransaction {
            val entity    = db.runDao().getRunById(runId) ?: return@withTransaction null
            val results   = db.resultDao().getResultsForRun(runId)
            val catScores = db.resultDao().getCategoryScoresForRun(runId)

            val benchByCategory = results.groupBy { it.category }
            val categories = catScores.map { cs ->
                val benchmarks = benchByCategory[cs.category].orEmpty().map { r ->
                    BenchmarkResult(
                        id          = r.benchmarkId,
                        displayName = r.displayName,
                        category    = Category.fromString(r.category),
                        unit        = MetricUnit.fromString(r.unit),
                        metricP50   = r.metricP50 ?: 0.0,
                        metricP99   = r.metricP99 ?: 0.0,
                        metricBest  = r.metricBest ?: 0.0,
                        metricMean  = r.metricMean ?: 0.0,
                        throughput  = r.throughput ?: 0.0,
                        variancePct = r.variancePct ?: 0.0,
                        score       = r.score,
                    )
                }
                CategoryScore(
                    category   = Category.fromString(cs.category),
                    score      = cs.score,
                    benchmarks = benchmarks,
                )
            }.sortedBy { it.category.ordinal }

            RunResult(
                id              = entity.id,
                deviceBrand     = entity.deviceBrand,
                deviceModel     = entity.deviceModel,
                deviceSoc       = entity.deviceSoc,
                appVersion      = entity.appVersion,
                overallScore    = entity.overallScore,
                stabilityRating = entity.stabilityRating?.let { StabilityRating.fromString(it) },
                categories      = categories,
                isUploaded      = entity.isUploaded,
                startedAt       = entity.startedAt,
                completedAt     = entity.completedAt,
            )
        }
    }

    /** Persists a completed [RunResult] to Room. Must be called from a coroutine. */
    suspend fun saveRun(run: RunResult) {
        db.withTransaction {
            db.runDao().insertRun(run.toEntity())
            val resultEntities = run.categories.flatMap { cat ->
                cat.benchmarks.map { b ->
                    LocalResultEntity(
                        runId       = run.id,
                        benchmarkId = b.id,
                        category    = b.category.id,
                        displayName = b.displayName,
                        metricP50   = b.metricP50,
                        metricP99   = b.metricP99,
                        metricBest  = b.metricBest,
                        metricMean  = b.metricMean,
                        throughput  = b.throughput,
                        score       = b.score,
                        variancePct = b.variancePct,
                        unit        = b.unit.id,
                    )
                }
            }
            db.resultDao().insertResults(resultEntities)
            val scoreEntities = run.categories.map { cat ->
                LocalCategoryScoreEntity(
                    runId    = run.id,
                    category = cat.category.id,
                    score    = cat.score,
                )
            }
            db.resultDao().insertCategoryScores(scoreEntities)
        }
    }

    /** Marks a run as uploaded to Supabase. */
    suspend fun markUploaded(runId: String) = db.runDao().markUploaded(runId)

    /** Deletes a single run and its associated results/scores (via CASCADE). */
    suspend fun deleteRun(runId: String) = db.runDao().deleteRun(runId)

    /** Deletes all local runs and their associated results/scores (via CASCADE). */
    suspend fun deleteAllRuns() = db.runDao().deleteAllRuns()

    // Private helpers

    private fun RunResult.toEntity() = LocalRunEntity(
        id              = id,
        deviceBrand     = deviceBrand,
        deviceModel     = deviceModel,
        deviceSoc       = deviceSoc,
        appVersion      = appVersion,
        overallScore    = overallScore,
        stabilityRating = stabilityRating?.id,
        isUploaded      = isUploaded,
        startedAt       = startedAt,
        completedAt     = completedAt,
    )
}
