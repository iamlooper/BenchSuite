package io.github.iamlooper.benchsuite.data.repository

import io.github.iamlooper.benchsuite.data.model.DeviceInfo
import io.github.iamlooper.benchsuite.data.model.LeaderboardEntry
import io.github.iamlooper.benchsuite.data.model.RunResult
import io.github.iamlooper.benchsuite.data.remote.SupabaseApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mediates between [SupabaseApi] and the UI for leaderboard data.
 * Upload is transactional: on success, [RunRepository.markUploaded] is called.
 *
 * Caches the most recently fetched entries so the run detail screens can
 * look up a single entry by [runId] without an extra network call.
 * Also caches the available app versions list so the filter UI does not
 * re-fetch it on every filter interaction.
 */
@Singleton
class LeaderboardRepository @Inject constructor(
    private val api: SupabaseApi,
    private val runRepository: RunRepository,
) {
    private val cachedEntries = mutableListOf<LeaderboardEntry>()
    private var cachedAppVersions: List<String> = emptyList()

    /**
     * Uploads [run] to Supabase and marks it locally as uploaded on success.
     *
     * @param displayName Name to display on the leaderboard (empty → "Anonymous").
     * @return The Supabase run_id UUID on success, null on failure.
     */
    suspend fun uploadRun(device: DeviceInfo, run: RunResult, displayName: String): String {
        val supabaseRunId = api.uploadRun(device, run, displayName)
        runRepository.markUploaded(run.id)
        return supabaseRunId
    }

    /**
     * Fetches the overall leaderboard from Supabase with pagination.
     * Returns an empty list (not an exception) on network failure.
     *
     * @param fingerprintHash When non-null, restricts results to the device matching this hash.
     * @param offset Row offset for the current page.
     * @param limit  Maximum entries per page.
     */
    suspend fun fetchLeaderboard(
        appVersion: String?,
        fingerprintHash: String? = null,
        offset: Int = 0,
        limit: Int = 50,
    ): List<LeaderboardEntry> {
        val entries = api.fetchLeaderboard(appVersion, fingerprintHash, offset, limit)
        synchronized(cachedEntries) {
            if (offset == 0) cachedEntries.clear()
            cachedEntries.addAll(entries)
        }
        return entries
    }

    /**
     * Returns the distinct app versions present in the active leaderboard window.
     *
     * The result is cached after the first successful fetch; subsequent calls return
     * the cache. Callers that need a fresh list should call [refreshAppVersions].
     */
    suspend fun getAppVersions(): List<String> {
        if (cachedAppVersions.isNotEmpty()) return cachedAppVersions
        return refreshAppVersions()
    }

    /** Forces a network fetch of available app versions and updates the cache. */
    suspend fun refreshAppVersions(): List<String> {
        val versions = api.fetchAppVersions()
        cachedAppVersions = versions
        return versions
    }

    /** Returns a previously fetched entry by [runId], or null if not in cache. */
    fun getCachedEntry(runId: String): LeaderboardEntry? =
        synchronized(cachedEntries) { cachedEntries.firstOrNull { it.runId == runId } }

    /**
     * Fetches full run details (including per-category benchmark details) from Supabase.
     *
     * Updates the in-memory cache with the enriched entry so subsequent
     * navigations to the same run don't need a second network round-trip.
     *
     * @param runId UUID of the run to fetch.
     * @param rank  Rank preserved from the leaderboard list entry.
     * @return [LeaderboardEntry] with [LeaderboardEntry.categories] populated, or null.
     */
    suspend fun fetchRunDetail(runId: String, rank: Int): LeaderboardEntry? {
        val entry = api.fetchRunDetail(runId, rank) ?: return null
        synchronized(cachedEntries) {
            val idx = cachedEntries.indexOfFirst { it.runId == runId }
            if (idx >= 0) cachedEntries[idx] = entry else cachedEntries.add(entry)
        }
        return entry
    }
}
