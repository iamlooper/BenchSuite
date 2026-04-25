package io.github.iamlooper.benchsuite.data.remote

import io.github.iamlooper.benchsuite.data.model.BenchmarkResult
import io.github.iamlooper.benchsuite.data.model.Category
import io.github.iamlooper.benchsuite.data.model.CategoryScore
import io.github.iamlooper.benchsuite.data.model.DeviceInfo
import io.github.iamlooper.benchsuite.data.model.LeaderboardEntry
import io.github.iamlooper.benchsuite.data.model.MetricUnit
import io.github.iamlooper.benchsuite.data.model.RunResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

// Supabase API

/**
 * Supabase SDK wrapper for Edge Function uploads and PostgREST leaderboard queries.
 *
 * Uses the official supabase-kt client which handles authentication headers, JSON
 * serialization, and HTTP transport internally. This eliminates the class of R8
 * release-build bugs caused by Ktor ContentNegotiation's OutgoingContent type checks
 * being broken by class hierarchy optimizations.
 */
@Singleton
class SupabaseApi @Inject constructor(private val supabase: SupabaseClient) {

    // Upload

    /**
     * Uploads a completed run to the global leaderboard via the `upload-run` Edge Function.
     *
     * @param device     Device hardware info for upsert.
     * @param run        Run metadata and scores.
     * @param displayName User-chosen name (or "Anonymous").
     * @return The new run_id UUID from Supabase.
     */
    suspend fun uploadRun(
        device: DeviceInfo,
        run: RunResult,
        displayName: String,
    ): String {
        val payload = buildUploadJson(device, run, displayName)
        // supabase-kt adds apikey + Authorization headers automatically
        val response = supabase.functions.invoke(
            function = "upload-run",
            body = payload,
        )
        val responseText = response.bodyAsText()
        val responseJson = Json.parseToJsonElement(responseText).jsonObject
        return responseJson["run_id"]?.jsonPrimitive?.content
            ?: throw RuntimeException("Missing run_id in upload response: $responseText")
    }

    // Leaderboard

    /**
     * Fetches the overall leaderboard filtered to the current app version.
     *
     * @param appVersion      Filter results to this app version. Pass null to show all versions.
     * @param fingerprintHash When non-null, restricts results to the single device whose
     *                        SHA-256(Build.FINGERPRINT) matches. Used for the "my runs" filter.
     * @param offset          Row offset for pagination.
     * @param limit           Maximum number of entries per page.
     */
    suspend fun fetchLeaderboard(appVersion: String?, fingerprintHash: String? = null, offset: Int = 0, limit: Int = 50): List<LeaderboardEntry> {
        return try {
            val parameters = buildJsonObject {
                put("p_app_version", appVersion)
                put("p_fingerprint_hash", fingerprintHash)
                put("p_offset", offset)
                put("p_limit", limit)
            }
            val rows: List<LeaderboardRow> = supabase.postgrest
                .rpc("leaderboard_overall", parameters)
                .decodeList<LeaderboardRow>()
            rows.mapIndexed { index, row ->
                LeaderboardEntry(
                    runId           = row.runId,
                    displayName     = row.displayName,
                    appVersion      = row.appVersion,
                    brand           = row.brand,
                    model           = row.model,
                    soc             = row.soc,
                    cpuCores        = row.cpuCores,
                    androidApi      = row.androidApi,
                    overallScore    = row.overallScore,
                    stabilityRating = row.stabilityRating,
                    rank            = offset + index + 1,
                    abi             = row.abi,
                    ramBytes        = row.ramBytes,
                    batteryLevel    = row.batteryLevel,
                    isCharging      = row.isCharging,
                    startedAt       = row.startedAt,
                    completedAt     = row.completedAt,
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Returns the distinct app versions present in the active leaderboard window.
     *
     * Used to populate the version filter chip menu. Returns an empty list on failure
     * so the version filter is simply hidden rather than breaking the screen.
     */
    suspend fun fetchAppVersions(): List<String> {
        return try {
            supabase.postgrest
                .rpc("leaderboard_app_versions")
                .decodeList<AppVersionRow>()
                .map { it.appVersion }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Fetches full details for a single run, including per-category benchmark results.
     *
     * Both RPCs are launched in parallel; a failure on the benchmark detail
     * RPC is treated as non-fatal so the screen can still render device/run metadata.
     *
     * @param runId The UUID of the run to fetch.
     * @param rank  The pre-computed rank from the leaderboard list (preserved).
     * @return [LeaderboardEntry] with category benchmark details populated, or null if the run is not found.
     */
    suspend fun fetchRunDetail(runId: String, rank: Int): LeaderboardEntry? {
        return try {
            val runParams = buildJsonObject { put("p_run_id", runId) }

            val (runRow, benchmarkRows) = coroutineScope {
                val runRowJob = async {
                    supabase.postgrest
                        .rpc("run_detail", runParams)
                        .decodeList<LeaderboardRow>()
                        .firstOrNull()
                }
                val benchmarkRowsJob = async {
                    try {
                        supabase.postgrest
                            .rpc("run_category_benchmarks", runParams)
                            .decodeList<RunCategoryBenchmarkRow>()
                    } catch (_: Exception) {
                        emptyList<RunCategoryBenchmarkRow>()
                    }
                }
                Pair(runRowJob.await(), benchmarkRowsJob.await())
            }

            if (runRow == null) return null

            val categories = Category.entries.mapNotNull { category ->
                val benchmarks = benchmarkRows
                    .asSequence()
                    .filter { it.category == category.id }
                    .map { row ->
                        BenchmarkResult(
                            id = row.benchmarkId,
                            displayName = row.displayName.ifBlank {
                                row.benchmarkId
                                    .split('_')
                                    .joinToString(" ") { token ->
                                        token.replaceFirstChar { char ->
                                            if (char.isLowerCase()) char.titlecase() else char.toString()
                                        }
                                    }
                            },
                            category = category,
                            unit = MetricUnit.fromString(row.unit),
                            metricP50 = row.metricP50 ?: 0.0,
                            metricP99 = row.metricP99 ?: 0.0,
                            metricBest = row.metricBest ?: 0.0,
                            metricMean = row.metricMean ?: 0.0,
                            throughput = row.throughput ?: 0.0,
                            variancePct = row.variancePct ?: 0.0,
                            score = null,
                        )
                    }
                    .toList()
                if (benchmarks.isEmpty()) {
                    null
                } else {
                    CategoryScore(
                        category = category,
                        score = null,
                        benchmarks = benchmarks,
                    )
                }
            }

            LeaderboardEntry(
                runId           = runRow.runId,
                displayName     = runRow.displayName,
                appVersion      = runRow.appVersion,
                brand           = runRow.brand,
                model           = runRow.model,
                soc             = runRow.soc,
                cpuCores        = runRow.cpuCores,
                androidApi      = runRow.androidApi,
                overallScore    = runRow.overallScore,
                stabilityRating = runRow.stabilityRating,
                rank            = rank,
                abi             = runRow.abi,
                ramBytes        = runRow.ramBytes,
                batteryLevel    = runRow.batteryLevel,
                isCharging      = runRow.isCharging,
                startedAt       = runRow.startedAt,
                completedAt     = runRow.completedAt,
                categories      = categories,
            )
        } catch (_: Exception) {
            null
        }
    }

    // Helpers

    /**
     * Builds the upload JSON payload manually with literal key names.
     *
     * This deliberately avoids @Serializable data-class serialization so that
     * R8/ProGuard can never strip, rename, or reorder the JSON field names.
     * Every key is a compile-time string constant visible in the DEX output.
     */
    private fun buildUploadJson(
        device: DeviceInfo,
        run: RunResult,
        displayName: String,
    ): JsonObject = buildJsonObject {
        put("device", buildJsonObject {
            put("brand", device.brand)
            put("model", device.model)
            put("device_name", device.deviceName)
            put("soc", device.soc)
            put("abi", device.abi)
            put("cpu_cores", device.cpuCores)
            put("ram_bytes", device.ramBytes)
            put("android_api", device.androidApi)
            put("fingerprint_hash", device.fingerprintHash)
        })
        put("run", buildJsonObject {
            put("display_name", displayName.ifBlank { "Anonymous" })
            put("app_version", run.appVersion)
            put("overall_score", run.overallScore.finiteOrNull())
            put("stability_rating", run.stabilityRating?.id)
            put("battery_level", device.batteryLevel)
            put("is_charging", device.isCharging)
            put("started_at", epochMillisToIso(run.startedAt))
            put("completed_at", epochMillisToIso(run.completedAt))
        })
        putJsonArray("results") {
            run.categories.forEach { cat ->
                cat.benchmarks.forEach { b ->
                    addJsonObject {
                        put("benchmark_id", b.id)
                        put("display_name", b.displayName)
                        put("category", b.category.id)
                        put("unit", b.unit.id)
                        put("higher_is_better", b.unit.higherIsBetter)
                        put("metric_p50", b.metricP50.finiteOrNull())
                        put("metric_p99", b.metricP99.finiteOrNull())
                        put("metric_best", b.metricBest.finiteOrNull())
                        put("metric_mean", b.metricMean.finiteOrNull())
                        put("throughput", b.throughput.finiteOrNull())
                        put("score", b.score.finiteOrNull())
                        put("variance_pct", b.variancePct.finiteOrNull())
                    }
                }
            }
        }
        putJsonArray("category_scores") {
            run.categories.forEach { cat ->
                addJsonObject {
                    put("category", cat.category.id)
                    put("score", cat.score.finiteOrNull())
                }
            }
        }
    }

    private fun epochMillisToIso(ms: Long): String {
        val instant = Instant.ofEpochMilli(ms)
        return instant.toString()
    }
}

// kotlinx.serialization throws IllegalArgumentException for non-finite doubles because they
// are not part of the JSON specification. Every Double field is sanitized here as a final
// safety net before it reaches the JSON builder.
private fun Double.finiteOrNull(): Double? = if (isFinite()) this else null
private fun Double?.finiteOrNull(): Double? = if (this != null && isFinite()) this else null

// Response models

@Serializable
internal data class LeaderboardRow(
    @SerialName("run_id")          val runId: String,
    @SerialName("display_name")    val displayName: String,
    @SerialName("app_version")     val appVersion: String,
    val brand: String,
    val model: String,
    val soc: String,
    @SerialName("cpu_cores")       val cpuCores: Int,
    @SerialName("android_api")     val androidApi: Int,
    @SerialName("overall_score")   val overallScore: Double,
    @SerialName("stability_rating") val stabilityRating: String?,
    val abi: String = "",
    @SerialName("ram_bytes")       val ramBytes: Long = 0L,
    @SerialName("battery_level")   val batteryLevel: Int = -1,
    @SerialName("is_charging")     val isCharging: Boolean = false,
    @SerialName("started_at")      val startedAt: String? = null,
    @SerialName("completed_at")    val completedAt: String? = null,
)

@Serializable
internal data class RunCategoryBenchmarkRow(
    val category: String,
    @SerialName("benchmark_id") val benchmarkId: String,
    @SerialName("display_name") val displayName: String = "",
    val unit: String,
    @SerialName("metric_p50") val metricP50: Double? = null,
    @SerialName("metric_p99") val metricP99: Double? = null,
    @SerialName("metric_best") val metricBest: Double? = null,
    @SerialName("metric_mean") val metricMean: Double? = null,
    val throughput: Double? = null,
    @SerialName("variance_pct") val variancePct: Double? = null,
    val score: Double? = null,
)

@Serializable
internal data class AppVersionRow(
    @SerialName("app_version") val appVersion: String,
)
